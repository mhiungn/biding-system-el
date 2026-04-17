package CommonClasses.Exceptions;

/**
 * Thrown when a bid is placed that is not higher than the current highest bid.
 */
public class AuctionLowBidException extends Exception {
    public AuctionLowBidException(String message) {
        super(message);
    }
}
