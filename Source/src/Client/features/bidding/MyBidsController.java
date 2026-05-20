package Client.features.bidding;

import Client.core.ui.NavigationController;
import Client.features.auth.SessionManager;
import CommonClasses.User;
import Server.dao.DashboardAuctionRow;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Controller for the My Bids screen.
 * <p>
 * Loads the logged-in user's active and completed bids from
 * {@link MyBidsService} and dynamically populates the FXML containers.
 * Replaces all hardcoded placeholder items.
 * </p>
 */
public class MyBidsController extends NavigationController {

    // ========================== FXML Fields ==========================

    @FXML private VBox activeBidsList;
    @FXML private VBox completedBidsList;
    @FXML private Button btnFilterAll;
    @FXML private Button btnFilterWinning;
    @FXML private Button btnFilterOutbid;

    // ========================== Service & State ==========================

    private final MyBidsService service = new MyBidsService();
    private final NumberFormat currencyFormat = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy");

    private List<DashboardAuctionRow> allActiveBids;
    private String currentFilter = "ALL";

    // ========================== Initialization ==========================

    @FXML
    public void initialize() {
        User user = SessionManager.getCurrentUser();
        if (user == null) {
            showEmptyState(activeBidsList, "Please log in to see your bids");
            showEmptyState(completedBidsList, "Please log in to see your history");
            return;
        }

        loadActiveBids(user.getUsername());
        loadCompletedBids(user.getUsername());
        setupFilterButtons();
    }

    // ========================== Data Loading ==========================

    /**
     * Loads and displays active bids for the user.
     */
    private void loadActiveBids(String username) {
        allActiveBids = service.loadActiveBids(username);
        renderActiveBids(allActiveBids);
    }

    /**
     * Renders active bids into the activeBidsList container.
     */
    private void renderActiveBids(List<DashboardAuctionRow> bids) {
        activeBidsList.getChildren().clear();

        if (bids.isEmpty()) {
            showEmptyState(activeBidsList, "No active bids");
            return;
        }

        User user = SessionManager.getCurrentUser();
        String username = user != null ? user.getUsername() : "";

        for (DashboardAuctionRow row : bids) {
            HBox bidItem = createActiveBidItem(row, username);
            activeBidsList.getChildren().add(bidItem);
        }
    }

    /**
     * Creates a single active bid item matching the FXML structure.
     */
    private HBox createActiveBidItem(DashboardAuctionRow row, String username) {
        HBox item = new HBox();
        item.getStyleClass().add("bid-item");

        // Image placeholder
        Region image = new Region();
        image.getStyleClass().add("bid-item-image");

        // Info section
        VBox info = new VBox(5);
        info.getStyleClass().add("bid-item-info");
        HBox.setHgrow(info, Priority.ALWAYS);

        Label title = new Label(row.getItem() != null ? row.getItem().getName() : "Unknown Item");
        title.getStyleClass().add("bid-item-title");

        float userBid = service.getUserHighestBid(row.getAuctionId(), username);
        float currentPrice = row.getItem() != null ? row.getItem().getCurrentHighestPrice() : 0;

        HBox bidAmounts = new HBox(15);
        Label yourBid = new Label("Your Bid: " + formatCurrency(userBid));
        yourBid.getStyleClass().add("bid-item-your-bid");
        Label currentBid = new Label("Current: " + formatCurrency(currentPrice));
        currentBid.getStyleClass().add("bid-item-current");
        bidAmounts.getChildren().addAll(yourBid, currentBid);

        info.getChildren().addAll(title, bidAmounts);

        // Right section: time + status
        VBox right = new VBox(8);
        right.setAlignment(Pos.CENTER_RIGHT);
        right.getStyleClass().add("bid-item-right");

        // Time remaining
        String timeLeft = formatTimeRemaining(row);
        HBox timeBox = new HBox(5);
        timeBox.setAlignment(Pos.CENTER_RIGHT);
        Region timeIcon = new Region();
        timeIcon.getStyleClass().add("time-icon-small");
        Label timeLabel = new Label(timeLeft);
        timeLabel.getStyleClass().add("bid-item-time");
        timeBox.getChildren().addAll(timeIcon, timeLabel);

        // Status (winning/outbid)
        String highestBidder = service.getHighestBidder(row.getAuctionId());
        boolean isWinning = username.equals(highestBidder);
        String statusText = isWinning ? "WINNING" : "OUTBID";

        HBox statusBox = new HBox(5);
        statusBox.setAlignment(Pos.CENTER_RIGHT);
        Region statusIcon = new Region();
        statusIcon.getStyleClass().add(isWinning ? "status-icon-winning" : "status-icon-outbid");
        Label statusLabel = new Label(statusText);
        statusLabel.getStyleClass().add(isWinning ? "status-winning" : "status-outbid");
        statusBox.getChildren().addAll(statusIcon, statusLabel);

        right.getChildren().addAll(timeBox, statusBox);

        // Action button
        VBox actionBox = new VBox();
        actionBox.setAlignment(Pos.CENTER);
        actionBox.getStyleClass().add("bid-item-action");
        Button increaseBtn = new Button("INCREASE BID");
        increaseBtn.getStyleClass().add("increase-bid-button");
        actionBox.getChildren().add(increaseBtn);

        item.getChildren().addAll(image, info, right, actionBox);
        return item;
    }

