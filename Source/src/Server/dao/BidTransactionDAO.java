package Server.dao;

import CommonClasses.*;

import java.sql.*;
import java.util.*;

/**
 * Lớp DAO quản lý việc lưu trữ dữ liệu {@link BidTransaction} trên MySQL.
 * <p>
 * DAO này xử lý tất cả các thao tác dữ liệu liên quan đến giao dịch đấu giá:
 * ghi nhận bid, truy vấn lịch sử giao dịch theo phiên đấu giá hoặc theo người đấu giá.
 * Sử dụng JDBC thông qua {@link DatabaseConnection} để truy vấn MySQL.
 * </p>
 *
 * <h3>Singleton Pattern:</h3>
 * {@code BidTransactionDAO} được triển khai dưới dạng Singleton an toàn đa luồng
 * (double-checked locking) để đảm bảo chỉ có một điểm truy cập dữ liệu
 * giao dịch duy nhất trong toàn bộ ứng dụng server.
 *
 * <h3>Cấu trúc bảng:</h3>
 * <pre>
 *   bid_transactions (
 *       transaction_id   VARCHAR(36)  PRIMARY KEY,
 *       auction_id       INT          NOT NULL,
 *       bid_amount       FLOAT        NOT NULL,
 *       bid_created_at   DATETIME     NULL,
 *       bidder_username  VARCHAR(50)  NOT NULL,
 *       timestamp        DATETIME     NOT NULL,
 *       successful       BOOLEAN      NOT NULL
 *   )
 * </pre>
 *
 * <h3>Mapping dữ liệu:</h3>
 * Đối tượng {@link Bid} bên trong {@link BidTransaction} được lưu trực tiếp
 * dưới dạng các cột {@code bid_amount}, {@code bid_created_at}, và
 * {@code bidder_username} trong bảng {@code bid_transactions}.
 *
 * <h3>Ví dụ sử dụng:</h3>
 * <pre>{@code
 *   BidTransactionDAO dao = BidTransactionDAO.getInstance();
 *
 *   Bid bid = new Bid(new Date(), 500.0f, "john");
 *   BidTransaction tx = new BidTransaction(1, bid, "john", true);
 *   dao.save(tx.getTransactionId(), tx);
 *
 *   List<BidTransaction> history = dao.findByAuctionId(1);
 * }</pre>
 *
 * @see BidTransaction
 * @see Bid
 * @see GenericDAO
 * @see DatabaseConnection
 */
public class BidTransactionDAO implements GenericDAO<String, BidTransaction> {

    // ========================== Singleton ==========================

    /** Instance duy nhất của BidTransactionDAO. */
    private static volatile BidTransactionDAO instance;

    /**
     * Trả về instance Singleton của {@code BidTransactionDAO}.
     * Sử dụng double-checked locking để khởi tạo lazy an toàn đa luồng.
     *
     * @return instance Singleton của {@code BidTransactionDAO}
     */
    public static BidTransactionDAO getInstance() {
        if (instance == null) {
            synchronized (BidTransactionDAO.class) {
                if (instance == null) {
                    instance = new BidTransactionDAO();
                }
            }
        }
        return instance;
    }

    // ========================== Constructor ==========================

    /**
     * Constructor private — sử dụng {@link #getInstance()} để lấy Singleton.
     * Tự động tạo bảng {@code bid_transactions} nếu chưa tồn tại.
     */
    private BidTransactionDAO() {
        createTableIfNotExists();
        System.out.println("[BidTransactionDAO] Đã khởi tạo với MySQL. Hiện có " + count() + " giao dịch.");
    }

    // ========================== Tạo bảng ==========================

