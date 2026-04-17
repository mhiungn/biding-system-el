package CommonClasses.Items;

/**
 * Represents an art item in the auction system.
 *
 * @see Item
 * @see ItemFactory
 */
public class Art extends Item {

    public Art(float startingPrice, String name, String description) {
        super(startingPrice, name, description);
    }

    @Override
    public String getDisplayInfo() {
        return String.format("[Art] %s - Starting: %.2f | %s",
                getName(), getStartingPrice(), getDescription());
    }

    @Override
    public String toString() {
        return "[Art] " + super.toString();
    }
}
