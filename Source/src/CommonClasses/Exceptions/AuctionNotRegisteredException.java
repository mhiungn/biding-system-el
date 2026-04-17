package CommonClasses.Exceptions;

/**
 * Thrown when an action is attempted on a client who is not registered in the auction.
 */
public class AuctionNotRegisteredException extends Exception {
    public AuctionNotRegisteredException(String message) {
        super(message);
    }
}
