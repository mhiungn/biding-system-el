package Server.dao;

import CommonClasses.*;

import java.sql.*;
import java.util.*;

/**
 * Lớp DAO quản lý việc lưu trữ dữ liệu {@link User} trên MySQL.
 * <p>
 * DAO này xử lý tất cả các thao tác dữ liệu liên quan đến người dùng: đăng ký,
 * xác thực, và CRUD cho tài khoản {@link Bidder}, {@link Seller}, {@link Admin}.
 * Sử dụng JDBC thông qua {@link DatabaseConnection} để truy vấn MySQL.
 * </p>
 *
 * <h3>Singleton Pattern:</h3>
 * {@code UserDAO} được triển khai dưới dạng Singleton an toàn đa luồng
 * (double-checked locking) để đảm bảo chỉ có một điểm truy cập dữ liệu
 * người dùng duy nhất trong toàn bộ ứng dụng server.
 *
 * <h3>Cấu trúc bảng:</h3>
 * <pre>
 *   users (
 *       username  VARCHAR(50)  PRIMARY KEY,
 *       password  VARCHAR(255) NOT NULL,
 *       email     VARCHAR(100) NOT NULL UNIQUE,
 *       role      VARCHAR(20)  NOT NULL   -- BIDDER / SELLER / ADMIN
 *   )
 * </pre>
 *
 * <h3>Mapping kiểu User:</h3>
 * Cột {@code role} được dùng để xác định tạo {@link Bidder}, {@link Seller},
 * hay {@link Admin} khi đọc từ database.
 *
 * <h3>Ví dụ sử dụng:</h3>
 * <pre>{@code
 *   UserDAO userDAO = UserDAO.getInstance();
 *   userDAO.save("john", new Bidder("john", "pass123", "john@mail.com"));
 *
 *   User user = userDAO.authenticate("john", "pass123");
 *   if (user != null) {
 *       System.out.println("Đăng nhập thành công: " + user.getDisplayInfo());
 *   }
 * }</pre>
 *
 * @see User
 * @see Bidder
 * @see Seller
 * @see Admin
 * @see GenericDAO
 * @see DatabaseConnection
 */
public class UserDAO implements GenericDAO<String, User> {

    // ========================== Singleton ==========================

    /** Instance duy nhất của UserDAO. */
    private static volatile UserDAO instance;

    /**
     * Trả về instance Singleton của {@code UserDAO}.
     * Sử dụng double-checked locking để khởi tạo lazy an toàn đa luồng.
     *
     * @return instance Singleton của {@code UserDAO}
     */
    public static UserDAO getInstance() {
        if (instance == null) {
            synchronized (UserDAO.class) {
                if (instance == null) {
                    instance = new UserDAO();
                }
            }
        }
        return instance;
    }

    // ========================== Constructor ==========================

    /**
     * Constructor private — sử dụng {@link #getInstance()} để lấy Singleton.
     * Tự động tạo bảng {@code users} nếu chưa tồn tại.
     */
    private UserDAO() {
        createTableIfNotExists();
        System.out.println("[UserDAO] Đã khởi tạo với MySQL. Hiện có " + count() + " người dùng.");
    }

    // ========================== Tạo bảng ==========================

