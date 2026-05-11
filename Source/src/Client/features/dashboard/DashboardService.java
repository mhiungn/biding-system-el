package Client.features.dashboard;

import Server.dao.AuctionDAO;
import Server.dao.DashboardAuctionRow;

import java.util.ArrayList;
import java.util.List;

/**
 * Loads dashboard domain data (items, stats) from persistence.
 */
public class DashboardService {

    public static final int PAGE_SIZE = 12;

    public DashboardPageResult loadAuctionPage(int pageNumber, String category, boolean endingSoon, Float minPrice, Float maxPrice) {
        try {
            AuctionDAO auctionDAO = AuctionDAO.getInstance();
            int total = auctionDAO.countDashboardAuctions(category, endingSoon, minPrice, maxPrice);
            int safePage = Math.max(pageNumber, 0);
            int offset = safePage * PAGE_SIZE;
            if (offset >= total && total > 0) {
                safePage = (int) Math.ceil((double) total / PAGE_SIZE) - 1;
                offset = safePage * PAGE_SIZE;
            }
            List<DashboardAuctionRow> rows = auctionDAO.findDashboardAuctions(
                    category, endingSoon, minPrice, maxPrice, PAGE_SIZE, offset
            );
            return new DashboardPageResult(rows, total);
        } catch (Exception e) {
            System.err.println("Lỗi khi tải auction page từ database: " + e.getMessage());
            return new DashboardPageResult(new ArrayList<>(), 0);
        }
    }

    public DashboardStats loadStats() {
        try {
            AuctionDAO auctionDAO = AuctionDAO.getInstance();
            return new DashboardStats(
                    auctionDAO.countActiveAuctions(),
                    auctionDAO.countEndingTodayAuctions(),
                    auctionDAO.countTotalBids()
            );
        } catch (Exception e) {
            System.err.println("Lỗi khi tải dashboard stats từ database: " + e.getMessage());
            return new DashboardStats(0, 0, 0);
        }
    }
}
