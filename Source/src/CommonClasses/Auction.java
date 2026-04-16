package CommonClasses;

import CommonClasses.Exceptions.*;
import CommonClasses.Items.*;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents a live auction in the auction system.
 * <p>
 * The {@code Auction} class is the central domain object. It encapsulates all
 * state and behavior for a single auction session:
 * <ul>
 *   <li>Ownership — which user (by username) created this auction.</li>
 *   <li>Item being sold — an {@link Item} with name, description, and starting price.</li>
 *   <li>Participant tracking — a {@code Set<String>} of registered usernames.</li>
 *   <li>Bid management — a {@code List<Bid>} kept in descending order (highest first).</li>
 *   <li>State management — lifecycle via {@link AuctionState}: OPEN → RUNNING → FINISHED / CANCELED.</li>
 *   <li>Observer pattern — {@link BidObserver} list for decoupled event notifications.</li>
 * </ul>
 *
 * <h3>Thread safety:</h3>
 * All state-mutating methods ({@link #placeBid}, {@link #addParticipant},
 * {@link #removeParticipant}, {@link #start}, {@link #conclude}, {@link #cancel})
 * are {@code synchronized} to prevent race conditions during concurrent bidding.
 *
 * @see Item
 * @see Bid
 * @see BidObserver
 * @see AuctionState
 * @see AuctionType
 */
public class Auction implements Serializable {

    // ========================== Static Fields ==========================

    /**
     * Thread-safe auto-incrementing counter used to generate unique auction IDs.
     */
    private static final AtomicInteger incrementer = new AtomicInteger();

    // ========================== Instance Fields ==========================

    /** Unique identifier for this auction. */
    private int id;

    /** Current lifecycle state of this auction. */
    private AuctionState state;

    /** Username of the user who created (owns) this auction. */
    private String ownerUsername;

    /** The item being auctioned. */
    private Item item;

    /** List of bids placed, ordered from highest (index 0) to lowest. */
    private List<Bid> bidList;

    /** Set of usernames of participants registered in this auction. */
    private Set<String> participants;

    /** List of observers to notify when auction events occur. */
    private transient List<BidObserver> observers;

    /** Timestamp when this auction was created. */
    private Date createdAt;

    /** The type of auction timing behavior. */
    private AuctionType type;

    // ========================== Constructors ==========================

    /**
     * Constructs a new Auction with the given owner, item, and type.
     * <p>
     * The auction starts in {@link AuctionState#OPEN} state with empty
     * participant and bid lists.
     *
     * @param ownerUsername the username of the auction creator
     * @param item         the item being auctioned
     * @param type         the auction timing type
     */
    public Auction(String ownerUsername, Item item, AuctionType type) {
        this.id = incrementer.incrementAndGet();
        this.ownerUsername = ownerUsername;
        this.item = item;
        this.type = type;
        this.state = AuctionState.OPEN;
        this.createdAt = new Date();
        this.bidList = new LinkedList<>();
        this.participants = new LinkedHashSet<>();
        this.observers = new ArrayList<>();
    }

    // ========================== Observer Management ==========================

    /**
     * Registers an observer to receive auction event notifications.
     *
     * @param observer the observer to add
     */
    public void addObserver(BidObserver observer) {
        if (observers == null) {
            observers = new ArrayList<>();
        }
        if (observer != null && !observers.contains(observer)) {
            observers.add(observer);
        }
    }

    /**
     * Removes a previously registered observer.
     *
     * @param observer the observer to remove
     */
    public void removeObserver(BidObserver observer) {
        if (observers != null) {
            observers.remove(observer);
        }
    }

    /**
     * Notifies all registered observers that a new bid has been placed.
     *
     * @param newBid the bid that was just placed
     */
    private void notifyObservers(Bid newBid) {
        if (observers == null) {
            return;
        }
        for (BidObserver observer : observers) {
            observer.update(newBid);
        }
    }

    // ========================== Business Logic ==========================

    /**
     * Transitions the auction from OPEN to RUNNING state.
     *
     * @throws IllegalStateException if the auction is not in OPEN state
     */
    public synchronized void start() {
        if (state != AuctionState.OPEN) {
            throw new IllegalStateException(
                    "Cannot start auction: current state is " + state);
        }
        this.state = AuctionState.RUNNING;
    }

    /**
     * Places a new bid on this auction.
     * <p>
     * Validates that:
     * <ol>
     *   <li>The auction is in OPEN or RUNNING state.</li>
     *   <li>The bidder is not the auction owner.</li>
     *   <li>The bidder is a registered participant.</li>
     *   <li>The bid amount exceeds the current highest bid (or starting price).</li>
     * </ol>
     * On the first valid bid, the auction automatically transitions from OPEN to RUNNING.
     * All registered {@link BidObserver}s are notified after a successful bid.
     *
     * @param bid      the bid to place
     * @param username the username of the bidder
     * @throws AuctionClientIsOwnerException if the bidder is the owner
     * @throws AuctionNotRegisteredException if the bidder is not registered
     * @throws AuctionLowBidException        if the bid is not high enough
     * @throws IllegalStateException         if the auction is not OPEN or RUNNING
     */
    public synchronized void placeBid(Bid bid, String username)
            throws AuctionClientIsOwnerException, AuctionNotRegisteredException,
                   AuctionLowBidException {

        // Verify auction is in a biddable state
        if (state != AuctionState.OPEN && state != AuctionState.RUNNING) {
            throw new IllegalStateException(
                    "Cannot place bid: auction state is " + state);
        }

        // Owner cannot bid on their own auction
        if (ownerUsername.equals(username)) {
            throw new AuctionClientIsOwnerException(
                    "The owner of the auction cannot bid on their own auction");
        }

        // Bidder must be a registered participant
        if (!participants.contains(username)) {
            throw new AuctionNotRegisteredException(
                    "User is not registered in this auction");
        }

        // Validate bid amount
        float currentHighest = bidList.isEmpty()
                ? item.getStartingPrice()
                : bidList.get(0).getBid();

        if (bid.getBid() <= currentHighest) {
            throw new AuctionLowBidException(
                    "Bid must be higher than current highest: " + currentHighest);
        }

        // Place the bid at the front of the list (highest first)
        bidList.add(0, bid);

        // Transition from OPEN → RUNNING on first bid
        if (state == AuctionState.OPEN) {
            state = AuctionState.RUNNING;
        }

        // Notify all observers about the new bid
        notifyObservers(bid);
    }

    /**
     * Registers a user as a participant in this auction.
     *
     * @param username the username to register
     * @throws AuctionClientIsOwnerException     if the user is the owner
     * @throws AuctionAlreadyRegisteredException if the user is already registered
     * @throws IllegalStateException             if the auction is not OPEN or RUNNING
     */
    public synchronized void addParticipant(String username)
            throws AuctionClientIsOwnerException, AuctionAlreadyRegisteredException {

        if (state != AuctionState.OPEN && state != AuctionState.RUNNING) {
            throw new IllegalStateException(
                    "Cannot join auction: auction state is " + state);
        }

        if (ownerUsername.equals(username)) {
            throw new AuctionClientIsOwnerException(
                    "The owner cannot register as a participant in their own auction");
        }

        if (participants.contains(username)) {
            throw new AuctionAlreadyRegisteredException(
                    "User is already registered in this auction");
        }

        participants.add(username);
    }

    /**
     * Unregisters a participant from this auction.
     * <p>
     * A participant who currently holds the highest bid cannot leave until outbid.
     *
     * @param username the username to unregister
     * @throws AuctionNotRegisteredException if the user is not registered
     * @throws AuctionHighBidException       if the user holds the highest bid
     */
    public synchronized void removeParticipant(String username)
            throws AuctionNotRegisteredException, AuctionHighBidException {

        if (!participants.contains(username)) {
            throw new AuctionNotRegisteredException(
                    "User is not registered in this auction");
        }

        // Prevent removal if this user holds the highest bid
        if (!bidList.isEmpty()
                && bidList.get(0).getBidderUsername().equals(username)) {
            throw new AuctionHighBidException(
                    "User holds the highest bid and cannot leave");
        }

        participants.remove(username);
    }

    /**
     * Concludes this auction, transitioning to FINISHED state.
     * <p>
     * Determines the winner as the user who placed the highest bid.
     * If no bids were placed, the auction finishes with no winner.
     *
     * @throws IllegalStateException if the auction is not OPEN or RUNNING
     */
    public synchronized void conclude() {
        if (state == AuctionState.FINISHED || state == AuctionState.CANCELED) {
            throw new IllegalStateException(
                    "Auction is already " + state);
        }

        this.state = AuctionState.FINISHED;
    }

    /**
     * Cancels this auction, transitioning to CANCELED state.
     * <p>
     * Only the owner can cancel, and only if no bids have been placed.
     *
     * @param username the username requesting the cancellation
     * @throws AuctionNotOwnerException if the requester is not the owner
     * @throws AuctionActiveException   if bids have already been placed
     */
    public synchronized void cancel(String username)
            throws AuctionNotOwnerException, AuctionActiveException {

        if (!ownerUsername.equals(username)) {
            throw new AuctionNotOwnerException(
                    "Only the auction owner can cancel the auction");
        }

        if (!bidList.isEmpty()) {
            throw new AuctionActiveException(
                    "Cannot cancel: bids have already been placed");
        }

        this.state = AuctionState.CANCELED;
    }

    // ========================== Query Methods ==========================

    /**
     * Returns the highest bid, or a sentinel bid with amount 0 if no bids exist.
     *
     * @return the highest {@link Bid}, or a sentinel {@code Bid(null, 0, null)}
     */
    public Bid findHighestBid() {
        if (!bidList.isEmpty()) {
            return bidList.get(0);
        }
        return new Bid(null, 0, null);
    }

    /**
     * Returns the highest bid amount, or 0 if no bids exist.
     *
     * @return the highest bid value as a float
     */
    public float findHighestBidAmount() {
        if (!bidList.isEmpty()) {
            return bidList.get(0).getBid();
        }
        return 0;
    }

    /**
     * Returns the username of the winning bidder, or {@code null} if no bids exist.
     *
     * @return the winner's username, or {@code null}
     */
    public String getWinnerUsername() {
        if (state == AuctionState.FINISHED && !bidList.isEmpty()) {
            return bidList.get(0).getBidderUsername();
        }
        return null;
    }

    /**
     * Returns whether the auction is currently accepting bids.
     *
     * @return {@code true} if state is OPEN or RUNNING
     */
    public boolean isActive() {
        return state == AuctionState.OPEN || state == AuctionState.RUNNING;
    }

    // ========================== Getters & Setters ==========================

    public static AtomicInteger getIncrementer() {
        return incrementer;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public AuctionState getState() {
        return state;
    }

    public void setState(AuctionState state) {
        this.state = state;
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public void setOwnerUsername(String ownerUsername) {
        this.ownerUsername = ownerUsername;
    }

    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
    }

    public List<Bid> getBidList() {
        return bidList;
    }

    public void setBidList(List<Bid> bidList) {
        this.bidList = bidList;
    }

    public Set<String> getParticipants() {
        return participants;
    }

    public void setParticipants(Set<String> participants) {
        this.participants = participants;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public AuctionType getType() {
        return type;
    }

    public void setType(AuctionType type) {
        this.type = type;
    }

    // ========================== Object Overrides ==========================

    @Override
    public String toString() {
        return "Auction{" +
                "id=" + id +
                ", state=" + state +
                ", ownerUsername='" + ownerUsername + '\'' +
                ", type=" + type +
                ", item=" + item +
                ", bidCount=" + bidList.size() +
                ", participantCount=" + participants.size() +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Auction auction = (Auction) o;
        return id == auction.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}