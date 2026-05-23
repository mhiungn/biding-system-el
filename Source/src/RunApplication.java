import Client.app.ClientApp;
import Server.dao.DatabaseConnection;
import Server.dao.UserDAO;
import Server.dao.ItemDAO;
import Server.dao.AuctionDAO;
import Server.dao.BidTransactionDAO;
import java.sql.Connection;
import java.sql.SQLException;

public class RunApplication {
    public static void main(String[] args) {
        System.out.println("Checking database connection...");
        System.out.flush(); // Force this to show up immediately

        // Kiểm tra kết nối Database trước khi chạy App
        try (Connection conn = DatabaseConnection.getConnection()) {
            if (conn != null && !conn.isClosed()) {
                System.out.println("--- CONNECTED SUCCESSFULLY ---");
                System.out.println("Server: Clever Cloud Online");
                System.out.println("Database: " + conn.getCatalog());
                System.out.flush(); // Ensure success messages appear before UI starts

                System.out.println("Initializing database tables...");
                System.out.flush();
                // Khởi tạo các DAO theo đúng thứ tự phụ thuộc để đảm bảo tạo bảng an toàn
                UserDAO.getInstance(); // Tạo bảng users
                ItemDAO.getInstance(); // Tạo bảng items
                AuctionDAO.getInstance(); // Tạo bảng auction_snapshots, bids, participants
                BidTransactionDAO.getInstance(); // Tạo bảng bid_transactions
                System.out.println("Database tables initialized successfully!");
                System.out.flush();
            }
        } catch (SQLException e) {
            System.err.println("CONNECT FAILED! CHECK YOUR INTERNET or User/Pass.");
            e.printStackTrace(); // Helpful for debugging why it failed
        }

        // Now launch the GUI
        ClientApp.main(args);
    }
}