    /**
     * Tạo bảng {@code bid_transactions} trong MySQL nếu chưa tồn tại.
     */
    private void createTableIfNotExists() {
        String sql = "CREATE TABLE IF NOT EXISTS bid_transactions ("
                + "transaction_id   VARCHAR(36)  PRIMARY KEY, "
                + "auction_id       INT          NOT NULL, "
                + "bid_amount       FLOAT        NOT NULL, "
                + "bid_created_at   DATETIME     NULL, "
                + "bidder_username  VARCHAR(50)  NOT NULL, "
                + "timestamp        DATETIME     NOT NULL, "
                + "successful       BOOLEAN      NOT NULL"
                + ")";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("[BidTransactionDAO] Không thể tạo bảng bid_transactions", e);
        }
    }

    // ========================== Triển khai GenericDAO ==========================

    /**
     * Lưu một giao dịch bid mới vào hệ thống.
     * <p>
     * Giao dịch được lưu trực tiếp vào MySQL. Nếu giao dịch với transactionId
     * này đã tồn tại, thao tác bị từ chối và in ra cảnh báo.
     * </p>
     *
     * @param transactionId mã giao dịch duy nhất (khóa)
     * @param transaction   đối tượng BidTransaction cần lưu
     * @throws IllegalArgumentException nếu transactionId rỗng/null hoặc transaction là null
     */
    @Override
    public void save(String transactionId, BidTransaction transaction) {
        if (transactionId == null || transactionId.trim().isEmpty()) {
            throw new IllegalArgumentException("TransactionId không được để trống hoặc null");
        }
        if (transaction == null) {
            throw new IllegalArgumentException("BidTransaction không được null");
        }

        // Kiểm tra trùng transactionId
        if (exists(transactionId)) {
            System.err.println("[BidTransactionDAO] Cảnh báo: Giao dịch '" + transactionId
                    + "' đã tồn tại. Dùng update() thay thế.");
            return;
        }

        String sql = "INSERT INTO bid_transactions "
                + "(transaction_id, auction_id, bid_amount, bid_created_at, bidder_username, timestamp, successful) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, transactionId);
            ps.setInt(2, transaction.getAuctionId());
            ps.setFloat(3, transaction.getBid().getBid());
            ps.setTimestamp(4, transaction.getBid().getCreatedAt() != null
                    ? new Timestamp(transaction.getBid().getCreatedAt().getTime())
                    : null);
            ps.setString(5, transaction.getBidderUsername());
            ps.setTimestamp(6, new Timestamp(transaction.getTimestamp().getTime()));
            ps.setBoolean(7, transaction.isSuccessful());
            ps.executeUpdate();
            System.out.println("[BidTransactionDAO] Đã lưu giao dịch: " + transactionId);
        } catch (SQLException e) {
            throw new RuntimeException("[BidTransactionDAO] Lỗi khi lưu giao dịch: " + transactionId, e);
        }
    }

    /**
     * Tìm giao dịch theo transactionId.
     *
     * @param transactionId mã giao dịch cần tìm
     * @return {@link BidTransaction} nếu tìm thấy, hoặc {@code null} nếu không tồn tại
     */
    @Override
    public BidTransaction findById(String transactionId) {
        String sql = "SELECT * FROM bid_transactions WHERE transaction_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, transactionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToBidTransaction(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("[BidTransactionDAO] Lỗi khi tìm giao dịch: " + transactionId, e);
        }
    }

    /**
     * Trả về danh sách tất cả giao dịch bid đã ghi nhận.
     *
     * @return danh sách tất cả giao dịch; trả về danh sách rỗng nếu không có
     */
    @Override
    public List<BidTransaction> findAll() {
        String sql = "SELECT * FROM bid_transactions ORDER BY timestamp DESC";
        List<BidTransaction> result = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapResultSetToBidTransaction(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("[BidTransactionDAO] Lỗi khi lấy tất cả giao dịch", e);
        }
        return result;
    }

    /**
     * Cập nhật thông tin của một giao dịch đã tồn tại.
     * <p>
     * Giao dịch được xác định bằng transactionId. Nếu không tìm thấy giao dịch
     * với transactionId này, thao tác cập nhật bị từ chối.
     * </p>
     *
     * @param transactionId mã giao dịch cần cập nhật
     * @param transaction   dữ liệu BidTransaction mới
     * @return {@code true} nếu tìm thấy và cập nhật thành công, {@code false} nếu không
     */
    @Override
    public boolean update(String transactionId, BidTransaction transaction) {
        String sql = "UPDATE bid_transactions SET auction_id = ?, bid_amount = ?, bid_created_at = ?, "
                + "bidder_username = ?, timestamp = ?, successful = ? WHERE transaction_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, transaction.getAuctionId());
            ps.setFloat(2, transaction.getBid().getBid());
            ps.setTimestamp(3, transaction.getBid().getCreatedAt() != null
                    ? new Timestamp(transaction.getBid().getCreatedAt().getTime())
                    : null);
            ps.setString(4, transaction.getBidderUsername());
            ps.setTimestamp(5, new Timestamp(transaction.getTimestamp().getTime()));
            ps.setBoolean(6, transaction.isSuccessful());
            ps.setString(7, transactionId);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                System.out.println("[BidTransactionDAO] Đã cập nhật giao dịch: " + transactionId);
                return true;
            }
            return false;
        } catch (SQLException e) {
            throw new RuntimeException("[BidTransactionDAO] Lỗi khi cập nhật giao dịch: " + transactionId, e);
        }
    }

    /**
     * Xóa một giao dịch theo transactionId.
     *
     * @param transactionId mã giao dịch cần xóa
     * @return {@code true} nếu tìm thấy và xóa thành công, {@code false} nếu không
     */
    @Override
    public boolean delete(String transactionId) {
        String sql = "DELETE FROM bid_transactions WHERE transaction_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, transactionId);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                System.out.println("[BidTransactionDAO] Đã xóa giao dịch: " + transactionId);
                return true;
            }
            return false;
        } catch (SQLException e) {
            throw new RuntimeException("[BidTransactionDAO] Lỗi khi xóa giao dịch: " + transactionId, e);
        }
    }

    /**
     * Kiểm tra xem giao dịch với transactionId cho trước có tồn tại hay không.
     *
     * @param transactionId mã giao dịch cần kiểm tra
     * @return {@code true} nếu tồn tại
     */
    @Override
    public boolean exists(String transactionId) {
        String sql = "SELECT COUNT(*) FROM bid_transactions WHERE transaction_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, transactionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
                return false;
            }
        } catch (SQLException e) {
            throw new RuntimeException("[BidTransactionDAO] Lỗi khi kiểm tra tồn tại: " + transactionId, e);
        }
    }

    /**
     * Trả về tổng số giao dịch đã ghi nhận.
     *
     * @return số lượng giao dịch
     */
    @Override
    public int count() {
        String sql = "SELECT COUNT(*) FROM bid_transactions";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        } catch (SQLException e) {
            throw new RuntimeException("[BidTransactionDAO] Lỗi khi đếm giao dịch", e);
        }
    }

    // ========================== Phương thức Truy vấn ==========================

    /**
     * Tìm tất cả giao dịch thuộc một phiên đấu giá cụ thể.
     * <p>
     * Kết quả được sắp xếp theo thời gian giảm dần (giao dịch mới nhất trước).
     * </p>
     *
     * @param auctionId mã phiên đấu giá cần truy vấn
     * @return danh sách giao dịch của phiên đấu giá; trả về danh sách rỗng nếu không có
     */
    public List<BidTransaction> findByAuctionId(int auctionId) {
        String sql = "SELECT * FROM bid_transactions WHERE auction_id = ? ORDER BY timestamp DESC";
        List<BidTransaction> result = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapResultSetToBidTransaction(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("[BidTransactionDAO] Lỗi khi tìm giao dịch theo auctionId: " + auctionId, e);
        }
        return result;
    }

    /**
     * Tìm tất cả giao dịch của một người đấu giá cụ thể.
     * <p>
     * Kết quả được sắp xếp theo thời gian giảm dần (giao dịch mới nhất trước).
     * </p>
     *
     * @param bidderUsername tên đăng nhập của người đấu giá
     * @return danh sách giao dịch của người đấu giá; trả về danh sách rỗng nếu không có
     */
    public List<BidTransaction> findByBidderUsername(String bidderUsername) {
        String sql = "SELECT * FROM bid_transactions WHERE bidder_username = ? ORDER BY timestamp DESC";
        List<BidTransaction> result = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, bidderUsername);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapResultSetToBidTransaction(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("[BidTransactionDAO] Lỗi khi tìm giao dịch theo bidder: " + bidderUsername, e);
        }
        return result;
    }

    /**
     * Tìm tất cả giao dịch thành công của một phiên đấu giá.
     *
     * @param auctionId mã phiên đấu giá
     * @return danh sách giao dịch thành công; trả về danh sách rỗng nếu không có
     */
    public List<BidTransaction> findSuccessfulByAuctionId(int auctionId) {
        String sql = "SELECT * FROM bid_transactions WHERE auction_id = ? AND successful = true "
                + "ORDER BY timestamp DESC";
        List<BidTransaction> result = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapResultSetToBidTransaction(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("[BidTransactionDAO] Lỗi khi tìm giao dịch thành công theo auctionId: "
                    + auctionId, e);
        }
        return result;
    }

    /**
     * Xóa tất cả giao dịch thuộc một phiên đấu giá.
     *
     * @param auctionId mã phiên đấu giá cần xóa giao dịch
     * @return số lượng giao dịch đã xóa
     */
    public int deleteByAuctionId(int auctionId) {
        String sql = "DELETE FROM bid_transactions WHERE auction_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, auctionId);
            int rows = ps.executeUpdate();
            System.out.println("[BidTransactionDAO] Đã xóa " + rows + " giao dịch của auction: " + auctionId);
            return rows;
        } catch (SQLException e) {
            throw new RuntimeException("[BidTransactionDAO] Lỗi khi xóa giao dịch theo auctionId: " + auctionId, e);
        }
    }

    // ========================== Phương thức Private ==========================

    /**
     * Chuyển đổi một dòng {@link ResultSet} thành đối tượng {@link BidTransaction}.
     * <p>
     * Tái tạo đối tượng {@link Bid} từ các cột {@code bid_amount},
     * {@code bid_created_at}, và {@code bidder_username}.
     * </p>
     *
     * @param rs ResultSet đang trỏ tới dòng cần đọc
     * @return đối tượng BidTransaction
     * @throws SQLException nếu lỗi đọc dữ liệu
     */
    private BidTransaction mapResultSetToBidTransaction(ResultSet rs) throws SQLException {
        String transactionId = rs.getString("transaction_id");
        int auctionId = rs.getInt("auction_id");
        float bidAmount = rs.getFloat("bid_amount");
        Timestamp bidCreatedAt = rs.getTimestamp("bid_created_at");
        String bidderUsername = rs.getString("bidder_username");
        Timestamp timestamp = rs.getTimestamp("timestamp");
        boolean successful = rs.getBoolean("successful");

        // Tái tạo đối tượng Bid
        Bid bid = new Bid(
                bidCreatedAt != null ? new java.util.Date(bidCreatedAt.getTime()) : null,
                bidAmount,
                bidderUsername
        );

        // Tái tạo đối tượng BidTransaction
        BidTransaction transaction = new BidTransaction(auctionId, bid, bidderUsername, successful);

        // Ghi đè transactionId và timestamp từ database (không dùng giá trị auto-generated)
        // Sử dụng reflection vì BidTransaction không có setter cho các trường này
        try {
            java.lang.reflect.Field txIdField = BidTransaction.class.getDeclaredField("transactionId");
            txIdField.setAccessible(true);
            txIdField.set(transaction, transactionId);

            java.lang.reflect.Field tsField = BidTransaction.class.getDeclaredField("timestamp");
            tsField.setAccessible(true);
            tsField.set(transaction, new java.util.Date(timestamp.getTime()));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("[BidTransactionDAO] Lỗi khi mapping dữ liệu từ ResultSet", e);
        }

        return transaction;
    }
}
