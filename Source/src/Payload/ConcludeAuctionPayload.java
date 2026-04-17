package Payload;

import java.io.Serializable;

/**
 * Payload sent when an auction concludes with a winner.
 */
public class ConcludeAuctionPayload implements Serializable {

    private int auctionId;
    private float winningBid;
    private String itemName;
    private String winnerUsername;

    public ConcludeAuctionPayload(int auctionId, float winningBid,
                                  String itemName, String winnerUsername) {
        this.auctionId = auctionId;
        this.winningBid = winningBid;
        this.itemName = itemName;
        this.winnerUsername = winnerUsername;
    }

    public int getAuctionId() { return auctionId; }
    public void setAuctionId(int auctionId) { this.auctionId = auctionId; }

    public float getWinningBid() { return winningBid; }
    public void setWinningBid(float winningBid) { this.winningBid = winningBid; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public String getWinnerUsername() { return winnerUsername; }
    public void setWinnerUsername(String winnerUsername) { this.winnerUsername = winnerUsername; }

    @Override
    public String toString() {
        return "ConcludeAuctionPayload{" +
                "auctionId=" + auctionId +
                ", winningBid=" + winningBid +
                ", itemName='" + itemName + '\'' +
                ", winnerUsername='" + winnerUsername + '\'' +
                '}';
    }
}
