package Client.controllers;

import CommonClasses.Items.Item;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

public class DashboardController extends Controller {

    @FXML private GridPane auctionCardsGrid;
    @FXML private Button btnPrevPage;
    @FXML private Button btnNextPage;
    @FXML private Button btnPage1;
    @FXML private Button btnPage2;
    @FXML private Button btnPage3;

    private List<Item> allItems;
    private int currentPage = 0;
    private static final int CARDS_PER_PAGE = 12;

    /**
     * List methods:
     * - initialize()
     * - showPage(int)
     * - createAuctionCard(Item)
     * - loadAuctionCards(List<Item>)
     */

    @FXML
    public void initialize() {
        // dummy database - tạo danh sách mẫu, ch implement database
        allItems = new ArrayList<>();
        String[] prefixes = {"Vintage", "Designer", "Collectible", "Antique", "Gaming", "Rare", "Modern"};
        String[] types = {"Camera", "Watch", "Computer", "Set", "Console", "Vase", "Bag", "Art", "Lamp"};
        
        for (int i = 0; i < 30; i++) {
            String name = prefixes[i % prefixes.length] + " " + types[i % types.length];
            float price = 100.0f + (i * 50.0f);
            
            // Create anonymous Item to avoid specific constructors -> tạo lớp con thừa hưởng từ lớp abstract Item mà không cần phải cụ thể type nào.
            Item item = new Item(price, name, "Description for " + name) { };
            allItems.add(item);
        }

        // 2. Setup pagination buttons
        btnPrevPage.setOnAction(e -> showPage(currentPage - 1));
        btnNextPage.setOnAction(e -> showPage(currentPage + 1));
        btnPage1.setOnAction(e -> showPage(0));
        btnPage2.setOnAction(e -> showPage(1));
        btnPage3.setOnAction(e -> showPage(2));

        // 3. Load initial page
        showPage(0);
        btnPrevPage.setVisible(false);

    }

    public void showPage(int pageNumber) {
        // Validate page Number bounds
        int maxPage = (int) Math.ceil((double) allItems.size() / CARDS_PER_PAGE) - 1;
        if (pageNumber < 0) pageNumber = 0;
        if (pageNumber > maxPage) pageNumber = maxPage;
        
        currentPage = pageNumber;

        int startIndex = currentPage * CARDS_PER_PAGE;
        int endIndex = Math.min(startIndex + CARDS_PER_PAGE, allItems.size());
        
        List<Item> pageItems = allItems.subList(startIndex, endIndex);
        loadAuctionCards(pageItems);

        // Emphasize current page button by styling (optional)
    }

    public void loadAuctionCards(List<Item> items) {
        // Clear existing cards
        auctionCardsGrid.getChildren().clear();

        int row = 0;
        int col = 0;

        for (Item item : items) {
            VBox card = createAuctionCard(item);
            auctionCardsGrid.add(card, col, row);

            col++;
            if (col == 4) { // 4 columns max
                col = 0;
                row++;
            }
        }
    }

    private VBox createAuctionCard(Item item) {
        VBox card = new VBox();
        card.getStyleClass().add("auction-card");
        
        // Image
        Region image = new Region();
        image.getStyleClass().add("card-image");
        
        // Content container
        VBox content = new VBox(5);
        content.getStyleClass().add("card-content");
        
        Label title = new Label(item.getName());
        title.getStyleClass().add("card-title");
        
        Label subtitle = new Label("Starting Price");
        subtitle.getStyleClass().add("card-subtitle");
        
        // Format price to match UI VND format
        String formattedPrice = String.format("%,.0fVND", item.getStartingPrice()).replace(",", ".");
        Label price = new Label(formattedPrice);
        price.getStyleClass().add("card-price");
        
        // Time and bids row matches previous mockups
        HBox metaBox = new HBox(15);
        metaBox.setAlignment(Pos.CENTER_LEFT);
        
        // Fake time
        HBox timeBox = new HBox(5);
        timeBox.setAlignment(Pos.CENTER_LEFT);
        Region timeIcon = new Region();
        timeIcon.getStyleClass().add("time-icon");
        // Random fake time based on price for uniqueness
        int hours = (int)(item.getStartingPrice() % 12) + 1;
        Label timeLabel = new Label(hours + "h " + (hours * 5) + "m");
        timeLabel.getStyleClass().add("card-meta");
        timeBox.getChildren().addAll(timeIcon, timeLabel);
        
        // Fake bids
        HBox bidBox = new HBox(5);
        bidBox.setAlignment(Pos.CENTER_LEFT);
        Region bidIcon = new Region();
        bidIcon.getStyleClass().add("bid-icon");
        // Random fake bids based on price
        int randomBids = (int)(item.getStartingPrice() % 30);
        Label bidLabel = new Label(randomBids + " bids");
        bidLabel.getStyleClass().add("card-meta");
        bidBox.getChildren().addAll(bidIcon, bidLabel);
        
        metaBox.getChildren().addAll(timeBox, bidBox);
        
        // Bid button
        Button bidButton = new Button("PLACE BID");
        bidButton.getStyleClass().add("bid-button");
        bidButton.setMaxWidth(Double.MAX_VALUE);
        // Delegate switching scene to parent controller
        bidButton.setOnAction(e -> {
            try {
                switchToBiddingDetails(e);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
        
        content.getChildren().addAll(title, subtitle, price, metaBox, bidButton);
        card.getChildren().addAll(image, content);
        
        return card;
    }
}
