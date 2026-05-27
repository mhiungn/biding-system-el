package Server.dao;

import CommonClasses.dto.NotificationDTO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class NotificationDAO {
    private static volatile NotificationDAO instance;

    public static NotificationDAO getInstance() {
        if (instance == null) {
            synchronized (NotificationDAO.class) {
                if (instance == null) {
                    instance = new NotificationDAO();
                }
            }
        }
        return instance;
    }

    private NotificationDAO() {
        UserDAO.getInstance();
        createTableIfNotExists();
    }

    private void createTableIfNotExists() {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            String usernameColumn = usernameColumnDefinition(conn);
            String sql = "CREATE TABLE IF NOT EXISTS notifications ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "username " + usernameColumn + " NOT NULL, "
                    + "auction_id INT NULL, "
                    + "type VARCHAR(64) NOT NULL, "
                    + "title VARCHAR(255) NOT NULL, "
                    + "message TEXT NOT NULL, "
                    + "action_target VARCHAR(64) NOT NULL, "
                    + "is_read BOOLEAN NOT NULL DEFAULT FALSE, "
                    + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                    + "FOREIGN KEY (username) REFERENCES users(username) ON DELETE CASCADE"
                    + ")";

            stmt.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("[NotificationDAO] Cannot create notifications table", e);
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

    public List<NotificationDTO> findRecentByUser(String username, int limit) {
        String sql = "SELECT * FROM notifications WHERE username = ? ORDER BY created_at DESC, id DESC LIMIT ?";
        List<NotificationDTO> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapNotification(rs));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("[NotificationDAO] Cannot list notifications for " + username, e);
        }
    }

    public int countUnread(String username) {
        String sql = "SELECT COUNT(*) FROM notifications WHERE username = ? AND is_read = FALSE";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("[NotificationDAO] Cannot count unread notifications", e);
        }
    }

    public void markRead(long notificationId, String username) {
        String sql = "UPDATE notifications SET is_read = TRUE WHERE id = ? AND username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, notificationId);
            ps.setString(2, username);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("[NotificationDAO] Cannot mark notification read", e);
        }
    }

    public void markAllRead(String username) {
        String sql = "UPDATE notifications SET is_read = TRUE WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("[NotificationDAO] Cannot mark notifications read", e);
        }
    }

    public void createNotification(String username, Integer auctionId, String type, String title,
                                   String message, String actionTarget) {
        if (username == null || username.isBlank()) {
            return;
        }

        String sql = "INSERT INTO notifications (username, auction_id, type, title, message, action_target) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            if (auctionId == null) {
                ps.setNull(2, java.sql.Types.INTEGER);
            } else {
                ps.setInt(2, auctionId);
            }
            ps.setString(3, type);
            ps.setString(4, title);
            ps.setString(5, message);
            ps.setString(6, actionTarget);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("[NotificationDAO] Cannot create notification", e);
        }
    }

    public void createNotification(Connection conn, String username, Integer auctionId, String type, String title,
                                   String message, String actionTarget) throws SQLException {
        if (username == null || username.isBlank()) {
            return;
        }

        String sql = "INSERT INTO notifications (username, auction_id, type, title, message, action_target) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            if (auctionId == null) {
                ps.setNull(2, java.sql.Types.INTEGER);
            } else {
                ps.setInt(2, auctionId);
            }
            ps.setString(3, type);
            ps.setString(4, title);
            ps.setString(5, message);
            ps.setString(6, actionTarget);
            ps.executeUpdate();
        }
    }

    public boolean existsByUserAuctionAndType(String username, int auctionId, String type) {
        String sql = "SELECT 1 FROM notifications WHERE username = ? AND auction_id = ? AND type = ? LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setInt(2, auctionId);
            ps.setString(3, type);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("[NotificationDAO] Cannot check notification existence", e);
        }
    }

    private NotificationDTO mapNotification(ResultSet rs) throws SQLException {
        int auctionId = rs.getInt("auction_id");
        Integer nullableAuctionId = rs.wasNull() ? null : auctionId;
        Timestamp createdAt = rs.getTimestamp("created_at");
        Date created = createdAt == null ? null : new Date(createdAt.getTime());

        return new NotificationDTO(
                rs.getLong("id"),
                rs.getString("username"),
                nullableAuctionId,
                rs.getString("type"),
                rs.getString("title"),
                rs.getString("message"),
                rs.getString("action_target"),
                rs.getBoolean("is_read"),
                created);
    }
}
