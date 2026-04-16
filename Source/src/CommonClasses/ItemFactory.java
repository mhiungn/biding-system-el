package CommonClasses;

/**
 * Factory Method for creating {@link Item} instances based on a type string.
 * <p>
 * Supported types: {@code "ELECTRONICS"}, {@code "ART"}, {@code "VEHICLE"}.
 * Type matching is case-insensitive.
 *
 * <h3>Usage example:</h3>
 * <pre>{@code
 * Item laptop = ItemFactory.createItem("ELECTRONICS", 500.0f, "Gaming Laptop", "RTX 4090");
 * Item painting = ItemFactory.createItem("ART", 1000.0f, "Mona Lisa", "Replica");
 * }</pre>
 *
 * @see Item
 * @see Electronics
 * @see Art
 * @see Vehicle
 */
public class ItemFactory {

    /**
     * Creates a concrete {@link Item} subclass based on the given type string.
     *
     * @param type  the item category (case-insensitive): "ELECTRONICS", "ART", or "VEHICLE"
     * @param price the starting price of the item
     * @param name  the display name of the item
     * @param desc  the description of the item
     * @return a new {@link Item} instance of the appropriate subclass
     * @throws IllegalArgumentException if the type is not recognized
     */
    public static Item createItem(String type, float price, String name, String desc) {
        switch (type.toUpperCase()) {
            case "ELECTRONICS":
                return new Electronics(price, name, desc);
            case "ART":
                return new Art(price, name, desc);
            case "VEHICLE":
                return new Vehicle(price, name, desc);
            default:
                throw new IllegalArgumentException("Unknown item type: " + type);
        }
    }
}
