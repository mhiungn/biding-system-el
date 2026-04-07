//Donat Salihu
//Nikolaos Lintas
//Memli Restelica
//Philippos Kalatzis

package CommonClasses;

import Packets.PacketMessage;
import Payload.*;
import Server.AuctionCountdownTask;
import Server.AuctionException.*;
import Server.AuctionTerminateTask;
import Server.Client;
import Server.Server;
import Server.ServerException.ServerNotClientOwnerException;

import java.io.IOException;
import java.io.Serializable;
import java.util.Date;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Timer;
import java.util.concurrent.atomic.AtomicInteger;

import static Packets.MessageType.*;

/**
 * Represents a live auction in the auction system.
 * <p>
 * The {@code Auction} class is the central domain object on the server side.
 * It encapsulates all state and behavior for a single auction session:
 * <ul>
 * <li>Ownership — which client (identified by IP) created this auction.</li>
 * <li>Item being sold — an {@link Item} with name, description, and starting
 * price.</li>
 * <li>Participant tracking — a list of {@link Client} objects registered to
 * bid.</li>
 * <li>Bid management — a list of {@link Bid} objects, kept in descending order
 * (highest bid first).</li>
 * <li>Timing — schedules automatic termination or countdown using
 * {@link Timer},
 * {@link AuctionTerminateTask}, and {@link AuctionCountdownTask}.</li>
 * </ul>
 *
 * <h3>Auction types:</h3>
 * <dl>
 * <dt>{@code "Time_Fixed"}</dt>
 * <dd>The auction ends at a fixed time, regardless of bidding activity.
 * Uses {@link AuctionTerminateTask} which simply calls {@link #conclude()} at
 * the scheduled termination date.</dd>
 * <dt>{@code "Time_With_Reset"}</dt>
 * <dd>The auction ends with a countdown phase. When the termination time is
 * reached,
 * an {@link AuctionCountdownTask} begins a "going once… going twice…"
 * countdown.
 * If a new bid arrives during this countdown, the timer
 * <strong>resets</strong>,
 * giving other participants a chance to outbid.</dd>
 * </dl>
 *
 * <h3>Thread safety note:</h3>
 * The {@link #incrementer} field uses {@link AtomicInteger} for thread-safe ID
 * generation.
 * However, the remaining mutable state (lists, timer, etc.) is <em>not</em>
 * internally
 * synchronized — the server is expected to coordinate access externally.
 *
 * @see Item
 * @see Bid
 * @see Client
 * @see Server
 * @see AuctionCountdownTask
 * @see AuctionTerminateTask
 */
public class Auction implements Serializable {

    // ========================== Attributes ==========================

    /**
     * Thread-safe auto-incrementing counter used to generate unique auction IDs.
     * Every new {@code Auction} instance receives the next available ID from this
     * counter.
     */
    private final static AtomicInteger incrementer = new AtomicInteger();

    /**
     * Unique identifier for this auction, assigned automatically at construction.
     */
    private int id;

    /**
     * IP address of the client who created (owns) this auction.
     * The owner cannot bid on their own auction but can cancel it if no bids exist.
     */
    private String clientOwner;

    /** Timestamp recording when this auction was created. */
    private Date createdAt;

    /**
     * The scheduled termination date/time for this auction.
     * For "Time_Fixed" auctions, the auction concludes exactly at this time.
     * For "Time_With_Reset" auctions, the countdown phase begins at this time.
     */
    private Date terminateAt;

    /**
     * Flag indicating whether the auction's countdown phase is currently active.
     * Only relevant for "Time_With_Reset" auctions. When {@code true}, new bids
     * will reset the countdown timer.
     */
    private boolean isInCountDown;

    /**
     * The auction type: either {@code "Time_Fixed"} or {@code "Time_With_Reset"}.
     * Determines how the auction terminates.
     */
    private String type;

    /** The {@link Timer} used to schedule the termination/countdown task. */
    private Timer timer;

    /** List of clients currently registered as participants in this auction. */
    private LinkedList<Client> clientList;

    /**
     * List of bids placed in this auction, ordered from highest (first) to lowest
     * (last).
     * The first element is always the current highest bid.
     */
    private LinkedList<Bid> bidList;

    /** The item being auctioned. */
    private Item item;

