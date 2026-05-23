package Client.features.sell;

import CommonClasses.Items.Item;
import CommonClasses.Items.ItemFactory;
import Server.dao.AuctionDAO;
import Server.dao.AuctionSnapshot;
import Server.dao.ItemDAO;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;
import java.util.UUID;

public class SellItemService {
    private static final String TIME_FIXED = "Time_Fixed";
    private static final String TIME_WITH_RESET = "Time_With_Reset";

    private final ItemDAO itemDAO;
    private final AuctionDAO auctionDAO;

    public SellItemService() {
        this(null, null);
    }

    SellItemService(ItemDAO itemDAO, AuctionDAO auctionDAO) {
        this.itemDAO = itemDAO;
        this.auctionDAO = auctionDAO;
    }

    public SellItemResult listItem(SellItemRequest request) {
        validate(request);

        Item item = ItemFactory.createItem(
                request.getCategory(),
                request.getStartingPrice(),
                request.getItemName(),
                request.getDescription()
        );
        item.setCurrentHighestPrice(request.getStartingPrice());
        item.setItemCondition(request.getCondition());
        item.setLocation(request.getLocation());
        item.setAuctionStartTime(new Date());
        item.setAuctionEndTime(request.getAuctionEndTime());

        String itemId = null;
        try {
            ItemDAO items = itemDAO != null ? itemDAO : ItemDAO.getInstance();
            AuctionDAO auctions = auctionDAO != null ? auctionDAO : AuctionDAO.getInstance();

            itemId = items.saveItem(item, request.getSellerUsername());

            int auctionId = auctions.getNextAuctionId();
            AuctionSnapshot snapshot = new AuctionSnapshot(
                    auctionId,
                    request.getSellerUsername(),
                    new Date(),
                    request.getAuctionEndTime(),
                    request.isAutoExtend() ? TIME_WITH_RESET : TIME_FIXED,
                    "OPEN",
                    item,
                    new LinkedList<>(),
                    new ArrayList<>(),
                    false
            );
            snapshot.setMinimumBidIncrement(request.getMinimumBidIncrement());
            auctions.save(String.valueOf(auctionId), snapshot);

            saveImages(items, itemId, request);
            return new SellItemResult(itemId, auctionId);
        } catch (RuntimeException e) {
            if (itemId != null) {
                ItemDAO items = itemDAO != null ? itemDAO : ItemDAO.getInstance();
                items.delete(itemId);
            }
            throw e;
        }
    }

    private void validate(SellItemRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Listing request is required.");
        }
        requireText(request.getSellerUsername(), "Seller session is missing. Please log in again.");
        requireText(request.getItemName(), "Item name is required.");
        requireText(request.getCategory(), "Category is required.");
        requireText(request.getCondition(), "Condition is required.");
        requireText(request.getDescription(), "Description is required.");
        requireText(request.getLocation(), "Location is required.");
        if (request.getStartingPrice() <= 0) {
            throw new IllegalArgumentException("Starting price must be greater than 0.");
        }
        if (request.getMinimumBidIncrement() <= 0) {
            throw new IllegalArgumentException("Minimum bid increment must be greater than 0.");
        }
        if (request.getAuctionEndTime() == null || !request.getAuctionEndTime().after(new Date())) {
            throw new IllegalArgumentException("End date must be in the future.");
        }
        if (request.getMainImage() == null) {
            throw new IllegalArgumentException("Main picture is required.");
        }
        validateImageFile(request.getMainImage());
        for (File file : request.getGalleryImages()) {
            validateImageFile(file);
        }
    }

    private void requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }

    private void validateImageFile(File file) {
        if (file == null || !file.isFile()) {
            throw new IllegalArgumentException("Selected image file is not valid.");
        }
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".jpg") && !name.endsWith(".jpeg") && !name.endsWith(".png")) {
            throw new IllegalArgumentException("Only JPG and PNG images are supported.");
        }
        if (file.length() > 5L * 1024L * 1024L) {
            throw new IllegalArgumentException("Each picture must be 5 MB or smaller.");
        }
    }

    private void saveImages(ItemDAO items, String itemId, SellItemRequest request) {
        try {
            String mainPath = copyImage(itemId, request.getMainImage());
            items.saveItemImage(itemId, mainPath, true);

            for (File file : request.getGalleryImages()) {
                String imagePath = copyImage(itemId, file);
                items.saveItemImage(itemId, imagePath, false);
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not copy uploaded pictures.", e);
        }
    }

    private String copyImage(String itemId, File source) throws IOException {
        Path uploadDir = Path.of(System.getProperty("user.dir"), "uploads", "items", itemId);
        Files.createDirectories(uploadDir);

        String originalName = source.getName();
        String extension = "";
        int dot = originalName.lastIndexOf('.');
        if (dot >= 0) {
            extension = originalName.substring(dot).toLowerCase(Locale.ROOT);
        }

        Path target = uploadDir.resolve(UUID.randomUUID() + extension);
        Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
        return target.toAbsolutePath().toString();
    }
}
