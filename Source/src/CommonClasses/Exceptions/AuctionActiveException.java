package CommonClasses.Exceptions;

/**
 * Thrown when an attempt is made to cancel an auction that has active bids.
 */
public class AuctionActiveException extends Exception {
    public AuctionActiveException(String message) {
        super(message);
    }
}
