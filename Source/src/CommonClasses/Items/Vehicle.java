package CommonClasses.Items;
import java.util.Date;

/**
 * Represents a vehicle item in the auction system.
 *
 * @see Item
 * @see ItemFactory
 */
public class Vehicle extends Item {

    public Vehicle(float startingPrice, String name, String description) {
        super(startingPrice, name, description);
    }

    public Vehicle(float startingPrice, String name, String description,
                   float currentHighestPrice, Date auctionStartTime, Date auctionEndTime) {
        super(startingPrice, name, description, currentHighestPrice, auctionStartTime, auctionEndTime);
    }

    @Override
    public String getDisplayInfo() {
        return String.format("[Vehicle] %s - Starting: %.2f | %s",
                getName(), getStartingPrice(), getDescription());
    }

    @Override
    public String toString() {
        return "[Vehicle] " + super.toString();
    }
}
