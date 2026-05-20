package Server.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Lớp tiện ích quản lý kết nối tới cơ sở dữ liệu MySQL.
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
    private static String URL = "jdbc:mysql://blbsc98ma5stojowrgcs-mysql.services.clever-cloud.com:3306/blbsc98ma5stojowrgcs";

    /** Tên đăng nhập MySQL. */
    private static String USER = "urhbndcybrfhy0sb";

    /** Mật khẩu MySQL. */
    private static String PASSWORD = "Gt37ZauKWCr4UeTUNiMt";

    // ========================== Cấu hình cho Testing ==========================
    public static void setConnectionParams(String url, String user, String password) {
        URL = url;
        USER = user;
        PASSWORD = password;
    }

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
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
