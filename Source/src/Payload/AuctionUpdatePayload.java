package Payload;

import java.io.Serializable;
import java.util.Date;

/**
 * Payload sent when an auction is updated (e.g., new bid placed or highest bidder lost).
 */
public class AuctionUpdatePayload implements Serializable {

    private int auctionId;
    private Date createdAt;
    private float highestBid;
    private String itemName;
    private String bidderUsername;
    private String itemDescription;

    public AuctionUpdatePayload(int auctionId, Date createdAt, float highestBid,
                                String itemName, String bidderUsername, String itemDescription) {
        this.auctionId = auctionId;
        this.createdAt = createdAt;
        this.highestBid = highestBid;
        this.itemName = itemName;
        this.bidderUsername = bidderUsername;
        this.itemDescription = itemDescription;
    }

    public int getAuctionId() { return auctionId; }
    public void setAuctionId(int auctionId) { this.auctionId = auctionId; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public float getHighestBid() { return highestBid; }
    public void setHighestBid(float highestBid) { this.highestBid = highestBid; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public String getBidderUsername() { return bidderUsername; }
    public void setBidderUsername(String bidderUsername) { this.bidderUsername = bidderUsername; }

    public String getItemDescription() { return itemDescription; }
    public void setItemDescription(String itemDescription) { this.itemDescription = itemDescription; }

    @Override
    public String toString() {
        return "AuctionUpdatePayload{" +
                "auctionId=" + auctionId +
                ", highestBid=" + highestBid +
                ", itemName='" + itemName + '\'' +
                ", bidderUsername='" + bidderUsername + '\'' +
                '}';
    }
}
