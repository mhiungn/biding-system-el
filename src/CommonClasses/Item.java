//Donat Salihu
//Nikolaos Lintas
//Memli Restelica
//Philippos Kalatzis

package CommonClasses;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents an item that is being auctioned in the auction system.
 * <p>
 * Each {@code Item} holds the basic metadata about the product/service being sold:
 * its display name, a textual description, and the minimum starting price that
 * the seller is willing to accept. This class is embedded inside an {@link Auction}
 * and is transmitted over the network as part of various payload objects, hence it
 * implements {@link Serializable}.
 * </p>
 *
 * <h3>Usage example:</h3>
 * <pre>{@code
 *   Item laptop = new Item(500.0f, "Gaming Laptop", "High-end RTX laptop, barely used.");
 *   System.out.println(laptop.getName());           // "Gaming Laptop"
 *   System.out.println(laptop.getStartingPrice());   // 500.0
 * }</pre>
 *
 * @see Auction
 * @see Bid
 */
public class Item implements Serializable {

    // ========================== Attributes ==========================

    /** The minimum price at which bidding starts for this item. */
    private float startingPrice;

    /** The display name of the item (e.g. "Vintage Watch", "Gaming Laptop"). */
    private String name;

    /** A longer textual description of the item providing additional details. */
    private String description;

    // ========================== Constructors ==========================

    /**
     * Constructs a new {@code Item} with the given starting price, name, and description.
     *
     * @param startingPrice the minimum price at which bidding begins (must be positive)
     * @param name          the display name of the item
     * @param description   a detailed description of the item
     */
    public Item(float startingPrice, String name, String description) {
        this.startingPrice = startingPrice;
        this.name = name;
        this.description = description;
    }

    // ========================== Getters & Setters ==========================

    /**
     * Returns the starting price of this item.
     *
     * @return the minimum bid price as a float
     */
    public float getStartingPrice() {
        return startingPrice;
    }

    /**
     * Sets the starting price of this item.
     *
     * @param startingPrice the new starting price
     */
    public void setStartingPrice(float startingPrice) {
        this.startingPrice = startingPrice;
    }

    /**
     * Returns the display name of this item.
     *
     * @return the item's name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the display name of this item.
     *
     * @param name the new name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the description of this item.
     *
     * @return the item's description string
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description of this item.
     *
     * @param description the new description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    // ========================== Methods ==========================

    /**
     * Returns a human-readable string representation of this item,
     * including its starting price, name, and description.
     *
     * @return a formatted string describing this item
     */
    @Override
    public String toString() {
        return "Item{" +
                "startingPrice=" + startingPrice +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                '}';
    }

    /**
     * Compares this item to another object for equality.
     * Two items are considered equal if they have the same starting price,
     * name, and description.
     *
     * @param o the object to compare with
     * @return {@code true} if the objects are equal, {@code false} otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Item item = (Item) o;
        return Float.compare(item.startingPrice, startingPrice) == 0 &&
                Objects.equals(name, item.name) &&
                Objects.equals(description, item.description);
    }

    /**
     * Returns a hash code value for this item based on its starting price,
     * name, and description.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(startingPrice, name, description);
    }
}
