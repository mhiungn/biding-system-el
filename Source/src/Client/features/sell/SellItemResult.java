package Client.features.sell;

public class SellItemResult {
    private final String itemId;
    private final int auctionId;

    public SellItemResult(String itemId, int auctionId) {
        this.itemId = itemId;
        this.auctionId = auctionId;
    }

    public String getItemId() {
        return itemId;
    }

    public int getAuctionId() {
        return auctionId;
    }
}
