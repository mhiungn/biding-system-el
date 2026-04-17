package CommonClasses.Items;

/**
 * Factory class for creating {@link Item} instances based on a type string.
 * <p>
 * Delegates to {@link ItemFactory#createItem(String, float, String, String)}.
 * </p>
 *
 * @see Item
 * @see ItemFactory
 */
public class TypeItem {

    /**
     * Creates an {@link Item} subclass instance based on the given type string.
     *
     * @param type  the item category (case-insensitive): "ELECTRONICS", "ART", or "VEHICLE"
     * @param price the starting price of the item
     * @param name  the display name of the item
     * @param desc  the description of the item
     * @return a new {@link Item} of the appropriate subclass
     * @throws IllegalArgumentException if the type is not recognized
     */
    public static Item createItem(String type, float price, String name, String desc) {
        return ItemFactory.createItem(type, price, name, desc);
    }
}
