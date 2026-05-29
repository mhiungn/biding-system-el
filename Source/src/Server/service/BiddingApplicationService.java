package Server.service;

import CommonClasses.Bid;
import Server.dao.AuctionDAO;
import Server.dao.DatabaseConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Date;

public class BiddingApplicationService {
    private static volatile BiddingApplicationService instance;

    public static BiddingApplicationService getInstance() {
        if (instance == null) {
            synchronized (BiddingApplicationService.class) {
                if (instance == null) {
                    instance = new BiddingApplicationService();
                }
            }
        }
        return instance;
    }

    private final AuctionDAO auctionDAO;
    private final WalletApplicationService walletService;
    private final NotificationApplicationService notificationService;

    private BiddingApplicationService() {
        this.auctionDAO = AuctionDAO.getInstance();
        this.walletService = WalletApplicationService.getInstance();
        this.notificationService = NotificationApplicationService.getInstance();
    }

    /**
     * Đặt giá thủ công (manual bid). Sau khi thành công sẽ tự động
     * kích hoạt chuỗi Auto-Bid nếu có cấu hình auto-bid trên phiên này.
     *
     * @param username  username người đặt giá
     * @param auctionId ID phiên đấu giá
     * @param amount    số tiền đặt giá
     * @return {@code true} nếu đặt giá thành công
     */
    public boolean placeBid(String username, int auctionId, float amount) {
        return placeBid(username, auctionId, amount, false);
    }

    /**
     * Đặt giá với cờ phân biệt manual/auto bid.
     * <p>
     * Khi {@code isAutoBid = false} (bid thủ công từ client), sau khi commit
     * thành công sẽ gọi {@link AutoBidManager#processAutoBids} để kích hoạt
     * chuỗi đấu giá tự động.
     * </p>
     * <p>
     * Khi {@code isAutoBid = true} (bid từ AutoBidManager), KHÔNG gọi lại
     * processAutoBids vì AutoBidManager đã xử lý iterative loop nội bộ.
     * </p>
     *
     * @param username  username người đặt giá
     * @param auctionId ID phiên đấu giá
     * @param amount    số tiền đặt giá
     * @param isAutoBid {@code true} nếu đây là lượt đặt giá từ AutoBidManager
     * @return {@code true} nếu đặt giá thành công
     */
    public boolean placeBid(String username, int auctionId, float amount, boolean isAutoBid) {
        if (username == null || username.isBlank()) {
            return false;
        }

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            AuctionDAO.BidPlacementSnapshot auction = auctionDAO.lockBidPlacementSnapshot(conn, auctionId);
            if (auction == null) {
                return rollbackAndReject(conn);
            }
            if (isNotBiddable(auction)) {
                return rollbackAndReject(conn);
            }
            if (username.equals(auction.getOwnerUsername())) {
                return rollbackAndReject(conn);
            }
            if (amount <= 0) {
                return rollbackAndReject(conn);
            }
            if (amount < auction.getCurrentPrice() + auction.getMinimumBidIncrement()) {
                return rollbackAndReject(conn);
            }

            long moneyAmount = (long) Math.ceil(amount);
            if (!walletService.canAffordBid(conn, username, auctionId, moneyAmount)) {
                return rollbackAndReject(conn);
            }

            String previousHighestBidder = auctionDAO.getHighestBidderUsername(conn, auctionId);
            if (previousHighestBidder != null && !previousHighestBidder.equals(username)) {
                walletService.releaseBidHold(conn, previousHighestBidder, auctionId);
            }

            walletService.reserveBidAmount(conn, username, auctionId, moneyAmount);
            auctionDAO.addParticipant(conn, String.valueOf(auctionId), username);
            auctionDAO.addBid(conn, String.valueOf(auctionId), new Bid(new Date(), amount, username));
            auctionDAO.updateCurrentPriceForAuction(conn, auctionId, amount);
            if ("OPEN".equalsIgnoreCase(auction.getStatus())) {
                auctionDAO.updateStatus(conn, String.valueOf(auctionId), "RUNNING");
            }

            String itemName = auction.getItemName();
            if (auction.getOwnerUsername() != null && !auction.getOwnerUsername().equals(username)) {
                notificationService.notifySellerNewBid(conn, auction.getOwnerUsername(), auctionId, itemName, moneyAmount);
            }
            if (previousHighestBidder != null && !previousHighestBidder.equals(username)) {
                notificationService.notifyBidderOutbid(conn, previousHighestBidder, auctionId, itemName, moneyAmount);
            }

            applyAutoExtendIfNeeded(conn, auction);
            conn.commit();
            publishBidPushQuietly(auctionId, username, previousHighestBidder, auction.getOwnerUsername());

            // === AUTO-BID TRIGGER (chỉ khi bid thủ công, post-commit) ===
            if (!isAutoBid) {
                triggerAutoBidsQuietly(auctionId, username, amount);
            }

            return true;
        } catch (SQLException | RuntimeException e) {
            rollbackQuietly(conn);
            System.err.println("[BiddingApplicationService] Bid rejected due to transactional failure: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(conn);
        }
    }

