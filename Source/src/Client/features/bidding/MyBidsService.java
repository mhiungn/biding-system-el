package Client.features.bidding;

import Server.dao.AuctionDAO;
import Server.dao.DashboardAuctionRow;

import java.util.ArrayList;
import java.util.List;

/**
 * Service layer for the My Bids screen.
 * <p>
 * Currently uses direct DAO access. When NetworkClient integration is complete,
 * this class will be refactored to send requests via the network socket instead.
 * </p>
 */
public class MyBidsService {

    /**
     * Returns all active auctions the user is participating in.
     *
     * @param username the logged-in user's username
     * @return list of active auction rows
     */
    public List<DashboardAuctionRow> loadActiveBids(String username) {
        try {
            return AuctionDAO.getInstance().findActiveAuctionsByParticipant(username);
        } catch (Exception e) {
            System.err.println("[MyBidsService] Error loading active bids: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Returns all completed/cancelled auctions where the user placed bids.
     *
     * @param username the logged-in user's username
     * @return list of completed auction rows
     */
    public List<DashboardAuctionRow> loadCompletedBids(String username) {
        try {
            return AuctionDAO.getInstance().findCompletedAuctionsByBidder(username);
        } catch (Exception e) {
            System.err.println("[MyBidsService] Error loading completed bids: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Returns the highest bid the user placed on a specific auction.
     *
     * @param auctionId the auction ID
     * @param username  the user's username
     * @return the user's highest bid amount, or 0
     */
    public float getUserHighestBid(int auctionId, String username) {
        try {
            return AuctionDAO.getInstance().getUserHighestBid(auctionId, username);
        } catch (Exception e) {
            System.err.println("[MyBidsService] Error loading user highest bid: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Returns the username of the current highest bidder for an auction.
     *
     * @param auctionId the auction ID
     * @return highest bidder username, or null
     */
    public String getHighestBidder(int auctionId) {
        try {
            return AuctionDAO.getInstance().getHighestBidderUsername(auctionId);
        } catch (Exception e) {
            System.err.println("[MyBidsService] Error loading highest bidder: " + e.getMessage());
            return null;
        }
    }
}
