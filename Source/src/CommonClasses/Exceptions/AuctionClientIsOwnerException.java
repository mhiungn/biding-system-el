package CommonClasses.Exceptions;

/**
 * Thrown when the auction owner attempts to bid on or register for their own auction.
 */
public class AuctionClientIsOwnerException extends Exception {
    public AuctionClientIsOwnerException(String message) {
        super(message);
    }
}