    /**
     * Tạo bảng {@code users} trong MySQL nếu chưa tồn tại.
     */
    private void createTableIfNotExists() {
        String sql = "CREATE TABLE IF NOT EXISTS users ("
                + "username  VARCHAR(50)  PRIMARY KEY, "
                + "password  VARCHAR(255) NOT NULL, "
                + "email     VARCHAR(100) NOT NULL UNIQUE, "
                + "role      VARCHAR(20)  NOT NULL"
                + ")";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("[UserDAO] Không thể tạo bảng users", e);
        }
    }

    // ========================== Triển khai GenericDAO ==========================

    /**
     * Đăng ký một người dùng mới vào hệ thống.
     * <p>
     * User được lưu trực tiếp vào MySQL. Nếu user với username này đã tồn tại,
     * thao tác bị từ chối và in ra cảnh báo.
     * </p>
     *
     * @param username tên đăng nhập duy nhất (khóa)
     * @param user     đối tượng User (Bidder, Seller, hoặc Admin)
     * @throws IllegalArgumentException nếu username rỗng/null hoặc user là null
     */
    @Override
    public void save(String username, User user) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username không được để trống hoặc null");
        }
        if (user == null) {
            throw new IllegalArgumentException("User không được null");
        }

        // Kiểm tra trùng username
        if (exists(username)) {
            System.err.println("[UserDAO] Cảnh báo: User '" + username + "' đã tồn tại. Dùng update() thay thế.");
            return;
        }

        String sql = "INSERT INTO users (username, password, email, role) VALUES (?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, user.getPassword());
            ps.setString(3, user.getEmail());
            ps.setString(4, user.getRole());
            ps.executeUpdate();
            System.out.println("[UserDAO] Đã lưu user: " + username + " (vai trò: " + user.getRole() + ")");
        } catch (SQLException e) {
            throw new RuntimeException("[UserDAO] Lỗi khi lưu user: " + username, e);
        }
    }

    /**
     * Tìm người dùng theo username.
     *
     * @param username tên đăng nhập cần tìm
     * @return {@link User} nếu tìm thấy, hoặc {@code null} nếu không tồn tại
     */
    @Override
    public User findById(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToUser(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("[UserDAO] Lỗi khi tìm user: " + username, e);
        }
    }

    /**
     * Trả về danh sách tất cả người dùng đã đăng ký.
     *
     * @return danh sách tất cả user; trả về danh sách rỗng nếu không có
     */
    @Override
    public List<User> findAll() {
        String sql = "SELECT * FROM users";
        List<User> result = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapResultSetToUser(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("[UserDAO] Lỗi khi lấy tất cả user", e);
        }
        return result;
    }

    /**
     * Cập nhật thông tin của một người dùng đã tồn tại.
     * <p>
     * User được xác định bằng username. Nếu không tìm thấy user
     * với username này, thao tác cập nhật bị từ chối.
     * </p>
     *
     * @param username tên đăng nhập của user cần cập nhật
     * @param user     dữ liệu User mới
     * @return {@code true} nếu tìm thấy và cập nhật thành công, {@code false} nếu không
     */
    @Override
    public boolean update(String username, User user) {
        String sql = "UPDATE users SET password = ?, email = ?, role = ? WHERE username = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getPassword());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getRole());
            ps.setString(4, username);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                System.out.println("[UserDAO] Đã cập nhật user: " + username);
                return true;
            }
            return false;
        } catch (SQLException e) {
            throw new RuntimeException("[UserDAO] Lỗi khi cập nhật user: " + username, e);
        }
    }

    /**
     * Xóa một người dùng theo username.
     *
     * @param username tên đăng nhập của user cần xóa
     * @return {@code true} nếu tìm thấy và xóa thành công, {@code false} nếu không
     */
    @Override
    public boolean delete(String username) {
        String sql = "DELETE FROM users WHERE username = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                System.out.println("[UserDAO] Đã xóa user: " + username);
                return true;
            }
            return false;
        } catch (SQLException e) {
            throw new RuntimeException("[UserDAO] Lỗi khi xóa user: " + username, e);
        }
    }

    /**
     * Kiểm tra xem user với username cho trước có tồn tại hay không.
     *
     * @param username tên đăng nhập cần kiểm tra
     * @return {@code true} nếu tồn tại
     */
    @Override
    public boolean exists(String username) {
        String sql = "SELECT COUNT(*) FROM users WHERE username = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
                return false;
            }
        } catch (SQLException e) {
            throw new RuntimeException("[UserDAO] Lỗi khi kiểm tra tồn tại: " + username, e);
        }
    }

    /**
     * Trả về tổng số người dùng đã đăng ký.
     *
     * @return số lượng user
     */
    @Override
    public int count() {
        String sql = "SELECT COUNT(*) FROM users";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        } catch (SQLException e) {
            throw new RuntimeException("[UserDAO] Lỗi khi đếm user", e);
        }
    }

    // ========================== Phương thức Xác thực ==========================

    /**
     * Xác thực người dùng bằng cách kiểm tra username và password.
     * <p>
     * Đây là phương thức chính được sử dụng trong luồng đăng nhập.
     * Thực hiện so sánh phân biệt chữ hoa/thường cho cả username và password.
     * </p>
     *
     * @param username tên đăng nhập cần xác thực
     * @param password mật khẩu cần kiểm tra
     * @return đối tượng {@link User} nếu thông tin hợp lệ,
     *         hoặc {@code null} nếu xác thực thất bại
     */
    public User authenticate(String username, String password) {
        String sql = "SELECT * FROM users WHERE username = ? AND password = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, password);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    System.out.println("[UserDAO] Xác thực thành công cho: " + username);
                    return mapResultSetToUser(rs);
                }
                System.out.println("[UserDAO] Xác thực thất bại cho: " + username);
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("[UserDAO] Lỗi khi xác thực user: " + username, e);
        }
    }

    // ========================== Phương thức Truy vấn ==========================

    /**
     * Tìm người dùng theo địa chỉ email.
     *
     * @param email địa chỉ email cần tìm
     * @return {@link User} nếu tìm thấy, hoặc {@code null} nếu không có user nào có email này
     */
    public User findByEmail(String email) {
        String sql = "SELECT * FROM users WHERE email = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToUser(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("[UserDAO] Lỗi khi tìm user theo email: " + email, e);
        }
    }

    /**
     * Tìm tất cả người dùng theo vai trò (role) cụ thể.
     *
     * @param role vai trò cần lọc (VD: "BIDDER", "SELLER", "ADMIN")
     * @return danh sách user có vai trò tương ứng
     */
    public List<User> findByRole(String role) {
        String sql = "SELECT * FROM users WHERE UPPER(role) = ?";
        List<User> result = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, role.toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapResultSetToUser(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("[UserDAO] Lỗi khi tìm user theo role: " + role, e);
        }
        return result;
    }

    /**
     * Kiểm tra xem địa chỉ email đã được đăng ký hay chưa.
     *
     * @param email email cần kiểm tra
     * @return {@code true} nếu email đã được sử dụng
     */
    public boolean isEmailTaken(String email) {
        return findByEmail(email) != null;
    }

    // ========================== Phương thức Private ==========================

    /**
     * Chuyển đổi một dòng {@link ResultSet} thành đối tượng {@link User}.
     *
     * @param rs ResultSet đang trỏ tới dòng cần đọc
     * @return đối tượng User
     * @throws SQLException nếu lỗi đọc dữ liệu
     */
    private User mapResultSetToUser(ResultSet rs) throws SQLException {
        String username = rs.getString("username");
        String password = rs.getString("password");
        String email = rs.getString("email");
        String role = rs.getString("role");

        // Chuẩn hóa vai trò về USER hoặc ADMIN
        String normalizedRole = "ADMIN".equalsIgnoreCase(role) ? "ADMIN" : "USER";

        return new User(username, password, email, normalizedRole);
    }
}
