package Client.features.dashboard;

import Client.core.network.NetworkRequestClient;
import CommonClasses.dto.DashboardAuctionRow;
import CommonClasses.dto.DashboardPageResult;
import CommonClasses.dto.DashboardStats;
import Packets.MessageType;
import Packets.PacketMessage;
import Server.dao.AuctionDAO;
import Server.service.AuctionFinalizationService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads dashboard domain data (items, stats) from persistence.
 */
public class DashboardService {

    public static final int PAGE_SIZE = 12;

    public DashboardPageResult loadAuctionPage(int pageNumber, String category, boolean endingSoon, Float minPrice, Float maxPrice) {
        if (NetworkRequestClient.isEnabled()) {
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("page", pageNumber);
                payload.put("category", category);
                payload.put("endingSoon", endingSoon);
                payload.put("minPrice", minPrice);
                payload.put("maxPrice", maxPrice);
                payload.put("pageSize", PAGE_SIZE);
                PacketMessage response = NetworkRequestClient.request(
                        MessageType.DASHBOARD_PAGE_REQUEST,
                        (HashMap<String, Object>) payload,
                        MessageType.DASHBOARD_PAGE_RESPONSE);
                if (response.getPayload() instanceof DashboardPageResult) {
                    return (DashboardPageResult) response.getPayload();
                }
            } catch (IOException e) {
                System.err.println("[DashboardService] Network page load unavailable, using DAO fallback: "
                        + e.getMessage());
            }
        }

        try {
            finalizeExpiredAuctions("dashboard page load");
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
            System.err.println("Lỗi khi tải auction page từ database: " + rootMessage(e));
            return new DashboardPageResult(new ArrayList<>(), 0);
        }
    }

    public DashboardStats loadStats() {
        if (NetworkRequestClient.isEnabled()) {
            try {
                PacketMessage response = NetworkRequestClient.request(
                        MessageType.DASHBOARD_STATS_REQUEST, null, MessageType.DASHBOARD_STATS_RESPONSE);
                if (response.getPayload() instanceof DashboardStats) {
                    return (DashboardStats) response.getPayload();
                }
            } catch (IOException e) {
                System.err.println("[DashboardService] Network stats unavailable, using DAO fallback: "
                        + e.getMessage());
            }
        }

        try {
            finalizeExpiredAuctions("dashboard stats load");
            AuctionDAO auctionDAO = AuctionDAO.getInstance();
            return new DashboardStats(
                    auctionDAO.countActiveAuctions(),
                    auctionDAO.countEndingTodayAuctions(),
                    auctionDAO.countTotalBids()
            );
        } catch (Exception e) {
            System.err.println("Lỗi khi tải dashboard stats từ database: " + rootMessage(e));
            return new DashboardStats(0, 0, 0);
        }
    }

    private String rootMessage(Exception exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? exception.getMessage() : message;
    }

    private void finalizeExpiredAuctions(String trigger) {
        AuctionFinalizationService.getInstance().finalizeEndedAuctionsSafely(trigger);
    }
}
