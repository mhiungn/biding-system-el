package Server.service;

import CommonClasses.Bid;
import CommonClasses.Items.Item;
import Server.dao.AuctionDAO;
import Server.dao.AuctionSnapshot;

import java.util.List;

public class AuctionFinalizationService {
    private static final String AUCTION_SOLD = "AUCTION_SOLD";
    private static final String AUCTION_WON = "AUCTION_WON";
    private static final String AUCTION_LOST = "AUCTION_LOST";
    private static final String AUCTION_ENDED_NO_BIDS = "AUCTION_ENDED_NO_BIDS";

    private static volatile AuctionFinalizationService instance;

    public static AuctionFinalizationService getInstance() {
        if (instance == null) {
            synchronized (AuctionFinalizationService.class) {
                if (instance == null) {
                    instance = new AuctionFinalizationService();
                }
            }
        }
        return instance;
    }

    private final AuctionDAO auctionDAO;
    private final WalletApplicationService walletService;
    private final NotificationApplicationService notificationService;
    private final NetworkPushService pushService;

    private AuctionFinalizationService() {
        this.auctionDAO = AuctionDAO.getInstance();
        this.walletService = WalletApplicationService.getInstance();
        this.notificationService = NotificationApplicationService.getInstance();
        this.pushService = NetworkPushService.getInstance();
    }

    public int finalizeEndedAuctions() {
        List<AuctionSnapshot> expired = auctionDAO.findExpiredOpenRunningAuctions();
        int finalized = 0;
        for (AuctionSnapshot snapshot : expired) {
            if (finalizeAuction(snapshot)) {
                finalized++;
            }
        }
        return finalized;
    }

    public int finalizeEndedAuctionsSafely(String trigger) {
        try {
            int finalized = finalizeEndedAuctions();
            if (finalized > 0) {
                System.out.println("[AuctionFinalizationService] Finalized " + finalized
                        + " expired auction(s) from " + trigger + ".");
            }
            return finalized;
        } catch (Exception e) {
            System.err.println("[AuctionFinalizationService] Failed to finalize expired auctions from "
                    + trigger + ": " + rootMessage(e));
            return 0;
        }
    }

    private boolean finalizeAuction(AuctionSnapshot snapshot) {
        if (snapshot == null || snapshot.getTerminateAt() == null
                || snapshot.getTerminateAt().getTime() > System.currentTimeMillis()) {
            return false;
        }

        int auctionId = snapshot.getAuctionId();
        boolean statusUpdated = auctionDAO.markAuctionFinished(auctionId);
        if (!statusUpdated) {
            return false;
        }

        Bid winningBid = auctionDAO.getHighestBidForAuction(auctionId);
        if (winningBid == null) {
            notifyNoBidAuction(snapshot);
            pushAuctionUpdateQuietly(auctionId, "AUCTION_FINALIZED");
            return true;
        }

        String winner = winningBid.getBidderUsername();
        long finalAmount = (long) Math.ceil(winningBid.getBid());
        if (winner != null && !winner.isBlank() && !walletService.hasSpentForAuction(winner, auctionId)) {
            walletService.finalizeWinningPayment(winner, auctionId, finalAmount);
        }

        String seller = snapshot.getClientOwner();
        if (seller != null && !seller.isBlank() && !walletService.hasEarnedForAuction(seller, auctionId)) {
            walletService.creditSellerPayout(seller, auctionId, finalAmount);
            pushWalletUpdateQuietly(seller, "AUCTION_FINALIZED");
        }

        notifySoldAuction(snapshot, winner, finalAmount);
        pushAuctionUpdateQuietly(auctionId, "AUCTION_FINALIZED");
        if (winner != null && !winner.isBlank()) {
            pushWalletUpdateQuietly(winner, "AUCTION_FINALIZED");
        }
        return true;
    }

    private void notifyNoBidAuction(AuctionSnapshot snapshot) {
        int auctionId = snapshot.getAuctionId();
        String seller = snapshot.getClientOwner();
        if (!notificationService.hasNotification(seller, auctionId, AUCTION_ENDED_NO_BIDS)) {
            notificationService.notifyAuctionEndedNoBids(
                    seller,
                    auctionId,
                    itemName(snapshot));
            pushNotificationUpdateQuietly(seller, auctionId, AUCTION_ENDED_NO_BIDS);
        }
    }

    private void notifySoldAuction(AuctionSnapshot snapshot, String winner, long finalAmount) {
        int auctionId = snapshot.getAuctionId();
        String itemName = itemName(snapshot);
        String seller = snapshot.getClientOwner();
        if (!notificationService.hasNotification(seller, auctionId, AUCTION_SOLD)) {
            notificationService.notifyAuctionSold(
                    seller,
                    auctionId,
                    itemName,
                    finalAmount);
            pushNotificationUpdateQuietly(seller, auctionId, AUCTION_SOLD);
        }

        if (winner != null && !winner.isBlank()
                && !notificationService.hasNotification(winner, auctionId, AUCTION_WON)) {
            notificationService.notifyAuctionWon(winner, auctionId, itemName, finalAmount);
            pushNotificationUpdateQuietly(winner, auctionId, AUCTION_WON);
        }

        for (String loser : auctionDAO.findLosingBiddersForAuction(auctionId)) {
            if (!notificationService.hasNotification(loser, auctionId, AUCTION_LOST)) {
                notificationService.notifyAuctionLost(loser, auctionId, itemName);
                pushNotificationUpdateQuietly(loser, auctionId, AUCTION_LOST);
            }
        }
    }

    private void pushAuctionUpdateQuietly(int auctionId, String reason) {
        try {
            pushService.pushAuctionUpdate(auctionId, reason);
        } catch (RuntimeException e) {
            System.err.println("[AuctionFinalizationService] Auction finalized but auction push failed: "
                    + e.getMessage());
        }
    }

    private void pushWalletUpdateQuietly(String username, String reason) {
        try {
            pushService.pushWalletUpdate(username, reason);
        } catch (RuntimeException e) {
            System.err.println("[AuctionFinalizationService] Auction finalized but wallet push failed: "
                    + e.getMessage());
        }
    }

    private void pushNotificationUpdateQuietly(String username, int auctionId, String type) {
        try {
            pushService.pushNotificationUpdate(username, auctionId, type);
        } catch (RuntimeException e) {
            System.err.println("[AuctionFinalizationService] Auction finalized but notification push failed: "
                    + e.getMessage());
        }
    }

    private String itemName(AuctionSnapshot snapshot) {
        Item item = snapshot.getItem();
        return item == null || item.getName() == null || item.getName().isBlank()
                ? "auction item"
                : item.getName();
    }

    private String rootMessage(Exception exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? exception.getMessage() : message;
    }
}
