import Client.app.ClientApp;
import Server.dao.DatabaseConnection;
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
            }
        } catch (SQLException e) {
            System.err.println("CONNECT FAILED! CHECK YOUR INTERNET or User/Pass.");
            e.printStackTrace(); // Helpful for debugging why it failed
        }

        // Now launch the GUI
        ClientApp.main(args);
    }
}
