package CommonClasses.Exceptions;

/**
 * Thrown when a non-owner attempts an owner-only action on an auction.
 */
public class AuctionNotOwnerException extends Exception {
    public AuctionNotOwnerException(String message) {
        super(message);
    }
}
