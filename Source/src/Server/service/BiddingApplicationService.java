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

    public boolean placeBid(String username, int auctionId, float amount) {
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
