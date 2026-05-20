package Server.dao;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Lớp tiện ích cung cấp H2 in-memory database cho DAO tests.
 * <p>
 * Redirect DatabaseConnection (dùng sun.misc.Unsafe) để tất cả DAO
 * kết nối tới H2 in-memory thay vì MySQL thật.
 * </p>
 * <p>
 * <b>QUAN TRỌNG:</b> {@link #redirectToH2()} phải được gọi TRƯỚC khi
 * bất kỳ DAO Singleton nào được khởi tạo.
 * </p>
 */
public class TestDatabaseHelper {

    /** 
     * URL kết nối H2 in-memory với MySQL compatibility mode.
     * DB_CLOSE_DELAY=-1 giữ DB tồn tại cho tới khi JVM tắt.
     */
    private static final String H2_URL = 
            "jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE";
    private static final String H2_USER = "sa";
    private static final String H2_PASSWORD = "";

    private static boolean redirected = false;

    /**
     * Redirect DatabaseConnection để trả về H2 connection thay vì MySQL thật.
     * Chỉ thực hiện 1 lần.
     */
    public static void redirectToH2() throws Exception {
        if (redirected) return;
        DatabaseConnection.setConnectionParams(H2_URL, H2_USER, H2_PASSWORD);
        redirected = true;
    }

    /**
     * Tạo tất cả các bảng cần thiết cho DAO tests.
     * Sử dụng DatabaseConnection.getConnection() (đã redirect sang H2).
     */
    public static void createAllTables() throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {

            // Bảng users
            stmt.execute("CREATE TABLE IF NOT EXISTS users ("
                    + "username  VARCHAR(50)  PRIMARY KEY, "
                    + "password  VARCHAR(255) NOT NULL, "
                    + "email     VARCHAR(100) NOT NULL UNIQUE, "
                    + "role      VARCHAR(20)  NOT NULL"
                    + ")");

            // Bảng items
            stmt.execute("CREATE TABLE IF NOT EXISTS items ("
                    + "item_id         VARCHAR(36)    PRIMARY KEY, "
                    + "name            VARCHAR(255)   NOT NULL, "
                    + "starting_price  FLOAT          NOT NULL, "
                    + "description     TEXT, "
                    + "item_type       VARCHAR(50)    NOT NULL, "
                    + "seller_username VARCHAR(50)"
                    + ")");

            // Bảng auction_snapshots
            stmt.execute("CREATE TABLE IF NOT EXISTS auction_snapshots ("
                    + "auction_id          INT           PRIMARY KEY, "
                    + "client_owner        VARCHAR(50)   NOT NULL, "
                    + "item_id             VARCHAR(36)   NOT NULL, "
                    + "created_at          DATETIME, "
                    + "terminate_at        DATETIME, "
                    + "type                VARCHAR(30), "
                    + "status              VARCHAR(20)   NOT NULL DEFAULT 'OPEN', "
                    + "was_in_countdown    BOOLEAN       NOT NULL DEFAULT FALSE, "
                    + "FOREIGN KEY (item_id) REFERENCES items(item_id) ON DELETE CASCADE"
                    + ")");

            // Bảng auction_bids
            stmt.execute("CREATE TABLE IF NOT EXISTS auction_bids ("
                    + "id                  INT           AUTO_INCREMENT PRIMARY KEY, "
                    + "auction_id          INT           NOT NULL, "
                    + "bid_amount          FLOAT         NOT NULL, "
                    + "bidder_username     VARCHAR(50), "
                    + "created_at          DATETIME, "
                    + "bid_order           INT           NOT NULL, "
                    + "FOREIGN KEY (auction_id) REFERENCES auction_snapshots(auction_id) ON DELETE CASCADE"
                    + ")");

            // Bảng auction_participants  
            stmt.execute("CREATE TABLE IF NOT EXISTS auction_participants ("
                    + "auction_id          INT           NOT NULL, "
                    + "username            VARCHAR(50)   NOT NULL, "
                    + "PRIMARY KEY (auction_id, username), "
                    + "FOREIGN KEY (auction_id) REFERENCES auction_snapshots(auction_id) ON DELETE CASCADE"
                    + ")");

            // Bảng bid_transactions
            stmt.execute("CREATE TABLE IF NOT EXISTS bid_transactions ("
                    + "transaction_id   VARCHAR(36)  PRIMARY KEY, "
                    + "auction_id       INT          NOT NULL, "
                    + "bid_amount       FLOAT        NOT NULL, "
                    + "bid_created_at   DATETIME     NULL, "
                    + "bidder_username  VARCHAR(50)  NOT NULL, "
                    + "timestamp        DATETIME     NOT NULL, "
                    + "successful       BOOLEAN      NOT NULL"
                    + ")");
        }
    }

    /**
     * Xóa sạch dữ liệu trong tất cả các bảng (giữ lại schema).
     * Sử dụng DatabaseConnection.getConnection() để đảm bảo xóa đúng DB.
     */
    public static void clearAllTables() throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("SET REFERENTIAL_INTEGRITY FALSE");
            stmt.execute("DELETE FROM auction_bids");
            stmt.execute("DELETE FROM auction_participants");
            stmt.execute("DELETE FROM auction_snapshots");
            stmt.execute("DELETE FROM bid_transactions");
            stmt.execute("DELETE FROM items");
            stmt.execute("DELETE FROM users");
            stmt.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }

    /**
     * Xóa tất cả bảng.
     */
    public static void dropAllTables() throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP ALL OBJECTS");
        }
    }

    /**
     * Reset singleton instance của một DAO class.
     */
    public static void resetSingleton(Class<?> daoClass) throws Exception {
        java.lang.reflect.Field instanceField = daoClass.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }
}
