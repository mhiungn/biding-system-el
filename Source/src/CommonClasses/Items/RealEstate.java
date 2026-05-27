package CommonClasses.Items;
import java.util.Date;

/**
 * Represents a real estate item in the auction system.
 *
 * @see Item
 * @see ItemFactory
 */
public class RealEstate extends Item {

    public RealEstate(float startingPrice, String name, String description) {
        super(startingPrice, name, description);
    }

    public RealEstate(float startingPrice, String name, String description,
                      float currentHighestPrice, Date auctionStartTime, Date auctionEndTime) {
        super(startingPrice, name, description, currentHighestPrice, auctionStartTime, auctionEndTime);
    }

    @Override
    public String getDisplayInfo() {
        return String.format("[Real Estate] %s - Starting: %.2f | %s",
                getName(), getStartingPrice(), getDescription());
    }

    @Override
    public String toString() {
        return "[Real Estate] " + super.toString();
    }
}
