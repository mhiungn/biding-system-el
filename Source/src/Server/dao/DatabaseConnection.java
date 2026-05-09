package Server.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Lớp tiện ích quản lý kết nối tới cơ sở dữ liệu MySQL. Ap dung Singleton
 * <p>
 * Cung cấp kết nối JDBC cho tất cả các lớp DAO trong hệ thống đấu giá.
 * Tất cả cấu hình kết nối (URL, username, password) được quản lý tập trung tại đây.
 * </p>
 *
 * <h3>Ví dụ sử dụng:</h3>
 * <pre>{@code
 *   try (Connection conn = DatabaseConnection.getConnection()) {
 *       PreparedStatement ps = conn.prepareStatement("SELECT * FROM users");
 *       ResultSet rs = ps.executeQuery();
 *       // ...
 *   }
 * }</pre>
 *
 * @see UserDAO
 * @see ItemDAO
 * @see AuctionDAO
 */
public class DatabaseConnection {

    // ========================== Cấu hình kết nối ==========================

    /** URL kết nối tới MySQL server. */
    private static final String URL = "jdbc:mysql://localhost:3306/hethongdaugia";

    /** Tên đăng nhập MySQL. */
    private static final String USER = "root";

    /** Mật khẩu MySQL. */
    private static final String PASSWORD = "123456";

    // ========================== Phương thức ==========================

    /**
     * Tạo và trả về một kết nối mới tới cơ sở dữ liệu MySQL.
     * <p>
     * Người gọi có trách nhiệm đóng kết nối sau khi sử dụng xong.
     * Nên sử dụng try-with-resources để đảm bảo kết nối luôn được đóng.
     * </p>
     *
     * @return kết nối JDBC tới cơ sở dữ liệu
     * @throws SQLException nếu không thể kết nối tới cơ sở dữ liệu
     */
    private static Connection instance;
    public static Connection getConnection() throws SQLException {
        if (instance == null || instance.isClosed()) {
            try {
                // Đăng ký Driver (với các bản Java mới có thể bỏ qua nhưng nên viết cho chắc)
                Class.forName("com.mysql.cj.jdbc.Driver");
                instance = DriverManager.getConnection(URL, USER, PASSWORD);
                System.out.println("Kết nối MySQL thành công!");
            } catch (ClassNotFoundException e) {
                System.err.println("Không tìm thấy MySQL Driver!");
                e.printStackTrace();
            }
        }
        return instance;
    }
}
