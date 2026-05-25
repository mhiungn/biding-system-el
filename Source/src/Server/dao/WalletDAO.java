package Server.dao;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;

public class WalletDAO {
    public static final long DEFAULT_INITIAL_BALANCE = 100_000L;

    private static volatile WalletDAO instance;

    public static WalletDAO getInstance() {
        if (instance == null) {
            synchronized (WalletDAO.class) {
                if (instance == null) {
                    instance = new WalletDAO();
                }
            }
        }
        return instance;
    }

    private WalletDAO() {
        UserDAO.getInstance();
        createTablesIfNotExist();
        createWalletsForExistingUsers();
    }

    private void createTablesIfNotExist() {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            String usernameColumn = usernameColumnDefinition(conn);
            String wallets = "CREATE TABLE IF NOT EXISTS user_wallets ("
                    + "username " + usernameColumn + " PRIMARY KEY, "
                    + "balance BIGINT NOT NULL DEFAULT 100000, "
                    + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                    + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                    + "FOREIGN KEY (username) REFERENCES users(username) ON DELETE CASCADE"
                    + ")";

            String transactions = "CREATE TABLE IF NOT EXISTS wallet_transactions ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "username " + usernameColumn + " NOT NULL, "
                    + "type VARCHAR(32) NOT NULL, "
                    + "amount BIGINT NOT NULL, "
                    + "auction_id INT NULL, "
                    + "bid_id BIGINT NULL, "
                    + "note VARCHAR(255), "
                    + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                    + "FOREIGN KEY (username) REFERENCES users(username) ON DELETE CASCADE"
                    + ")";

            String holds = "CREATE TABLE IF NOT EXISTS wallet_holds ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "username " + usernameColumn + " NOT NULL, "
                    + "auction_id INT NOT NULL, "
                    + "amount BIGINT NOT NULL, "
                    + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                    + "UNIQUE (username, auction_id), "
                    + "FOREIGN KEY (username) REFERENCES users(username) ON DELETE CASCADE"
                    + ")";

            stmt.execute(wallets);
            stmt.execute(transactions);
            stmt.execute(holds);
        } catch (SQLException e) {
            throw new RuntimeException("[WalletDAO] Cannot create wallet tables", e);
        }
    }

    private String usernameColumnDefinition(Connection conn) {
        String sql = "SELECT CHARACTER_SET_NAME, COLLATION_NAME "
                + "FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'username'";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                String charset = rs.getString("CHARACTER_SET_NAME");
                String collation = rs.getString("COLLATION_NAME");
                if (isSafeIdentifier(charset) && isSafeIdentifier(collation)) {
                    return "VARCHAR(50) CHARACTER SET " + charset + " COLLATE " + collation;
                }
            }
        } catch (SQLException ignored) {
            // Test databases may not expose MySQL's information_schema shape.
        }
        return "VARCHAR(50)";
    }

    private boolean isSafeIdentifier(String value) {
        return value != null && value.matches("[A-Za-z0-9_]+");
    }

    public void createWalletsForExistingUsers() {
        String sql = "SELECT username FROM users WHERE username NOT IN (SELECT username FROM user_wallets)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                createWalletIfMissing(rs.getString("username"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("[WalletDAO] Cannot migrate existing users", e);
        }
    }

    public long getBalance(String username) {
        createWalletIfMissing(username);
        String sql = "SELECT balance FROM user_wallets WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong("balance") : 0L;
            }
        } catch (SQLException e) {
            throw new RuntimeException("[WalletDAO] Cannot read balance", e);
        }
    }

    public long getHeldAmount(String username) {
        String sql = "SELECT COALESCE(SUM(amount), 0) AS held FROM wallet_holds WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong("held") : 0L;
            }
        } catch (SQLException e) {
            throw new RuntimeException("[WalletDAO] Cannot read held amount", e);
        }
    }

    public long getAvailableBalance(String username) {
        return getBalance(username) - getHeldAmount(username);
    }

    public boolean canAffordBid(Connection conn, String username, int auctionId, long bidAmount) throws SQLException {
        createWalletIfMissing(conn, username);
        long balance = getBalanceForUpdate(conn, username);
        long held = getHeldAmountForUpdate(conn, username);
        long existingHoldForAuction = getHoldForAuctionForUpdate(conn, username, auctionId);
        return bidAmount <= balance - held + existingHoldForAuction;
    }

    public long getBalanceForUpdate(Connection conn, String username) throws SQLException {
        String sql = "SELECT balance FROM user_wallets WHERE username = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong("balance") : 0L;
            }
        }
    }

    public long getHeldAmountForUpdate(Connection conn, String username) throws SQLException {
        String sql = "SELECT amount FROM wallet_holds WHERE username = ? FOR UPDATE";
        long total = 0L;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    total += rs.getLong("amount");
                }
            }
        }
        return total;
    }

    public long getTodayDepositTotal(String username) {
        String sql = "SELECT COALESCE(SUM(amount), 0) AS total "
                + "FROM wallet_transactions "
                + "WHERE username = ? AND type = 'DEPOSIT' AND CAST(created_at AS DATE) = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setDate(2, Date.valueOf(LocalDate.now()));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong("total") : 0L;
            }
        } catch (SQLException e) {
            throw new RuntimeException("[WalletDAO] Cannot read today's deposit total", e);
        }
    }

    public long getHoldForAuction(String username, int auctionId) {
        String sql = "SELECT amount FROM wallet_holds WHERE username = ? AND auction_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setInt(2, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong("amount") : 0L;
            }
        } catch (SQLException e) {
            throw new RuntimeException("[WalletDAO] Cannot read auction hold", e);
        }
    }

    public long getHoldForAuctionForUpdate(Connection conn, String username, int auctionId) throws SQLException {
        String sql = "SELECT amount FROM wallet_holds WHERE username = ? AND auction_id = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setInt(2, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong("amount") : 0L;
            }
        }
    }

    public void createWalletIfMissing(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required.");
        }
        String exists = "SELECT COUNT(*) FROM user_wallets WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(exists)) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        return;
                    }
                }
            }

            String insert = "INSERT INTO user_wallets (username, balance) VALUES (?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insert)) {
                ps.setString(1, username);
                ps.setLong(2, DEFAULT_INITIAL_BALANCE);
                ps.executeUpdate();
            }
            addTransaction(username, "INITIAL_CREDIT", DEFAULT_INITIAL_BALANCE, null, null, "Initial wallet credit");
        } catch (SQLException e) {
            throw new RuntimeException("[WalletDAO] Cannot create wallet for user: " + username, e);
        }
    }

    public void createWalletIfMissing(Connection conn, String username) throws SQLException {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required.");
        }

        String insert = "INSERT IGNORE INTO user_wallets (username, balance) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insert)) {
            ps.setString(1, username);
            ps.setLong(2, DEFAULT_INITIAL_BALANCE);
            int inserted = ps.executeUpdate();
            if (inserted > 0) {
                addTransaction(conn, username, "INITIAL_CREDIT", DEFAULT_INITIAL_BALANCE,
                        null, null, "Initial wallet credit");
            }
        }
    }

    public void deposit(String username, long amount) {
        createWalletIfMissing(username);
        String sql = "UPDATE user_wallets SET balance = balance + ?, updated_at = ? WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, amount);
            ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            ps.setString(3, username);
            ps.executeUpdate();
            addTransaction(username, "DEPOSIT", amount, null, null, "Manual deposit");
        } catch (SQLException e) {
            throw new RuntimeException("[WalletDAO] Cannot deposit", e);
        }
    }

    public void addTransaction(String username, String type, long amount, Integer auctionId, Long bidId, String note) {
        String sql = "INSERT INTO wallet_transactions (username, type, amount, auction_id, bid_id, note) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, type);
            ps.setLong(3, amount);
            if (auctionId == null) {
                ps.setNull(4, java.sql.Types.INTEGER);
            } else {
                ps.setInt(4, auctionId);
            }
            if (bidId == null) {
                ps.setNull(5, java.sql.Types.BIGINT);
            } else {
                ps.setLong(5, bidId);
            }
            ps.setString(6, note);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("[WalletDAO] Cannot add wallet transaction", e);
        }
    }

    public void addTransaction(Connection conn, String username, String type, long amount,
                               Integer auctionId, Long bidId, String note) throws SQLException {
        String sql = "INSERT INTO wallet_transactions (username, type, amount, auction_id, bid_id, note) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, type);
            ps.setLong(3, amount);
            if (auctionId == null) {
                ps.setNull(4, java.sql.Types.INTEGER);
            } else {
                ps.setInt(4, auctionId);
            }
            if (bidId == null) {
                ps.setNull(5, java.sql.Types.BIGINT);
            } else {
                ps.setLong(5, bidId);
            }
            ps.setString(6, note);
            ps.executeUpdate();
        }
    }

    public boolean hasTransaction(String username, String type, int auctionId) {
        String sql = "SELECT 1 FROM wallet_transactions WHERE username = ? AND type = ? AND auction_id = ? LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, type);
            ps.setInt(3, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("[WalletDAO] Cannot check wallet transaction existence", e);
        }
    }

    public void reserveHold(String username, int auctionId, long amount) {
        createWalletIfMissing(username);
        long existing = getHoldForAuction(username, auctionId);
        String sql = existing > 0
                ? "UPDATE wallet_holds SET amount = ?, created_at = CURRENT_TIMESTAMP WHERE username = ? AND auction_id = ?"
                : "INSERT INTO wallet_holds (amount, username, auction_id) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, amount);
            ps.setString(2, username);
            ps.setInt(3, auctionId);
            ps.executeUpdate();
            addTransaction(username, "HOLD", amount, auctionId, null, "Bid hold reserved");
        } catch (SQLException e) {
            throw new RuntimeException("[WalletDAO] Cannot reserve wallet hold", e);
        }
    }

    public void reserveHold(Connection conn, String username, int auctionId, long amount) throws SQLException {
        createWalletIfMissing(conn, username);
        long existing = getHoldForAuctionForUpdate(conn, username, auctionId);
        String sql = existing > 0
                ? "UPDATE wallet_holds SET amount = ?, created_at = CURRENT_TIMESTAMP WHERE username = ? AND auction_id = ?"
                : "INSERT INTO wallet_holds (amount, username, auction_id) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, amount);
            ps.setString(2, username);
            ps.setInt(3, auctionId);
            ps.executeUpdate();
        }
        addTransaction(conn, username, "HOLD", amount, auctionId, null, "Bid hold reserved");
    }

    public void releaseHold(String username, int auctionId) {
        long existing = getHoldForAuction(username, auctionId);
        if (existing <= 0) {
            return;
        }
        String sql = "DELETE FROM wallet_holds WHERE username = ? AND auction_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setInt(2, auctionId);
            ps.executeUpdate();
            addTransaction(username, "HOLD_RELEASE", existing, auctionId, null, "Bid hold released");
        } catch (SQLException e) {
            throw new RuntimeException("[WalletDAO] Cannot release wallet hold", e);
        }
    }

    public void releaseHold(Connection conn, String username, int auctionId) throws SQLException {
        createWalletIfMissing(conn, username);
        getBalanceForUpdate(conn, username);
        long existing = getHoldForAuctionForUpdate(conn, username, auctionId);
        if (existing <= 0) {
            return;
        }
        String sql = "DELETE FROM wallet_holds WHERE username = ? AND auction_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setInt(2, auctionId);
            ps.executeUpdate();
        }
        addTransaction(conn, username, "HOLD_RELEASE", existing, auctionId, null, "Bid hold released");
    }

    public void convertHoldToSpent(String username, int auctionId, long finalAmount) {
        createWalletIfMissing(username);
        if (hasTransaction(username, "SPENT", auctionId) || getHoldForAuction(username, auctionId) <= 0) {
            return;
        }
        String update = "UPDATE user_wallets SET balance = balance - ?, updated_at = ? WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(update)) {
            ps.setLong(1, finalAmount);
            ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            ps.setString(3, username);
            ps.executeUpdate();
            releaseHold(username, auctionId);
            addTransaction(username, "SPENT", finalAmount, auctionId, null, "Winning bid finalized");
        } catch (SQLException e) {
            throw new RuntimeException("[WalletDAO] Cannot convert hold to spent", e);
        }
    }

    public long getTotalSpent(String username) {
        String sql = "SELECT COALESCE(SUM(amount), 0) AS spent FROM wallet_transactions "
                + "WHERE username = ? AND type = 'SPENT'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong("spent") : 0L;
            }
        } catch (SQLException e) {
            throw new RuntimeException("[WalletDAO] Cannot read total spent", e);
        }
    }
}
