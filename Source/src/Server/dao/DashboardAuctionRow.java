package Server.dao;

import CommonClasses.Items.Item;

import java.util.Date;

public class DashboardAuctionRow {
    private final int auctionId;
    private final String status;
    private final Date startTime;
    private final Date endTime;
    private final Item item;
    private final int bidCount;
    private final float minimumBidIncrement;

    public DashboardAuctionRow(int auctionId, String status, Date startTime, Date endTime, Item item, int bidCount) {
        this(auctionId, status, startTime, endTime, item, bidCount, 1f);
    }

    public DashboardAuctionRow(int auctionId, String status, Date startTime, Date endTime, Item item, int bidCount,
                               float minimumBidIncrement) {
        this.auctionId = auctionId;
        this.status = status;
        this.startTime = startTime;
        this.endTime = endTime;
        this.item = item;
        this.bidCount = bidCount;
        this.minimumBidIncrement = minimumBidIncrement > 0 ? minimumBidIncrement : 1f;
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
}
