package Server.service;

import CommonClasses.Auction;
import CommonClasses.Bid;
import CommonClasses.BidTransaction;
import CommonClasses.Exceptions.*;
import Server.dao.BidTransactionDAO;

import java.util.Date;
import java.util.List;

/**
 * Service Layer for bid placement and bid history.
 * <p>
 * Encapsulates all business logic related to placing bids on auctions
 * and recording bid transactions for audit purposes. Acts as the
 * intermediary between the network layer ({@code ClientHandler}) and
 * the domain layer ({@link Auction}) plus the data access layer
 * ({@link BidTransactionDAO}).
 * </p>
 *
 * <h3>Design rationale:</h3>
 * Bid placement involves both domain validation (handled by
 * {@link Auction#placeBid(Bid, String)}) and persistence of the transaction
 * log (handled by {@link BidTransactionDAO}). This service coordinates both
 * concerns, keeping the {@code ClientHandler} free of business logic.
 *
 * <h3>Singleton Pattern:</h3>
 * Thread-safe lazy initialization using double-checked locking.
 *
 * @see Auction
 * @see Bid
 * @see BidTransaction
 * @see BidTransactionDAO
 */
public class BidService {

    // ========================== Singleton ==========================

    private static volatile BidService instance;

    /**
     * Returns the singleton instance of {@code BidService}.
     *
     * @return the singleton instance
     */
    public static BidService getInstance() {
        if (instance == null) {
            synchronized (BidService.class) {
                if (instance == null) {
                    instance = new BidService();
                }
            }
        }
        return instance;
    }

    private final AuctionService auctionService;
    private final BidTransactionDAO bidTransactionDAO;

    private BidService() {
        this.auctionService = AuctionService.getInstance();
        this.bidTransactionDAO = BidTransactionDAO.getInstance();
    }

    // ========================== Business Logic ==========================

    /**
     * Places a bid on an auction.
     * <p>
     * This method:
     * <ol>
     *   <li>Retrieves the auction from {@link AuctionService}.</li>
     *   <li>Creates a {@link Bid} object with the current timestamp.</li>
     *   <li>Delegates validation and state mutation to
     *       {@link Auction#placeBid(Bid, String)}.</li>
     *   <li>Records a successful {@link BidTransaction} via
     *       {@link BidTransactionDAO}.</li>
     * </ol>
     * If validation fails, a failed transaction is recorded instead.
     * </p>
     *
     * @param auctionId the ID of the auction to bid on
     * @param bidAmount the monetary value of the bid
     * @param username  the username of the bidder
     * @return the {@link Bid} that was successfully placed
     * @throws AuctionClientIsOwnerException if the bidder is the owner
     * @throws AuctionNotRegisteredException if the bidder is not registered
     * @throws AuctionLowBidException        if the bid is not high enough
     * @throws IllegalStateException         if the auction is not in a biddable state
     * @throws IllegalArgumentException      if the auction is not found
     */
    public Bid placeBid(int auctionId, float bidAmount, String username)
            throws AuctionClientIsOwnerException, AuctionNotRegisteredException,
                   AuctionLowBidException {

        Auction auction = auctionService.getAuction(auctionId);
        Bid bid = new Bid(new Date(), bidAmount, username);

        try {
            // Delegate domain validation and state mutation to the Auction entity
            auction.placeBid(bid, username);

            // Record successful transaction
            BidTransaction transaction = new BidTransaction(auctionId, bid, username, true);
            bidTransactionDAO.save(transaction.getTransactionId(), transaction);

            System.out.println("[BidService] Bid placed successfully: " + bid
                    + " on auction " + auctionId);
            return bid;

        } catch (AuctionClientIsOwnerException | AuctionNotRegisteredException
                | AuctionLowBidException | IllegalStateException e) {

            // Record failed transaction for audit
            BidTransaction failedTransaction = new BidTransaction(auctionId, bid, username, false);
            bidTransactionDAO.save(failedTransaction.getTransactionId(), failedTransaction);

            System.out.println("[BidService] Bid rejected on auction " + auctionId
                    + ": " + e.getMessage());
            throw e;
        }
    }

    // ========================== Query Methods ==========================

    /**
     * Returns the previous highest bidder's username before a new bid is placed.
     * <p>
     * Useful for notifying the outbid user. Should be called <b>before</b>
     * {@link #placeBid(int, float, String)}.
     * </p>
     *
     * @param auctionId the auction ID to check
     * @return the username of the current highest bidder, or {@code null} if no
     *         bids exist
     */
    public String getCurrentHighestBidder(int auctionId) {
        Auction auction = auctionService.getAuction(auctionId);
        Bid highest = auction.findHighestBid();
        return highest.getBidderUsername();
    }

    /**
     * Returns the bid transaction history for a specific auction.
     *
     * @param auctionId the auction ID
     * @return list of {@link BidTransaction} objects, newest first
     */
    public List<BidTransaction> getTransactionsByAuction(int auctionId) {
        return bidTransactionDAO.findByAuctionId(auctionId);
    }

    /**
     * Returns the bid transaction history for a specific user.
     *
     * @param username the bidder's username
     * @return list of {@link BidTransaction} objects, newest first
     */
    public List<BidTransaction> getTransactionsByUser(String username) {
        return bidTransactionDAO.findByBidderUsername(username);
    }
}
