package Payload;

import java.io.Serializable;

/**
 * Payload confirming that an auction has been cancelled.
 */
public class ConfirmAuctionCancellationPayload implements Serializable {

    private int auctionId;

    public ConfirmAuctionCancellationPayload(int auctionId) {
        this.auctionId = auctionId;
    }

    public int getAuctionId() { return auctionId; }
    public void setAuctionId(int auctionId) { this.auctionId = auctionId; }

    @Override
    public String toString() {
        return "ConfirmAuctionCancellationPayload{auctionId=" + auctionId + '}';
    }
}
