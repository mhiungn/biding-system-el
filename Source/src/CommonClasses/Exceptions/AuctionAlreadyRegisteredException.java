package CommonClasses.Exceptions;

/**
 * Thrown when a client attempts to register for an auction they are already registered in.
 */
public class AuctionAlreadyRegisteredException extends Exception {
    public AuctionAlreadyRegisteredException(String message) {
        super(message);
    }
}
