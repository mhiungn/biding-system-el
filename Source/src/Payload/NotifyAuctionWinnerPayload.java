package Payload;

import java.io.Serializable;

/**
 * Payload sent to notify the winning bidder of an auction.
 */
public class NotifyAuctionWinnerPayload implements Serializable {

    private int auctionId;
    private float winningBid;
    private String itemName;

    public NotifyAuctionWinnerPayload(int auctionId, float winningBid, String itemName) {
        this.auctionId = auctionId;
        this.winningBid = winningBid;
        this.itemName = itemName;
    }

    public int getAuctionId() { return auctionId; }
    public void setAuctionId(int auctionId) { this.auctionId = auctionId; }

    public float getWinningBid() { return winningBid; }
    public void setWinningBid(float winningBid) { this.winningBid = winningBid; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    @Override
    public String toString() {
        return "NotifyAuctionWinnerPayload{" +
                "auctionId=" + auctionId +
                ", winningBid=" + winningBid +
                ", itemName='" + itemName + '\'' +
                '}';
    }
}
