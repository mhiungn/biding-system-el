package Client.features.dashboard;

import CommonClasses.Items.Item;
import Server.dao.ItemDAO;

import java.util.ArrayList;
import java.util.List;

/**
 * Loads dashboard domain data (items, stats) from persistence.
 */
public class DashboardService {

    public List<Item> loadAllItems() {
        try {
            return ItemDAO.getInstance().findAll();
        } catch (Exception e) {
            System.err.println("Lỗi khi tải dữ liệu từ database: " + e.getMessage());
            return new ArrayList<>();
        }
    }
}
