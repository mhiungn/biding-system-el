package CommonClasses.Items;
import java.util.Date;

/**
 * Represents a collectibles item in the auction system.
 *
 * @see Item
 * @see ItemFactory
 */
public class Collectibles extends Item {

    public Collectibles(float startingPrice, String name, String description) {
        super(startingPrice, name, description);
    }

    public Collectibles(float startingPrice, String name, String description,
                        float currentHighestPrice, Date auctionStartTime, Date auctionEndTime) {
        super(startingPrice, name, description, currentHighestPrice, auctionStartTime, auctionEndTime);
    }

    @Override
    public String getDisplayInfo() {
        return String.format("[Collectibles] %s - Starting: %.2f | %s",
                getName(), getStartingPrice(), getDescription());
    }

    @Override
    public String toString() {
        return "[Collectibles] " + super.toString();
    }
}
