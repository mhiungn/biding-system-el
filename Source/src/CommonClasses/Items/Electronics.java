package CommonClasses.Items
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
