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
    private static String URL;
    private static String USER;
    private static String PASSWORD;

    static {
        try (java.io.InputStream input = DatabaseConnection.class.getResourceAsStream("/db.properties")) {
            java.util.Properties prop = new java.util.Properties();
            if (input == null) {
                System.err.println("Không tìm thấy file db.properties!");
            } else {
                prop.load(input);
                URL = prop.getProperty("db.url");
                USER = prop.getProperty("db.user");
                PASSWORD = prop.getProperty("db.password");
            }
        } catch (java.io.IOException ex) {
            ex.printStackTrace();
        }
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
    private static Connection instance;
    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            if (URL == null || USER == null || PASSWORD == null) {
                throw new SQLException("Cấu hình database chưa được tải!");
            }
            return DriverManager.getConnection(URL, USER, PASSWORD);
        } catch (ClassNotFoundException e) {
            throw new SQLException("Không tìm thấy MySQL Driver!", e);
        }
    }

    public static void setConnectionParams(String url, String user, String password) throws SQLException {
        URL = url;
        USER = user;
        PASSWORD = password;
        if (instance != null && !instance.isClosed()) {
            instance.close();
        }
        instance = null;
    }
}
