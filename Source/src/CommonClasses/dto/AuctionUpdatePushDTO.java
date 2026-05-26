package CommonClasses.dto;

import java.io.Serializable;

public class AuctionUpdatePushDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int auctionId;
    private final DashboardAuctionRow auction;
    private final String reason;

    public AuctionUpdatePushDTO(int auctionId, DashboardAuctionRow auction, String reason) {
        this.auctionId = auctionId;
        this.auction = auction;
        this.reason = reason;
    }

    public int getAuctionId() {
        return auctionId;
    }

    public DashboardAuctionRow getAuction() {
        return auction;
    }

    public String getReason() {
        return reason;
    }
}
