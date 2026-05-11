package Client.features.dashboard;

import Client.core.ui.NavigationController;
import Client.features.auth.UserSession;
import CommonClasses.Items.Item;
import CommonClasses.User;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Optional;

public class DashboardController extends NavigationController {

    @FXML private GridPane auctionCardsGrid;
    @FXML private Button btnPrevPage;
    @FXML private Button btnNextPage;
    @FXML private Button btnPage1;
    @FXML private Button btnPage2;
    @FXML private Button btnPage3;
    @FXML private Label lblActiveAuctions;
    @FXML private Label lblEndingToday;
    @FXML private Label lblTotalBids;
    @FXML private Label lblCurrentUserQuickInfo;

    private List<Item> allItems;
    private int currentPage = 0;
    private static final int CARDS_PER_PAGE = 12;

    @FXML
    public void initialize() {
        final DashboardService dashboardService = new DashboardService();
        allItems = dashboardService.loadAllItems();
        User currentUser = UserSession.getCurrentUser();
        if (lblCurrentUserQuickInfo != null) {
            lblCurrentUserQuickInfo.setText(currentUser == null
                    ? "Guest"
                    : currentUser.getUsername() + " | " + currentUser.getEmail());
        }

        if (lblActiveAuctions != null) {
            lblActiveAuctions.setText("0");
        }
        if (lblEndingToday != null && lblTotalBids != null) {
            String totalItems = String.valueOf(allItems.size());
            lblEndingToday.setText(totalItems);
            lblTotalBids.setText(totalItems);
        }

//        btnPrevPage.setOnAction(e -> showPage(currentPage - 1));
//        btnNextPage.setOnAction(e -> showPage(currentPage + 1));
//        btnPage1.setOnAction(e -> showPage(0));
//        btnPage2.setOnAction(e -> showPage(1));
//        btnPage3.setOnAction(e -> showPage(2));

        showPage(0);
//        btnPrevPage.setVisible(false);
    }

    public void showPage(int pageNumber) {
        int maxPage = (int) Math.ceil((double) allItems.size() / CARDS_PER_PAGE) - 1;
        if (pageNumber < 0) {
            pageNumber = 0;
        }
        if (pageNumber > maxPage) {
            pageNumber = maxPage;
        }

        currentPage = pageNumber;

        int startIndex = currentPage * CARDS_PER_PAGE;
        int endIndex = Math.min(startIndex + CARDS_PER_PAGE, allItems.size());

        List<Item> pageItems = allItems.subList(startIndex, endIndex);
        loadAuctionCards(pageItems);
    }

    public void loadAuctionCards(List<Item> items) {
        if (auctionCardsGrid == null) {
            return;
        }

        auctionCardsGrid.getChildren().clear();

        int row = 0;
        int col = 0;

        for (Item item : items) {
            VBox card = createAuctionCard(item);
            auctionCardsGrid.add(card, col, row);

            col++;
            if (col == 4) {
                col = 0;
                row++;
            }
        }
    }

    private VBox createAuctionCard(Item item) {
        VBox card = new VBox();
        card.getStyleClass().add("auction-card");

        Region image = new Region();
        image.getStyleClass().add("card-image");

        VBox content = new VBox(5);
        content.getStyleClass().add("card-content");

        Label title = new Label(item.getName());
        title.getStyleClass().add("card-title");

        Label subtitle = new Label("Starting Price");
        subtitle.getStyleClass().add("card-subtitle");

        String formattedPrice = String.format("%,.0fVND", item.getStartingPrice()).replace(",", ".");
        Label price = new Label(formattedPrice);
        price.getStyleClass().add("card-price");

        HBox metaBox = new HBox(15);
        metaBox.setAlignment(Pos.CENTER_LEFT);

        HBox timeBox = new HBox(5);
        timeBox.setAlignment(Pos.CENTER_LEFT);
        Region timeIcon = new Region();
        timeIcon.getStyleClass().add("time-icon");
        int hours = (int) (item.getStartingPrice() % 12) + 1;
        Label timeLabel = new Label(hours + "h " + (hours * 5) + "m");
        timeLabel.getStyleClass().add("card-meta");
        timeBox.getChildren().addAll(timeIcon, timeLabel);

        HBox bidBox = new HBox(5);
        bidBox.setAlignment(Pos.CENTER_LEFT);
        Region bidIcon = new Region();
        bidIcon.getStyleClass().add("bid-icon");
        int randomBids = (int) (item.getStartingPrice() % 30);
        Label bidLabel = new Label(randomBids + " bids");
        bidLabel.getStyleClass().add("card-meta");
        bidBox.getChildren().addAll(bidIcon, bidLabel);

        metaBox.getChildren().addAll(timeBox, bidBox);

        Button bidButton = new Button("PLACE BID");
        bidButton.getStyleClass().add("bid-button");
        bidButton.setMaxWidth(Double.MAX_VALUE);
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

    @Override
    protected boolean onBeforeClose() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Are you sure you want to exit?",
                ButtonType.YES, ButtonType.NO);

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.YES;
    }
}
