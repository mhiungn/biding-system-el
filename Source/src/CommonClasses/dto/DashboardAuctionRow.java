package CommonClasses.dto;

import CommonClasses.Items.Item;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class DashboardAuctionRow implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int auctionId;
    private final String status;
    private final Date startTime;
    private final Date endTime;
    private final Item item;
    private final int bidCount;
    private final float minimumBidIncrement;
    private final List<String> imagePaths;
    private final String ownerUsername;
    private final String ownerProfileImageUrl;

    public DashboardAuctionRow(int auctionId, String status, Date startTime, Date endTime, Item item, int bidCount) {
        this(auctionId, status, startTime, endTime, item, bidCount, 1f);
    }

    public DashboardAuctionRow(int auctionId, String status, Date startTime, Date endTime, Item item, int bidCount,
                               float minimumBidIncrement) {
        this(auctionId, status, startTime, endTime, item, bidCount, minimumBidIncrement, new ArrayList<>());
    }

    public DashboardAuctionRow(int auctionId, String status, Date startTime, Date endTime, Item item, int bidCount,
                               float minimumBidIncrement, List<String> imagePaths) {
        this(auctionId, status, startTime, endTime, item, bidCount, minimumBidIncrement, imagePaths, null, null);
    }

    public DashboardAuctionRow(int auctionId, String status, Date startTime, Date endTime, Item item, int bidCount,
                               float minimumBidIncrement, List<String> imagePaths, String ownerUsername,
                               String ownerProfileImageUrl) {
        this.auctionId = auctionId;
        this.status = status;
        this.startTime = startTime;
        this.endTime = endTime;
        this.item = item;
        this.bidCount = bidCount;
        this.minimumBidIncrement = minimumBidIncrement > 0 ? minimumBidIncrement : 1f;
        this.imagePaths = imagePaths == null ? new ArrayList<>() : new ArrayList<>(imagePaths);
        this.ownerUsername = ownerUsername;
        this.ownerProfileImageUrl = ownerProfileImageUrl;
    }

    public int getAuctionId() {
        return auctionId;
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

    public Item getItem() {
        return item;
    }

    public int getBidCount() {
        return bidCount;
    }

    public float getMinimumBidIncrement() {
        return minimumBidIncrement;
    }

    public List<String> getImagePaths() {
        return new ArrayList<>(imagePaths);
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public String getOwnerProfileImageUrl() {
        return ownerProfileImageUrl;
    }
}
