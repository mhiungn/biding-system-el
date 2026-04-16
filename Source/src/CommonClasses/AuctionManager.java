package CommonClasses;

import CommonClasses.Items.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Singleton manager for the auction system.
 * <p>
 * Manages the central registry of all auctions and users. Provides methods
 * to create, retrieve, and remove auctions. Uses the Singleton pattern to
 * ensure a single point of coordination across the application.
 * </p>
 *
 * <h3>Thread safety:</h3>
 * Key methods are {@code synchronized} to support concurrent access
 * from multiple client handler threads.
 *
 * @see Auction
 * @see User
 */
public class AuctionManager {

    /** The single instance of AuctionManager. */
    private static AuctionManager instance;

    /** Map of all auctions keyed by auction ID. */
    private Map<Integer, Auction> auctions;

    /** List of all registered users. */
    private List<User> users;

    /**
     * Private constructor to enforce Singleton pattern.
     */
    private AuctionManager() {
        auctions = new HashMap<>();
        users = new ArrayList<>();
    }

    /**
     * Returns the singleton instance of AuctionManager.
     * Uses lazy initialization with synchronized access.
     *
     * @return the AuctionManager instance
     */
    public static synchronized AuctionManager getInstance() {
        if (instance == null) {
            instance = new AuctionManager();
        }
        return instance;
    }

    // ========================== Auction Management ==========================

    /**
     * Creates and registers a new auction.
     *
     * @param ownerUsername the username of the auction creator
     * @param item         the item to be auctioned
     * @param type         the auction timing type
     * @return the newly created {@link Auction}
     */
    public synchronized Auction createAuction(String ownerUsername, Item item, AuctionType type) {
        Auction auction = new Auction(ownerUsername, item, type);
        auctions.put(auction.getId(), auction);
        return auction;
    }

    /**
     * Retrieves an auction by its ID.
     *
     * @param auctionId the auction ID to look up
     * @return the {@link Auction}, or {@code null} if not found
     */
    public synchronized Auction getAuction(int auctionId) {
        return auctions.get(auctionId);
    }

    /**
     * Removes an auction from the registry.
     *
     * @param auctionId the ID of the auction to remove
     * @return the removed {@link Auction}, or {@code null} if not found
     */
    public synchronized Auction removeAuction(int auctionId) {
        return auctions.remove(auctionId);
    }

    /**
     * Returns all registered auctions.
     *
     * @return an unmodifiable view of the auctions map
     */
    public Map<Integer, Auction> getAllAuctions() {
        return auctions;
    }

    // ========================== User Management ==========================

    /**
     * Registers a user in the system.
     *
     * @param user the user to register
     */
    public synchronized void addUser(User user) {
        users.add(user);
    }

    /**
     * Returns all registered users.
     *
     * @return the list of users
     */
    public List<User> getUsers() {
        return users;
    }
}