    private void publishBidPushQuietly(int auctionId, String bidderUsername, String previousHighestBidder,
                                       String sellerUsername) {
        try {
            NetworkPushService.getInstance().pushBidAccepted(
                    auctionId,
                    bidderUsername,
                    previousHighestBidder,
                    sellerUsername);
        } catch (RuntimeException e) {
            System.err.println("[BiddingApplicationService] Bid accepted but push update failed: " + e.getMessage());
        }
    }

    /**
     * Kích hoạt chuỗi Auto-Bid sau khi một lượt bid thủ công commit thành công.
     * <p>
     * Được gọi NGOÀI transaction (post-commit) để đảm bảo:
     * <ul>
     *   <li>Bid thủ công đã hoàn tất và dữ liệu đã persist</li>
     *   <li>Auto-bid mỗi lượt là một transaction riêng biệt</li>
     *   <li>Lỗi auto-bid không ảnh hưởng đến bid thủ công đã commit</li>
     * </ul>
     * </p>
     *
     * @param auctionId       ID phiên đấu giá
     * @param triggerUsername  username người vừa đặt giá thủ công
     * @param currentPrice    giá hiện tại sau lượt đặt giá
     */
    private void triggerAutoBidsQuietly(int auctionId, String triggerUsername, float currentPrice) {
        try {
            AutoBidManager.getInstance().processAutoBids(auctionId, triggerUsername, currentPrice);
        } catch (RuntimeException e) {
            System.err.println("[BiddingApplicationService] Auto-bid processing failed (bid was accepted): "
                    + e.getMessage());
        }
    }

    private void applyAutoExtendIfNeeded(Connection conn, AuctionDAO.BidPlacementSnapshot auction) throws SQLException {
        if (!"Time_With_Reset".equals(auction.getType()) || auction.getTerminateAt() == null) {
            return;
        }

        long now = System.currentTimeMillis();
        long remaining = auction.getTerminateAt().getTime() - now;
        if (remaining > 0 && remaining <= 120_000L) {
            auctionDAO.updateTerminateAt(conn, String.valueOf(auction.getAuctionId()),
                    new Date(auction.getTerminateAt().getTime() + 300_000L));
        }
    }

    private boolean isNotBiddable(AuctionDAO.BidPlacementSnapshot auction) {
        if (auction.getTerminateAt() == null || auction.getTerminateAt().getTime() <= System.currentTimeMillis()) {
            return true;
        }
        String status = auction.getStatus();
        return !("OPEN".equalsIgnoreCase(status) || "RUNNING".equalsIgnoreCase(status));
    }

    private boolean rollbackAndReject(Connection conn) {
        rollbackQuietly(conn);
        return false;
    }

    private void rollbackQuietly(Connection conn) {
        if (conn == null) {
            return;
        }
        try {
            conn.rollback();
        } catch (SQLException e) {
            System.err.println("[BiddingApplicationService] Rollback failed: " + e.getMessage());
        }
    }

    private void closeQuietly(Connection conn) {
        if (conn == null) {
            return;
        }
        try {
            conn.setAutoCommit(true);
            conn.close();
        } catch (SQLException e) {
            System.err.println("[BiddingApplicationService] Close failed: " + e.getMessage());
        }
    }
}
