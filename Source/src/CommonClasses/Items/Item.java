package CommonClasses.Items;

import CommonClasses.Entity;

import java.io.Serializable;
import java.util.Date;
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

    /** Current highest price for this item in auction session. */
    private float currentHighestPrice;

    /** Planned auction start time for this item. */
    private Date auctionStartTime;

    /** Planned auction end time for this item. */
    private Date auctionEndTime;

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
        this.currentHighestPrice = startingPrice;
    }

    public Item(float startingPrice, String name, String description,
                float currentHighestPrice, Date auctionStartTime, Date auctionEndTime) {
        super();
        this.startingPrice = startingPrice;
        this.name = name;
        this.description = description;
        this.currentHighestPrice = currentHighestPrice;
        this.auctionStartTime = auctionStartTime;
        this.auctionEndTime = auctionEndTime;
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

    public float getCurrentHighestPrice() {
        return currentHighestPrice;
    }

    public void setCurrentHighestPrice(float currentHighestPrice) {
        this.currentHighestPrice = currentHighestPrice;
    }

    public Date getAuctionStartTime() {
        return auctionStartTime;
    }

    public void setAuctionStartTime(Date auctionStartTime) {
        this.auctionStartTime = auctionStartTime;
    }

    public Date getAuctionEndTime() {
        return auctionEndTime;
    }

    public void setAuctionEndTime(Date auctionEndTime) {
        this.auctionEndTime = auctionEndTime;
    }

    // ========================== Object Overrides ==========================

    @Override
    public String toString() {
        return "Item{" +
                "startingPrice=" + startingPrice +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", currentHighestPrice=" + currentHighestPrice +
                ", auctionStartTime=" + auctionStartTime +
                ", auctionEndTime=" + auctionEndTime +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Item item = (Item) o;
        return Float.compare(item.startingPrice, startingPrice) == 0 &&
                Float.compare(item.currentHighestPrice, currentHighestPrice) == 0 &&
                Objects.equals(name, item.name) &&
                Objects.equals(description, item.description) &&
                Objects.equals(auctionStartTime, item.auctionStartTime) &&
                Objects.equals(auctionEndTime, item.auctionEndTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(startingPrice, name, description, currentHighestPrice, auctionStartTime, auctionEndTime);
    }
}
