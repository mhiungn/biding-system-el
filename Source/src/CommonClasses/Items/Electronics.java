package CommonClasses.Items;
import java.util.Date;
/**
 * Represents an electronics item in the auction system.
 *
 * @see Item
 * @see ItemFactory
 */
public class Electronics extends Item {

    public Electronics(float startingPrice, String name, String description) {
        super(startingPrice, name, description);
    }

    public Electronics(float startingPrice, String name, String description,
                       float currentHighestPrice, Date auctionStartTime, Date auctionEndTime) {
        super(startingPrice, name, description, currentHighestPrice, auctionStartTime, auctionEndTime);
    }

    @Override
    public String getDisplayInfo() {
        return String.format("[Electronics] %s - Starting: %.2f | %s",
                getName(), getStartingPrice(), getDescription());
    }

    @Override
    public String toString() {
        return "[Electronics] " + super.toString();
    }
}