    /**
     * Loads and displays completed bids for the user.
     */
    private void loadCompletedBids(String username) {
        List<DashboardAuctionRow> completed = service.loadCompletedBids(username);
        completedBidsList.getChildren().clear();

        if (completed.isEmpty()) {
            showEmptyState(completedBidsList, "No completed bids yet");
            return;
        }

        for (DashboardAuctionRow row : completed) {
            HBox tableRow = createCompletedBidRow(row, username);
            completedBidsList.getChildren().add(tableRow);
        }
    }

    /**
     * Creates a single completed bid table row matching the FXML structure.
     */
    private HBox createCompletedBidRow(DashboardAuctionRow row, String username) {
        HBox tableRow = new HBox(12);
        tableRow.setAlignment(Pos.CENTER_LEFT);
        tableRow.getStyleClass().add("table-row");

        // Thumbnail
        Region thumb = new Region();
        thumb.setMinWidth(42); thumb.setMaxWidth(42); thumb.setPrefWidth(42);
        thumb.getStyleClass().add("table-item-image");

        // Item name
        Label name = new Label(row.getItem() != null ? row.getItem().getName() : "Unknown");
        name.getStyleClass().add("table-item-name");
        name.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(name, Priority.ALWAYS);

        // User's bid
        float userBid = service.getUserHighestBid(row.getAuctionId(), username);
        Label yourBid = new Label(formatCurrency(userBid));
        yourBid.getStyleClass().add("table-value");
        yourBid.setMinWidth(100); yourBid.setPrefWidth(100);

        // Final price
        float finalPrice = row.getItem() != null ? row.getItem().getCurrentHighestPrice() : 0;
        Label finalPriceLabel = new Label(formatCurrency(finalPrice));
        finalPriceLabel.getStyleClass().add("table-value");
        finalPriceLabel.setMinWidth(110); finalPriceLabel.setPrefWidth(110);

        // Status (WON / LOST)
        String highestBidder = service.getHighestBidder(row.getAuctionId());
        boolean won = username.equals(highestBidder);
        Label status = new Label(won ? "WON" : "LOST");
        status.setAlignment(Pos.CENTER);
        status.getStyleClass().addAll("status-badge", won ? "status-won" : "status-lost");
        status.setMinWidth(90); status.setPrefWidth(90);

        // Date
        Label dateLabel = new Label(row.getEndTime() != null ? dateFormat.format(row.getEndTime()) : "—");
        dateLabel.getStyleClass().add("table-value");
        dateLabel.setMinWidth(110); dateLabel.setPrefWidth(110);

        tableRow.getChildren().addAll(thumb, name, yourBid, finalPriceLabel, status, dateLabel);
        return tableRow;
    }

    // ========================== Filters ==========================

    /**
     * Sets up filter button click handlers.
     */
    private void setupFilterButtons() {
        if (btnFilterAll != null) {
            btnFilterAll.setOnAction(e -> applyFilter("ALL"));
        }
        if (btnFilterWinning != null) {
            btnFilterWinning.setOnAction(e -> applyFilter("WINNING"));
        }
        if (btnFilterOutbid != null) {
            btnFilterOutbid.setOnAction(e -> applyFilter("OUTBID"));
        }
    }

    /**
     * Applies a filter to the active bids list.
     */
    private void applyFilter(String filter) {
        currentFilter = filter;
        updateFilterButtonStyles();

        if (allActiveBids == null) return;

        User user = SessionManager.getCurrentUser();
        String username = user != null ? user.getUsername() : "";

        if ("ALL".equals(filter)) {
            renderActiveBids(allActiveBids);
            return;
        }

        List<DashboardAuctionRow> filtered = allActiveBids.stream().filter(row -> {
            String highestBidder = service.getHighestBidder(row.getAuctionId());
            boolean isWinning = username.equals(highestBidder);
            return "WINNING".equals(filter) ? isWinning : !isWinning;
        }).collect(java.util.stream.Collectors.toList());

        renderActiveBids(filtered);
    }

    /**
     * Updates filter button CSS to show the active filter.
     */
    private void updateFilterButtonStyles() {
        if (btnFilterAll != null) {
            btnFilterAll.getStyleClass().removeAll("filter-active", "filter-btn");
            btnFilterAll.getStyleClass().add("ALL".equals(currentFilter) ? "filter-active" : "filter-btn");
        }
        if (btnFilterWinning != null) {
            btnFilterWinning.getStyleClass().removeAll("filter-active", "filter-btn");
            btnFilterWinning.getStyleClass().add("WINNING".equals(currentFilter) ? "filter-active" : "filter-btn");
        }
        if (btnFilterOutbid != null) {
            btnFilterOutbid.getStyleClass().removeAll("filter-active", "filter-btn");
            btnFilterOutbid.getStyleClass().add("OUTBID".equals(currentFilter) ? "filter-active" : "filter-btn");
        }
    }

    // ========================== Utility ==========================

    private String formatCurrency(float amount) {
        return currencyFormat.format((long) amount) + "VND";
    }

    private String formatTimeRemaining(DashboardAuctionRow row) {
        if (row.getEndTime() == null) return "—";

        long remaining = row.getEndTime().getTime() - System.currentTimeMillis();
        if (remaining <= 0) return "Ended";

        long hours = remaining / 3_600_000;
        long minutes = (remaining % 3_600_000) / 60_000;

        if (hours > 24) {
            long days = hours / 24;
            return days + "d " + (hours % 24) + "h";
        }
        return hours + "h " + minutes + "m";
    }

    private void showEmptyState(VBox container, String message) {
        container.getChildren().clear();
        Label empty = new Label(message);
        empty.getStyleClass().add("bid-time");
        container.getChildren().add(empty);
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
