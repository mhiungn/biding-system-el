package CommonClasses;

import java.io.Serializable;
import java.util.Date;
import java.util.Objects;

/**
 * Represents a single bid placed by a client on an auction.
 * <p>
 * A {@code Bid} records three pieces of information:
 * <ul>
 * <li><b>createdAt</b> — the timestamp when the bid was placed.</li>
 * <li><b>bid</b> — the monetary value of the bid (as a float).</li>
 * <li><b>bidderUsername</b> — the username of the client who placed this bid,
 * used to uniquely identify the bidder.</li>
 * </ul>
 * Bids are stored in a {@link java.util.LinkedList} inside each
 * {@link Auction},
 * ordered from highest (first) to lowest (last). This class implements
 * {@link Serializable} so it can be transmitted over the network inside
 * packet payloads.
 * </p>
 *
 * <h3>Usage example:</h3>
 * 
 * <pre>{@code
 * Bid bid = new Bid(new Date(), 250.0f, "john_doe");
 * float amount = bid.getBid(); // 250.0
 * String who = bid.getBidderUsername(); // "john_doe"
 * }</pre>
 *
 * @see Auction#placeBid(Bid, String)
 * @see Auction#findHighestBid()
 */
public class Bid implements Serializable {

    // ========================== Attributes ==========================

    /** The date/time at which this bid was created. */
    private Date createdAt;

    /** The monetary value of the bid. */
    private float bid;

    /**
     * The username of the client who placed this bid.
     * Used as a unique identifier since each authenticated user has a distinct
     * username.
     */
    private String bidderUsername;

    // ========================== Constructors ==========================

    /**
     * Constructs a new {@code Bid} with the specified timestamp, amount, and bidder
     * username.
     *
     * @param createdAt      the date/time the bid was placed (can be {@code null}
     *                       for a "no bid" sentinel)
     * @param bid            the monetary value of the bid
     * @param bidderUsername the username of the bidder (can be {@code null} for a
     *                       sentinel bid)
     */
    public Bid(Date createdAt, float bid, String bidderUsername) {
        this.createdAt = createdAt;
        this.bid = bid;
        this.bidderUsername = bidderUsername;
    }

    // ========================== Getters & Setters ==========================

    /**
     * Returns the timestamp when this bid was created.
     *
     * @return the creation date of this bid
     */
    public Date getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the creation timestamp of this bid.
     *
     * @param createdAt the new creation date
     */
    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Returns the monetary value of this bid.
     *
     * @return the bid amount as a float
     */
    public float getBid() {
        return bid;
    }

    /**
     * Sets the monetary value of this bid.
     *
     * @param bid the new bid amount
     */
    public void setBid(float bid) {
        this.bid = bid;
    }

    /**
     * Returns the username of the client who placed this bid.
     *
     * @return the bidder's username string, or {@code null} for a sentinel "empty"
     *         bid
     */
    public String getBidderUsername() {
        return bidderUsername;
    }

    /**
     * Sets the username of the bidder.
     *
     * @param bidderUsername the new bidder username
     */
    public void setBidderUsername(String bidderUsername) {
        this.bidderUsername = bidderUsername;
    }

    // ========================== Methods ==========================

    /**
     * Returns a human-readable string representation of this bid.
     *
     * @return formatted string with createdAt, bid amount, and bidder username
     */
    @Override
    public String toString() {
        return "Bid{" +
                "createdAt=" + createdAt +
                ", bid=" + bid +
                ", bidderUsername='" + bidderUsername + '\'' +
                '}';
    }

    /**
     * Compares this bid with another object for equality.
     * Two bids are equal if their creation date, bid amount, and bidder username
     * all match.
     *
     * @param o the object to compare against
     * @return {@code true} if the bids are equal, {@code false} otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Bid bid1 = (Bid) o;
        return Float.compare(bid1.bid, bid) == 0 &&
                Objects.equals(createdAt, bid1.createdAt) &&
                Objects.equals(bidderUsername, bid1.bidderUsername);
    }

    /**
     * Returns a hash code based on createdAt, bid value, and bidder username.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(createdAt, bid, bidderUsername);
    }
}