    /**
     * The countdown task for "Time_With_Reset" auctions.
     * Manages the "going once… going twice… sold!" countdown sequence.
     */
    private AuctionCountdownTask countdownTask;

    // ========================== Constructors ==========================

    /**
     * Constructs a new Auction with pre-populated client and bid lists.
     * <p>
     * This constructor is used when you already have existing participant and bid
     * lists (e.g., for testing or restoring state). The auction ID is
     * auto-generated
     * from the static {@link #incrementer}.
     * </p>
     * <p>
     * Depending on the {@code type} parameter:
     * <ul>
     * <li>{@code "Time_Fixed"} — schedules an {@link AuctionTerminateTask} to fire
     * at {@code terminateAt}.</li>
     * <li>{@code "Time_With_Reset"} — schedules an {@link AuctionCountdownTask} to
     * start at {@code terminateAt}.</li>
     * </ul>
     *
     * @param clientOwner the IP address of the client who owns this auction
     * @param terminateAt the date/time at which the auction should end or begin its
     *                    countdown
     * @param type        the auction type: {@code "Time_Fixed"} or
     *                    {@code "Time_With_Reset"}
     * @param clientList  the initial list of registered clients (participants)
     * @param bidList     the initial list of bids
     * @param item        the item being auctioned
     */
    public Auction(String clientOwner, Date terminateAt, String type,
            LinkedList<Client> clientList, LinkedList<Bid> bidList, Item item) {
        id = incrementer.incrementAndGet();
        this.clientOwner = clientOwner;
        this.createdAt = new Date();
        this.terminateAt = terminateAt;
        this.type = type;
        this.timer = new Timer();
        countdownTask = new AuctionCountdownTask(this);

        // Schedule the appropriate timer task based on auction type
        if (type.equals("Time_Fixed")) {
            // Fixed auctions terminate immediately at the scheduled time
            timer.schedule(new AuctionTerminateTask(this), terminateAt);
        } else {
            // Resettable auctions enter a countdown phase at the scheduled time
            timer.schedule(countdownTask, terminateAt);
        }

        this.clientList = clientList;
        this.bidList = bidList;
        this.item = item;
        isInCountDown = false;
    }

    /**
     * Constructs a new Auction with empty client and bid lists.
     * <p>
     * This is the primary constructor used when a client creates a new auction.
     * The participant list and bid list are initialized as empty
     * {@link LinkedList}s.
     * </p>
     *
     * @param clientOwner the IP address of the client who owns this auction
     * @param terminateAt the date/time at which the auction should end or begin its
     *                    countdown
     * @param type        the auction type: {@code "Time_Fixed"} or
     *                    {@code "Time_With_Reset"}
     * @param item        the item being auctioned
     */
    public Auction(String clientOwner, Date terminateAt, String type, Item item) {
        id = incrementer.incrementAndGet();
        this.clientOwner = clientOwner;
        createdAt = new Date();
        this.terminateAt = terminateAt;
        this.type = type;
        timer = new Timer();
        bidList = new LinkedList<>();
        clientList = new LinkedList<>();
        this.item = item;
        isInCountDown = false;
        countdownTask = new AuctionCountdownTask(this);

        // Schedule the appropriate timer task based on auction type
        if (type.equals("Time_Fixed")) {
            timer.schedule(new AuctionTerminateTask(this), terminateAt);
        } else {
            timer.schedule(countdownTask, terminateAt);
        }

    }

    // ========================== Getters & Setters ==========================

    /**
     * Returns the static auto-incrementing ID generator.
     *
     * @return the {@link AtomicInteger} used for generating auction IDs
     */
    public static AtomicInteger getIncrementer() {
        return incrementer;
    }

    /**
     * Returns the unique ID of this auction.
     *
     * @return the auction ID
     */
    public int getId() {
        return id;
    }

    /**
     * Sets the unique ID of this auction.
     *
     * @param id the new auction ID
     */
    public void setId(int id) {
        this.id = id;
    }

    /**
     * Returns the IP address of the client who created this auction.
     *
     * @return the owner's IP address string
     */
    public String getClientOwner() {
        return clientOwner;
    }

    /**
     * Sets the client owner IP address of this auction.
     *
     * @param clientOwner the new owner IP address
     */
    public void setClientOwner(String clientOwner) {
        this.clientOwner = clientOwner;
    }

