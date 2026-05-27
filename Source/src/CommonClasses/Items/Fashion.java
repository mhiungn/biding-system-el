package CommonClasses.Items;
import java.util.Date;

/**
 * Represents a fashion item in the auction system.
 *
 * @see Item
 * @see ItemFactory
 */
public class Fashion extends Item {

    public Fashion(float startingPrice, String name, String description) {
        super(startingPrice, name, description);
    }

    public Fashion(float startingPrice, String name, String description,
                   float currentHighestPrice, Date auctionStartTime, Date auctionEndTime) {
        super(startingPrice, name, description, currentHighestPrice, auctionStartTime, auctionEndTime);
    }

    @Override
    public String getDisplayInfo() {
        return String.format("[Fashion] %s - Starting: %.2f | %s",
                getName(), getStartingPrice(), getDescription());
    }

    @Override
    public String toString() {
        return "[Fashion] " + super.toString();
    }
}
