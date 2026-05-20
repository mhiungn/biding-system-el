package Client.features.dashboard;

import Server.dao.DashboardAuctionRow;

import java.util.List;

public class DashboardPageResult {
    private final List<DashboardAuctionRow> rows;
    private final int totalItems;

    public DashboardPageResult(List<DashboardAuctionRow> rows, int totalItems) {
        this.rows = rows;
        this.totalItems = totalItems;
    }

    public List<DashboardAuctionRow> getRows() {
        return rows;
    }

    public int getTotalItems() {
        return totalItems;
    }
}
