package CommonClasses;

import java.io.Serializable;
import java.util.Date;
import java.util.Objects;

/**
 * Records a complete bid transaction in the auction system.
 * <p>
 * A {@code BidTransaction} captures all context around a bid event:
 * the auction it belongs to, the bid itself, the bidder, the timestamp,
 * and whether the transaction was successful.
 * </p>
 * <p>
 * This class serves as an immutable audit log entry for bid history
 * tracking and visualization.
 * </p>
 *
 * @see Bid
 * @see Auction
 */
public class BidTransaction implements Serializable {

    /** Unique transaction identifier. */
    private String transactionId;

    /** The ID of the auction this transaction belongs to. */
    private int auctionId;

    /** The bid that was placed. */
    private Bid bid;

    /** The username of the bidder. */
    private String bidderUsername;

    /** The timestamp when this transaction was recorded. */
    private Date timestamp;

    /** Whether the bid was accepted (true) or rejected (false). */
    private boolean successful;

    /**
     * Constructs a new BidTransaction.
     *
     * @param auctionId      the ID of the auction
     * @param bid            the bid that was placed
     * @param bidderUsername  the username of the bidder
     * @param successful     whether the bid was accepted
     */
    public BidTransaction(int auctionId, Bid bid, String bidderUsername, boolean successful) {
        this.transactionId = java.util.UUID.randomUUID().toString();
        this.auctionId = auctionId;
        this.bid = bid;
        this.bidderUsername = bidderUsername;
        this.timestamp = new Date();
        this.successful = successful;
    }

    // ========================== Getters ==========================

    public String getTransactionId() {
        return transactionId;
    }

    public int getAuctionId() {
        return auctionId;
    }

    public Bid getBid() {
        return bid;
    }

    public String getBidderUsername() {
        return bidderUsername;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public boolean isSuccessful() {
        return successful;
    }

    // ========================== Object Overrides ==========================

    @Override
    public String toString() {
        return "BidTransaction{" +
                "transactionId='" + transactionId + '\'' +
                ", auctionId=" + auctionId +
                ", bid=" + bid.getBid() +
                ", bidder='" + bidderUsername + '\'' +
                ", successful=" + successful +
                ", timestamp=" + timestamp +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BidTransaction that = (BidTransaction) o;
        return Objects.equals(transactionId, that.transactionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transactionId);
    }
}
