package CommonClasses;

/**
 * Observer interface for receiving notifications about auction bid events.
 * <p>
 * Implements the Observer design pattern to decouple the auction domain
 * logic from the notification mechanism. The Server (or any other component)
 * can register as a {@code BidObserver} to react when bids are placed,
 * without the Auction needing to know about networking or UI.
 * </p>
 *
 * @see Auction#addObserver(BidObserver)
 * @see Auction#removeObserver(BidObserver)
 */
public interface BidObserver {

    /**
     * Called when a new valid bid is successfully placed on an auction.
     *
     * @param newBid the newly placed bid
     */
    void update(Bid newBid);
}
