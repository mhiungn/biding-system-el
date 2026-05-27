package Client.features.sell;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class SellItemRequest {
    private String sellerUsername;
    private String itemName;
    private String category;
    private String condition;
    private float startingPrice;
    private String description;
    private String location;
    private Date auctionEndTime;
    private float minimumBidIncrement;
    private boolean autoExtend;
    private File mainImage;
    private final List<File> galleryImages = new ArrayList<>();

    public String getSellerUsername() {
        return sellerUsername;
    }

    public void setSellerUsername(String sellerUsername) {
        this.sellerUsername = sellerUsername;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public float getStartingPrice() {
        return startingPrice;
    }

    public void setStartingPrice(float startingPrice) {
        this.startingPrice = startingPrice;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Date getAuctionEndTime() {
        return auctionEndTime;
    }

    public void setAuctionEndTime(Date auctionEndTime) {
        this.auctionEndTime = auctionEndTime;
    }

    public float getMinimumBidIncrement() {
        return minimumBidIncrement;
    }

    public void setMinimumBidIncrement(float minimumBidIncrement) {
        this.minimumBidIncrement = minimumBidIncrement;
    }

    public boolean isAutoExtend() {
        return autoExtend;
    }

    public void setAutoExtend(boolean autoExtend) {
        this.autoExtend = autoExtend;
    }

    public File getMainImage() {
        return mainImage;
    }

    public void setMainImage(File mainImage) {
        this.mainImage = mainImage;
    }

    public List<File> getGalleryImages() {
        return galleryImages;
    }
}
