package CommonClasses.Items;

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
