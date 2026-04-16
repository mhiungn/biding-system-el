package Payload;

import java.io.Serializable;

/**
 * Payload sent when an auction ends with no winner.
 */
public class NotifyNoAuctionWinnerPayload implements Serializable {

    private int auctionId;
    private String itemName;
    private float startingPrice;

    public NotifyNoAuctionWinnerPayload(int auctionId, String itemName, float startingPrice) {
        this.auctionId = auctionId;
        this.itemName = itemName;
        this.startingPrice = startingPrice;
    }

    public int getAuctionId() { return auctionId; }
    public void setAuctionId(int auctionId) { this.auctionId = auctionId; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public float getStartingPrice() { return startingPrice; }
    public void setStartingPrice(float startingPrice) { this.startingPrice = startingPrice; }

    @Override
    public String toString() {
        return "NotifyNoAuctionWinnerPayload{" +
                "auctionId=" + auctionId +
                ", itemName='" + itemName + '\'' +
                ", startingPrice=" + startingPrice +
                '}';
    }
}