    /**
     * Returns the date/time at which this auction was created.
     *
     * @return the creation timestamp
     */
    public Date getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the creation timestamp.
     *
     * @param createdAt the new creation date
     */
    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Returns the scheduled termination date/time of this auction.
     *
     * @return the termination timestamp
     */
    public Date getTerminateAt() {
        return terminateAt;
    }

    /**
     * Sets the termination date/time of this auction.
     *
     * @param terminateAt the new termination timestamp
     */
    public void setTerminateAt(Date terminateAt) {
        this.terminateAt = terminateAt;
    }

    /**
     * Returns whether the auction is currently in its countdown phase.
     * Only meaningful for "Time_With_Reset" auctions.
     *
     * @return {@code true} if the countdown phase is active
     */
    public boolean isInCountDown() {
        return isInCountDown;
    }

    /**
     * Sets the countdown phase flag.
     *
     * @param inCountDown {@code true} to mark the auction as being in its countdown
     *                    phase
     */
    public void setInCountDown(boolean inCountDown) {
        isInCountDown = inCountDown;
    }

    /**
     * Returns the auction type string.
     *
     * @return either {@code "Time_Fixed"} or {@code "Time_With_Reset"}
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the auction type string.
     *
     * @param type the new type
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Returns the timer used to schedule auction termination/countdown.
     *
     * @return the {@link Timer} instance
     */
    public Timer getTimer() {
        return timer;
    }

    /**
     * Sets the timer for this auction.
     *
     * @param timer the new timer
     */
    public void setTimer(Timer timer) {
        this.timer = timer;
    }

    /**
     * Returns the list of clients currently registered in this auction.
     *
     * @return the linked list of registered {@link Client} objects
     */
    public LinkedList<Client> getClientList() {
        return clientList;
    }

    /**
     * Sets the list of registered clients.
     *
     * @param clientList the new client list
     */
    public void setClientList(LinkedList<Client> clientList) {
        this.clientList = clientList;
    }

    /**
     * Returns the list of bids placed in this auction.
     * The list is ordered from highest (index 0) to lowest.
     *
     * @return the linked list of {@link Bid} objects
     */
    public LinkedList<Bid> getBidList() {
        return bidList;
    }

    /**
     * Sets the list of bids.
     *
     * @param bidList the new bid list
     */
    public void setBidList(LinkedList<Bid> bidList) {
        this.bidList = bidList;
    }

    /**
     * Returns the item being auctioned.
     *
     * @return the {@link Item} object
     */
    public Item getItem() {
        return item;
    }

    /**
     * Sets the item being auctioned.
     *
     * @param item the new item
     */
    public void setItem(Item item) {
        this.item = item;
    }

    /**
     * Returns the countdown task associated with this auction.
     * Used for "Time_With_Reset" auctions to manage the going-once/going-twice
     * sequence.
     *
     * @return the {@link AuctionCountdownTask}, or the default task if never reset
     */
    public AuctionCountdownTask getCountdownTask() {
        return countdownTask;
    }

    /**
     * Sets the countdown task for this auction.
     *
     * @param countdownTask the new countdown task
     */
    public void setCountdownTask(AuctionCountdownTask countdownTask) {
        this.countdownTask = countdownTask;
    }

    // ========================== Business Logic Methods ==========================

    /**
     * Registers a client as a participant in this auction.
     * <p>
     * The client is added to the auction's {@link #clientList} and the auction ID
     * is added to the client's list of registered auctions. This method enforces
     * two constraints:
     * <ol>
     * <li>The owner of the auction cannot register as a participant.</li>
     * <li>A client cannot register more than once in the same auction.</li>
     * </ol>
     *
     * @param client the client to register
     * @throws AuctionAlreadyRegisteredException if the client is already registered
     * @throws AuctionClientIsOwnerException     if the client is the auction owner
     */
    public void addClient(Client client) throws AuctionAlreadyRegisteredException, AuctionClientIsOwnerException {

        // Check if the client is the owner of the auction — owners cannot participate
        if (!clientOwner.equals(client.getUsername())) {
            // Check if the client is already registered in this auction
            if (!clientList.contains(client)) {

                // Add this auction to the client's personal list of registered auctions
                client.getRegisteredAuctions().addFirst(id);

                // Register the client as a participant of this auction
                clientList.add(client);

            } else {
                throw new AuctionAlreadyRegisteredException("Client is already registered");
            }
        } else {
            throw new AuctionClientIsOwnerException("The onwer of the auction can not register to their own auction");
        }
    }

