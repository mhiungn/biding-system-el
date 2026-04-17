package CommonClasses.Items;

import CommonClasses.Entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents an item that is being auctioned in the auction system.
 * <p>
 * Each {@code Item} holds the basic metadata about the product/service being
 * sold: its display name, a textual description, and the minimum starting price
 * that the seller is willing to accept.
 * </p>
 *
 * @see Electronics
 * @see Art
 * @see Vehicle
 * @see ItemFactory
 */
public abstract class Item extends Entity implements Serializable {

    // ========================== Attributes ==========================

    /** The minimum price at which bidding starts for this item. */
    private float startingPrice;

    /** The display name of the item (e.g. "Vintage Watch", "Gaming Laptop"). */
    private String name;

    /** A longer textual description of the item providing additional details. */
    private String description;

    // ========================== Constructors ==========================

    /**
     * Constructs a new {@code Item} with the given starting price, name, and
     * description. Automatically generates a UUID via {@link Entity}.
     *
     * @param startingPrice the minimum price at which bidding begins (must be positive)
     * @param name          the display name of the item
     * @param description   a detailed description of the item
     */
    public Item(float startingPrice, String name, String description) {
        super();
        this.startingPrice = startingPrice;
        this.name = name;
        this.description = description;
    }

    // ========================== Entity Implementation ==========================

    /**
     * Returns a display string for this item, including type, name, price,
     * and description.
     *
     * @return a formatted display string
     */
    @Override
    public String getDisplayInfo() {
        return String.format("[Item] %s - Starting: %.2f | %s",
                name, startingPrice, description);
    }

    // ========================== Getters & Setters ==========================

    public float getStartingPrice() {
        return startingPrice;
    }

    public void setStartingPrice(float startingPrice) {
        this.startingPrice = startingPrice;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    // ========================== Object Overrides ==========================

    @Override
    public String toString() {
        return "Item{" +
                "startingPrice=" + startingPrice +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Item item = (Item) o;
        return Float.compare(item.startingPrice, startingPrice) == 0 &&
                Objects.equals(name, item.name) &&
                Objects.equals(description, item.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(startingPrice, name, description);
    }
}
