package Server.service;

import CommonClasses.dto.NotificationDTO;
import Server.dao.NotificationDAO;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class NotificationApplicationService {
    public static final String ACTION_AUCTION_DETAIL = "AUCTION_DETAIL";
    public static final String ACTION_MY_BIDS = "MY_BIDS";

    private static volatile NotificationApplicationService instance;

    public static NotificationApplicationService getInstance() {
        if (instance == null) {
            synchronized (NotificationApplicationService.class) {
                if (instance == null) {
                    instance = new NotificationApplicationService();
                }
            }
        }
        return instance;
    }

    private final NotificationDAO notificationDAO;

    private NotificationApplicationService() {
        this.notificationDAO = NotificationDAO.getInstance();
    }

    public List<NotificationDTO> getRecentNotifications(String username) {
        return notificationDAO.findRecentByUser(username, 25);
    }

    public int getUnreadCount(String username) {
        return notificationDAO.countUnread(username);
    }

    public void markRead(String username, long notificationId) {
        notificationDAO.markRead(notificationId, username);
    }

    public void markAllRead(String username) {
        notificationDAO.markAllRead(username);
    }

    public boolean hasNotification(String username, int auctionId, String type) {
        return notificationDAO.existsByUserAuctionAndType(username, auctionId, type);
    }

    public void notifySellerNewBid(String sellerUsername, int auctionId, String itemName, long amount) {
        notificationDAO.createNotification(
                sellerUsername,
                auctionId,
                "NEW_BID_ON_SELLER_ITEM",
                "New bid on your item",
                "Someone placed a bid of " + amount + " on " + itemName + ".",
                ACTION_AUCTION_DETAIL);
    }

    public void notifySellerNewBid(Connection conn, String sellerUsername, int auctionId,
                                   String itemName, long amount) throws SQLException {
        notificationDAO.createNotification(
                conn,
                sellerUsername,
                auctionId,
                "NEW_BID_ON_SELLER_ITEM",
                "New bid on your item",
                "Someone placed a bid of " + amount + " on " + itemName + ".",
                ACTION_AUCTION_DETAIL);
    }

    public void notifyBidderOutbid(String bidderUsername, int auctionId, String itemName, long newAmount) {
        notificationDAO.createNotification(
                bidderUsername,
                auctionId,
                "OUTBID",
                "You were outbid",
                "Someone placed a higher bid on " + itemName + ".",
                ACTION_AUCTION_DETAIL);
    }

    public void notifyBidderOutbid(Connection conn, String bidderUsername, int auctionId,
                                   String itemName, long newAmount) throws SQLException {
        notificationDAO.createNotification(
                conn,
                bidderUsername,
                auctionId,
                "OUTBID",
                "You were outbid",
                "Someone placed a higher bid on " + itemName + ".",
                ACTION_AUCTION_DETAIL);
    }

    public void notifyAuctionSold(String sellerUsername, int auctionId, String itemName, long amount) {
        notificationDAO.createNotification(
                sellerUsername,
                auctionId,
                "AUCTION_SOLD",
                "Item sold",
                "Your item " + itemName + " was sold for " + amount + ".",
                ACTION_MY_BIDS);
    }

    public void notifyAuctionWon(String winnerUsername, int auctionId, String itemName, long amount) {
        notificationDAO.createNotification(
                winnerUsername,
                auctionId,
                "AUCTION_WON",
                "You won the auction",
                "You won " + itemName + " for " + amount + ".",
                ACTION_MY_BIDS);
    }

    public void notifyAuctionEndedNoBids(String sellerUsername, int auctionId, String itemName) {
        notificationDAO.createNotification(
                sellerUsername,
                auctionId,
                "AUCTION_ENDED_NO_BIDS",
                "Auction ended",
                "Your item " + itemName + " ended without bids.",
                ACTION_MY_BIDS);
    }

    public void notifyAuctionLost(String bidderUsername, int auctionId, String itemName) {
        notificationDAO.createNotification(
                bidderUsername,
                auctionId,
                "AUCTION_LOST",
                "Auction ended",
                "You did not win " + itemName + ".",
                ACTION_MY_BIDS);
    }
}