    /**
     * Unregisters a client from this auction (voluntary departure).
     * <p>
     * The client is removed from the auction's participant list and the auction ID
     * is removed from the client's personal list of registered auctions.
     * <br>
     * <br>
     * <strong>Constraint:</strong> A client who holds the current highest bid
     * <em>cannot</em> unregister — they must be outbid first. This prevents the
     * scenario where a winning bidder leaves before the auction concludes.
     * </p>
     *
     * @param client the client to unregister
     * @throws AuctionHighBidException       if the client holds the highest bid
     * @throws AuctionNotRegisteredException if the client is not registered in this
     *                                       auction
     */
    public void removeClient(Client client)
            throws AuctionHighBidException, AuctionNotRegisteredException {

        // Verify the client is registered before attempting removal
        if (clientList.contains(client)) {

            // Prevent removal if this client holds the current highest bid
            if (!bidList.isEmpty() && bidList.getFirst().getBidderUsername()
                    .equals(client.getUsername())) {
                throw new AuctionHighBidException("User has the highest bid");
            }

            // Remove this auction from the client's personal list of registered auctions
            int auctionIndex = client.getRegisteredAuctions().indexOf(id);
            if (auctionIndex != -1) {
                client.getRegisteredAuctions().remove(auctionIndex);
            }

            // Remove the client from this auction's participant list
            clientList.remove(client);
        } else {
            throw new AuctionNotRegisteredException("The client is not registered in the auction");
        }
    }

    /**
     * Forcefully removes a client from this auction, even if they hold the highest
     * bid.
     * <p>
     * This method is called when a client's connection is lost unexpectedly.
     * If the forcefully removed client had the highest bid, that bid is discarded
     * and all remaining participants are notified of the updated highest bid
     * via a {@code HIGHEST_BID_OWNER_LOST} packet.
     * </p>
     *
     * @param client the client to forcefully remove
     * @throws AuctionNotRegisteredException if the client is not registered in this
     *                                       auction
     */
    public void forcefullyRemoveClient(Client client) throws AuctionNotRegisteredException {

        Server server = Server.getInstance();

        // Verify the client is actually registered
        if (clientList.contains(client)) {
            // Check if the client being removed has the highest bid
            if (!bidList.isEmpty() && bidList.getFirst().getBidderUsername().equals(client.getUsername())) {
                // Remove the highest bid since its owner is being removed
                bidList.remove(0);

                // Determine the new highest bid (or fall back to starting price)
                float highestBid = item.getStartingPrice();

                if (!bidList.isEmpty()) {
                    highestBid = bidList.getFirst().getBid();
                }

                // Notify all remaining participants about the updated highest bid
                AuctionUpdatePayload auctionUpdate = new AuctionUpdatePayload(id, createdAt, highestBid,
                        item.getName(), client.getUsername(),
                        item.getDescription());
                server.sendPackets(clientList, new PacketMessage(HIGHEST_BID_OWNER_LOST, auctionUpdate));
            }

            // Remove the client from the participant list
            clientList.remove(client);

        } else {
            throw new AuctionNotRegisteredException("Not registered in auction");
        }
    }

