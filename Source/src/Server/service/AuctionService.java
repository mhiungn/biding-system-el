package Server.service;

import CommonClasses.Auction;
import CommonClasses.AuctionManager;
import CommonClasses.AuctionType;
import CommonClasses.Exceptions.*;
import CommonClasses.Items.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service Layer for auction lifecycle management.
 * <p>
 * Encapsulates all business logic related to creating, retrieving, joining,
 * leaving, and cancelling auctions. Acts as the intermediary between the
 * network layer ({@code ClientHandler}) and the domain layer
 * ({@link AuctionManager}, {@link Auction}).
 * </p>
 *
 * <h3>Design rationale:</h3>
 * The {@code ClientHandler} should never directly access {@code AuctionManager}
 * or mutate {@code Auction} objects. All auction operations flow through this
 * service, which coordinates between the in-memory domain model and the DAO
 * persistence layer when needed.
 *
 * <h3>Singleton Pattern:</h3>
 * Thread-safe lazy initialization using double-checked locking.
 *
 * @see AuctionManager
 * @see Auction
 */
public class AuctionService {

    // ========================== Singleton ==========================

    private static volatile AuctionService instance;

    /**
     * Returns the singleton instance of {@code AuctionService}.
     *
     * @return the singleton instance
     */
    public static AuctionService getInstance() {
        if (instance == null) {
            synchronized (AuctionService.class) {
                if (instance == null) {
                    instance = new AuctionService();
                }
            }
        }
        return instance;
    }

    private final AuctionManager auctionManager;

    private AuctionService() {
        this.auctionManager = AuctionManager.getInstance();
    }

    // ========================== Auction Lifecycle ==========================

    /**
     * Creates a new auction and registers it with the {@link AuctionManager}.
     *
     * @param ownerUsername the username of the auction creator
     * @param item          the item to be auctioned
     * @param type          the auction timing type
     * @return the newly created {@link Auction}
     * @throws IllegalArgumentException if any parameter is null or empty
     */
    public Auction createAuction(String ownerUsername, Item item, AuctionType type) {
        if (ownerUsername == null || ownerUsername.trim().isEmpty()) {
            throw new IllegalArgumentException("Owner username cannot be null or empty.");
        }
        if (item == null) {
            throw new IllegalArgumentException("Item cannot be null.");
        }
        if (type == null) {
            throw new IllegalArgumentException("Auction type cannot be null.");
        }

        Auction auction = auctionManager.createAuction(ownerUsername, item, type);
        System.out.println("[AuctionService] Auction created: " + auction);
        return auction;
    }

    /**
     * Registers an existing {@link Auction} object directly with the
     * {@link AuctionManager}.
     * <p>
     * Used when the client sends a pre-built Auction object over the network.
     * The service sets the owner and registers the auction in the central
     * manager.
     * </p>
     *
     * @param auction       the auction object received from the client
     * @param ownerUsername  the authenticated username of the creator
     * @return the registered auction (same reference, with owner set)
     */
    public Auction registerAuction(Auction auction, String ownerUsername) {
        if (auction == null) {
            throw new IllegalArgumentException("Auction cannot be null.");
        }
        auction.setOwnerUsername(ownerUsername);
        auctionManager.addAuction(auction);
        System.out.println("[AuctionService] Auction registered: " + auction);
        return auction;
    }

    /**
     * Retrieves a single auction by its ID.
     *
     * @param auctionId the ID of the auction
     * @return the {@link Auction} object
     * @throws IllegalArgumentException if the auction is not found
     */
    public Auction getAuction(int auctionId) {
        Auction auction = auctionManager.getAuction(auctionId);
        if (auction == null) {
            throw new IllegalArgumentException("Auction not found: " + auctionId);
        }
        return auction;
    }

    /**
     * Returns all active auctions as a list.
     *
     * @return a list of all registered {@link Auction} objects
     */
    public List<Auction> getAllAuctions() {
        Map<Integer, Auction> auctionMap = auctionManager.getAllAuctions();
        return new ArrayList<>(auctionMap.values());
    }

    /**
     * Adds a participant to an auction.
     * <p>
     * Delegates validation (owner check, duplicate check, state check) to the
     * {@link Auction#addParticipant(String)} domain method.
     * </p>
     *
     * @param auctionId the ID of the auction to join
     * @param username  the username of the participant
     * @return the updated {@link Auction}
     * @throws AuctionClientIsOwnerException     if the user is the owner
     * @throws AuctionAlreadyRegisteredException if the user is already registered
     * @throws IllegalStateException             if the auction is not OPEN or RUNNING
     */
    public Auction joinAuction(int auctionId, String username)
            throws AuctionClientIsOwnerException, AuctionAlreadyRegisteredException {
        Auction auction = getAuction(auctionId);
        auction.addParticipant(username);
        System.out.println("[AuctionService] User '" + username + "' joined auction " + auctionId);
        return auction;
    }

    /**
     * Removes a participant from an auction.
     * <p>
     * Delegates validation (registration check, highest-bid check) to the
     * {@link Auction#removeParticipant(String)} domain method.
     * </p>
     *
     * @param auctionId the ID of the auction to leave
     * @param username  the username of the participant
     * @return the updated {@link Auction}
     * @throws AuctionNotRegisteredException if the user is not registered
     * @throws AuctionHighBidException       if the user holds the highest bid
     */
    public Auction leaveAuction(int auctionId, String username)
            throws AuctionNotRegisteredException, AuctionHighBidException {
        Auction auction = getAuction(auctionId);
        auction.removeParticipant(username);
        System.out.println("[AuctionService] User '" + username + "' left auction " + auctionId);
        return auction;
    }

    /**
     * Cancels an auction.
     * <p>
     * Delegates validation (owner check, active-bids check) to the
     * {@link Auction#cancel(String)} domain method.
     * </p>
     *
     * @param auctionId the ID of the auction to cancel
     * @param username  the username requesting the cancellation
     * @return the cancelled {@link Auction}
     * @throws AuctionNotOwnerException if the requester is not the owner
     * @throws AuctionActiveException   if bids have already been placed
     */
    public Auction cancelAuction(int auctionId, String username)
            throws AuctionNotOwnerException, AuctionActiveException {
        Auction auction = getAuction(auctionId);
        auction.cancel(username);
        System.out.println("[AuctionService] Auction " + auctionId + " cancelled by '" + username + "'");
        return auction;
    }

    /**
     * Concludes an auction, transitioning it to FINISHED state.
     *
     * @param auctionId the ID of the auction to conclude
     * @return the concluded {@link Auction}
     * @throws IllegalStateException if the auction is already finished or cancelled
     */
    public Auction concludeAuction(int auctionId) {
        Auction auction = getAuction(auctionId);
        auction.conclude();
        System.out.println("[AuctionService] Auction " + auctionId + " concluded. Winner: "
                + auction.getWinnerUsername());
        return auction;
    }

    /**
     * Starts an auction, transitioning it from OPEN to RUNNING.
     *
     * @param auctionId the ID of the auction to start
     * @return the started {@link Auction}
     * @throws IllegalStateException if the auction is not in OPEN state
     */
    public Auction startAuction(int auctionId) {
        Auction auction = getAuction(auctionId);
        auction.start();
        System.out.println("[AuctionService] Auction " + auctionId + " started.");
        return auction;
    }
}
