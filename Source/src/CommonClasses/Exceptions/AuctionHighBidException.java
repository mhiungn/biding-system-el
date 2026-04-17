package CommonClasses.Exceptions;

/**
 * Thrown when a client with the highest bid attempts to leave the auction.
 */
public class AuctionHighBidException extends Exception {
    public AuctionHighBidException(String message) {
        super(message);
    }
}