    /**
     * Places a new bid on this auction on behalf of a registered client.
     * <p>
     * This method validates the bid amount (must exceed the current highest bid or
     * the item's starting price if no bids exist), then:
     * <ol>
     * <li>Decrements the previous highest bidder's high-bid count.</li>
     * <li>Inserts the new bid at the front of the bid list.</li>
     * <li>Increments the new bidder's high-bid count.</li>
     * <li>If this is a "Time_With_Reset" auction and the countdown is active,
     * the countdown timer is <strong>reset</strong> — giving other participants
     * another chance to bid.</li>
     * <li>Sends an {@code AUCTION_UPDATE} packet to all participants and the
     * owner.</li>
     * </ol>
     *
     * @param bid    the new bid to place
     * @param client the client placing the bid
     * @throws AuctionNotRegisteredException if the client is not registered
     * @throws AuctionLowBidException        if the bid is not higher than the
     *                                       current highest
     * @throws AuctionClientIsOwnerException if the client is the auction owner
     */
    public void addBid(Bid bid, Client client)
            throws AuctionNotRegisteredException, AuctionLowBidException, AuctionClientIsOwnerException {

        // Auction owners are not allowed to bid on their own auction
        if (!clientOwner.equals(client.getUsername())) {
            // Verify the bidder is a registered participant
            if (clientList.contains(client)) {

                // Validate that the new bid exceeds the current highest bid (or starting price)
                if ((!bidList.isEmpty() && bidList.getFirst().getBid() < bid.getBid())
                        || (bidList.isEmpty() && bid.getBid() > item.getStartingPrice())) {

                    Server server = Server.getInstance();

                    // Record the previous highest bid value for the update notification
                    float highestBid = findHighestBid().getBid();

                    // Decrement the previous highest bidder's high-bid counter
                    if (!bidList.isEmpty()
                            && server.getClientHandlers().containsKey(bidList.getFirst().getBidderUsername())) {
                        server.getClientHandlers().get(bidList.getFirst().getBidderUsername()).getClient()
                                .lostHighBid();
                    }

                    // Insert the new bid at the front (making it the highest)
                    bidList.addFirst(bid);
                    // Increment the bidder's high-bid counter
                    client.madeHighBid();

                    // TIMER RESET LOGIC for "Time_With_Reset" auctions:
                    // If the auction is in its countdown phase, reset the countdown
                    // so other participants have time to respond to the new bid.
                    if (type.equals("Time_With_Reset") && isInCountDown) {

                        countdownTask.setCanConclude(false); // Prevent the old task from concluding
                        countdownTask.cancel(); // Cancel the old countdown task
                        timer.cancel(); // Cancel the existing timer
                        timer.purge(); // Remove cancelled tasks from queue
                        timer = null;
                        countdownTask = null;
                        timer = new Timer(); // Create a fresh timer
                        countdownTask = new AuctionCountdownTask(this); // Create a fresh countdown
                        timer.schedule(countdownTask, 0); // Start the new countdown immediately
                    }

                    // Build a notification payload with the new bid details
                    AuctionUpdatePayload auctionUpdate = new AuctionUpdatePayload(id, createdAt, highestBid,
                            item.getName(), client.getUsername(),
                            item.getDescription());
                    PacketMessage auctionUpdatePacket = new PacketMessage(AUCTION_UPDATE, auctionUpdate);

                    // Send update to all registered participants
                    server.sendPackets(clientList, auctionUpdatePacket);

                    // Also notify the auction owner (if still connected)
                    if (server.getClientHandlers().containsKey(clientOwner)) {
                        try {
                            server.getClientHandlers().get(clientOwner).sendPacket(auctionUpdatePacket);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                } else {
                    throw new AuctionLowBidException("Bid is lower than highest price");
                }
            } else {
                throw new AuctionNotRegisteredException("Not registered in auction");
            }
        } else {
            throw new AuctionClientIsOwnerException("The client is the owner of the auction");
        }
    }

    /**
     * Cancels this auction, removing it from the server.
     * <p>
     * Only the auction <strong>owner</strong> can cancel, and only if <strong>no
     * bids</strong>
     * have been placed yet. Once bids exist, the auction is considered active and
     * cannot
     * be cancelled (it must conclude naturally or via its timer).
     * </p>
     * <p>
     * On successful cancellation:
     * <ul>
     * <li>The timer is destroyed.</li>
     * <li>All registered participants receive an {@code AUCTION_CANCELLED}
     * packet.</li>
     * <li>The owner also receives the cancellation confirmation.</li>
     * <li>All clients are unregistered and the auction is removed from the
     * server.</li>
     * </ul>
     *
     * @param client the client requesting the cancellation (must be the owner)
     * @throws AuctionActiveException        if bids have already been placed
     * @throws ServerNotClientOwnerException if the requesting client is not the
     *                                       owner
     */
    public void cancel(Client client) throws AuctionActiveException, ServerNotClientOwnerException {

        // Only the owner is allowed to cancel
        if (clientOwner.equals(client.getUsername())) {
            // Can only cancel if no bids have been made
            if (bidList.isEmpty()) {
                Server server = Server.getInstance();

                // Destroy the scheduled timer to prevent future termination
                timer.cancel();
                timer = null;

                // Create and send cancellation notification to all participants
                PacketMessage auctionCanceledPacket = new PacketMessage(AUCTION_CANCELLED,
                        new ConfirmAuctionCancellationPayload(id));
                server.sendPackets(clientList, auctionCanceledPacket);

                // Also send the cancellation packet to the auction owner if still connected
                if (server.getClientHandlers().containsKey(clientOwner)) {
                    try {
                        server.getClientHandlers().get(clientOwner).sendPacket(auctionCanceledPacket);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                // Unregister all clients from this auction
                for (int i = 0; i < clientList.size(); i++) {
                    if (clientList.get(i).getRegisteredAuctions().contains(id)) {
                        clientList.remove(i);
                    }
                }

                // Remove this auction from the server's active auctions map
                server.getAuctions().remove(id);
            } else {
                throw new AuctionActiveException("Bid has already been made in this auction, action not permitted.");
            }
        } else {
            throw new ServerNotClientOwnerException("The client is not the owner of the auction");
        }
    }

    /**
     * Concludes this auction, determining a winner (if any) and cleaning up.
     * <p>
     * This method is called either by the timer tasks ({@link AuctionTerminateTask}
     * or {@link AuctionCountdownTask}) or during server shutdown. The conclusion
     * logic:
     * <ol>
     * <li>If bids exist, iterate through them (highest first) to find the first
     * bidder who is still connected — that bidder is the winner.</li>
     * <li>Send a {@code NOTIFY_AUCTION_WINNER} packet to the winner.</li>
     * <li>Send an {@code AUCTION_CONCLUDED} packet to all participants and the
     * owner.</li>
     * <li>If no bids exist or no winning bidder can be contacted, send a
     * {@code NOTIFY_NO_AUCTION_WINNER} packet to everyone.</li>
     * <li>Clean up: remove this auction from all clients' registered lists
     * and remove the auction from the server.</li>
     * </ol>
     */
    public void conclude() {

        Server server = Server.getInstance();
        PacketMessage noAuctionWinnerPacket;
        boolean foundWinner = false;

        // CASE 1: Bids have been placed — try to find a connected winner
        if (!bidList.isEmpty()) {
            // Iterate through bids from highest to lowest
            for (int i = 0; i < bidList.size(); i++) {
                // Check if the bidder is still connected to the server
                if (server.getClientHandlers().containsKey(bidList.get(i).getBidderUsername())) {

                    foundWinner = true;

                    // Create conclusion and winner notification payloads
                    ConcludeAuctionPayload concludePayload = new ConcludeAuctionPayload(id, bidList.get(i).getBid(),
                            item.getName(), bidList.get(i).getBidderUsername());
                    PacketMessage concludeAuctionPacket = new PacketMessage(AUCTION_CONCLUDED, concludePayload);
                    NotifyAuctionWinnerPayload notifyWinnerPayload = new NotifyAuctionWinnerPayload(id,
                            bidList.get(i).getBid(), item.getName());
                    PacketMessage notifyWinnerPacket = new PacketMessage(NOTIFY_AUCTION_WINNER, notifyWinnerPayload);

                    // Notify the auction owner (if still connected)
                    if (server.getClientHandlers().containsKey(clientOwner)) {
                        try {
                            server.getClientHandlers().get(clientOwner).sendPacket(concludeAuctionPacket);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    // Decrement the winner's high-bid counter (auction is over)
                    server.getClientHandlers().get(bidList.get(i).getBidderUsername()).getClient().lostHighBid();

                    // Send the winner their personal notification
                    try {
                        server.getClientHandlers().get(bidList.get(i).getBidderUsername())
                                .sendPacket(notifyWinnerPacket);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    // Broadcast the conclusion to all registered participants
                    server.sendPackets(clientList, concludeAuctionPacket);
                    break; // Only the first connected highest bidder wins
                }
            }
            if (!foundWinner) {
                // None of the bidders are still connected — no winner
                noAuctionWinnerPacket = new PacketMessage(NOTIFY_NO_AUCTION_WINNER,
                        new NotifyNoAuctionWinnerPayload(id, item.getName(), item.getStartingPrice()));

                // Notify the owner if still connected
                if (server.getClientHandlers().containsKey(clientOwner)) {
                    try {
                        server.getClientHandlers().get(clientOwner).sendPacket(noAuctionWinnerPacket);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                // Notify all participants
                server.sendPackets(clientList, noAuctionWinnerPacket);
            }
        } else {
            // CASE 2: No bids were ever placed — auction ends with no winner
            noAuctionWinnerPacket = new PacketMessage(NOTIFY_NO_AUCTION_WINNER,
                    new NotifyNoAuctionWinnerPayload(id, item.getName(), item.getStartingPrice()));

            // Notify the owner if still connected
            if (server.getClientHandlers().containsKey(clientOwner)) {
                try {
                    server.getClientHandlers().get(clientOwner).sendPacket(noAuctionWinnerPacket);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            // Notify all participants
            server.sendPackets(clientList, noAuctionWinnerPacket);
        }

        // CLEANUP: Remove this auction from every participant's registered list
        for (int i = 0; i < clientList.size(); i++) {
            int auctionIndex = clientList.get(i).getRegisteredAuctions().indexOf(id);
            if (auctionIndex != -1) {
                clientList.get(i).getRegisteredAuctions().remove(auctionIndex);
            }
        }

        // Remove this auction from the server's active auctions map
        server.getAuctions().remove(id);
    }

    /**
     * Returns the highest bid placed in this auction, or a sentinel "empty" bid
     * if no bids have been placed.
     * <p>
     * The sentinel bid has {@code null} values for createdAt and bidderIP, and
     * a bid amount of {@code 0}. This allows callers to safely read bid data
     * without null-checking the returned Bid itself.
     * </p>
     *
     * @return the highest {@link Bid}, or a sentinel {@code Bid(null, 0, null)}
     */
    public Bid findHighestBid() {
        if (!bidList.isEmpty()) {
            return this.getBidList().getFirst();
        } else {
            // Return a sentinel empty bid (no bids exist)
            return new Bid(null, 0, null);
        }
    }

    /**
     * Returns the highest bid value as a float, or {@code 0} if no bids exist.
     * <p>
     * Unlike {@link #findHighestBid()}, this method returns only the numeric amount
     * rather than the full {@link Bid} object. Returns 0 (not the starting price)
     * when no bids have been placed.
     * </p>
     *
     * @return the highest bid amount, or {@code 0.0f} if no bids exist
     */
    public float findHighestItemPrice() {

        float highestBid = 0;

        if (!bidList.isEmpty()) {
            highestBid = bidList.getFirst().getBid();
        }

        return highestBid;
    }

    // ========================== Object Override Methods ==========================

    /**
     * Returns a string representation of this auction for debugging purposes.
     *
     * @return a formatted string containing all auction fields
     */
    @Override
    public String toString() {
        return "ServerAuction{" +
                "id=" + id +
                ", clientOwner=" + clientOwner +
                ", createdAt=" + createdAt +
                ", terminateAt=" + terminateAt +
                ", isInCountDown=" + isInCountDown +
                ", type='" + type + '\'' +
                ", timer=" + timer +
                ", clientList=" + clientList +
                ", bidList=" + bidList +
                ", item=" + item +
                '}';
    }

    /**
     * Compares this auction with another object for equality.
     * Two auctions are equal if all their fields match (id, owner, dates, type,
     * etc.).
     *
     * @param o the object to compare against
     * @return {@code true} if equal, {@code false} otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Auction auction = (Auction) o;
        return isInCountDown == auction.isInCountDown &&
                Objects.equals(id, auction.id) &&
                Objects.equals(clientOwner, auction.clientOwner) &&
                Objects.equals(createdAt, auction.createdAt) &&
                Objects.equals(terminateAt, auction.terminateAt) &&
                Objects.equals(type, auction.type) &&
                Objects.equals(timer, auction.timer) &&
                Objects.equals(clientList, auction.clientList) &&
                Objects.equals(bidList, auction.bidList) &&
                Objects.equals(item, auction.item);
    }

    /**
     * Returns a hash code for this auction based on all its fields.
     *
     * @return the hash code value
     */
    @Override
    public int hashCode() {
        return Objects
                .hash(id, clientOwner, createdAt, terminateAt, isInCountDown, type, timer, clientList,
                        bidList, item);
    }
}