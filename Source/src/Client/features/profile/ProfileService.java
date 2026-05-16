package Client.features.profile;

import CommonClasses.User;
import Server.dao.AuctionDAO;
import Server.dao.UserDAO;

/**
 * Service layer for the User Profile screen.
 * <p>
 * Currently uses direct DAO access. When NetworkClient integration is complete,
 * this class will be refactored to send requests via the network socket instead.
 * </p>
 */
public class ProfileService {

    /**
     * Loads a user by username.
     *
     * @param username the username
     * @return User object or null
     */
    public User loadUser(String username) {
        try {
            return UserDAO.getInstance().findById(username);
        } catch (Exception e) {
            System.err.println("[ProfileService] Error loading user: " + e.getMessage());
            return null;
        }
    }

    /**
     * Returns the total number of bids placed by this user.
     *
     * @param username the username
     * @return bid count
     */
    public int getBidsPlaced(String username) {
        try {
            return AuctionDAO.getInstance().countBidsByUser(username);
        } catch (Exception e) {
            System.err.println("[ProfileService] Error counting bids: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Returns the number of auctions won by this user.
     *
     * @param username the username
     * @return auctions won count
     */
    public int getAuctionsWon(String username) {
        try {
            return AuctionDAO.getInstance().countWonByUser(username);
        } catch (Exception e) {
            System.err.println("[ProfileService] Error counting wins: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Returns the number of auctions created/sold by this user.
     *
     * @param username the username
     * @return auctions created count
     */
    public int getAuctionsCreated(String username) {
        try {
            return AuctionDAO.getInstance().countCreatedByUser(username);
        } catch (Exception e) {
            System.err.println("[ProfileService] Error counting created: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Returns the number of active auctions the user is participating in.
     *
     * @param username the username
     * @return active participation count
     */
    public int getActiveParticipations(String username) {
        try {
            return AuctionDAO.getInstance().countActiveParticipations(username);
        } catch (Exception e) {
            System.err.println("[ProfileService] Error counting active participations: " + e.getMessage());
            return 0;
        }
    }
}
