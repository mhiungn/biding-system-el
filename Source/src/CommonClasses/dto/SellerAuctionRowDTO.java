package CommonClasses.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class SellerAuctionRowDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int auctionId;
    private final String itemName;
    private final String status;
    private final Date startTime;
    private final Date endTime;
    private final float currentPrice;
    private final int bidCount;
    private final String highestBidderUsername;
    private final List<String> imagePaths;

    public SellerAuctionRowDTO(int auctionId, String itemName, String status, Date startTime, Date endTime,
                               float currentPrice, int bidCount, String highestBidderUsername,
                               List<String> imagePaths) {
        this.auctionId = auctionId;
        this.itemName = itemName;
        this.status = status;
        this.startTime = startTime;
        this.endTime = endTime;
        this.currentPrice = currentPrice;
        this.bidCount = bidCount;
        this.highestBidderUsername = highestBidderUsername;
        this.imagePaths = imagePaths == null ? new ArrayList<>() : new ArrayList<>(imagePaths);
    }

    public int getAuctionId() {
        return auctionId;
    }

    public String getItemName() {
        return itemName;
    }

    public String getStatus() {
        return status;
    }

    public Date getStartTime() {
        return startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public float getCurrentPrice() {
        return currentPrice;
    }

    public int getBidCount() {
        return bidCount;
    }

    public String getHighestBidderUsername() {
        return highestBidderUsername;
    }

    public List<String> getImagePaths() {
        return new ArrayList<>(imagePaths);
    }
}
