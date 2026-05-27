package Client.features.bidding;

import Client.core.ui.NavigationController;
import Client.components.AppHeader;
import Client.components.LoadingOverlay;
import Client.features.auth.SessionManager;
import CommonClasses.dto.AuctionUpdatePushDTO;
import CommonClasses.dto.DashboardAuctionRow;
import CommonClasses.dto.NotificationPushDTO;
import CommonClasses.dto.SellerAuctionRowDTO;
import CommonClasses.User;
import CommonClasses.dto.WalletUpdatePushDTO;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.concurrent.Task;
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

import java.io.IOException;
import java.nio.file.Path;
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
    @FXML private VBox sellingItemsList;
    @FXML private Button btnFilterAll;
    @FXML private Button btnFilterWinning;
    @FXML private Button btnFilterOutbid;
    @FXML private Label lblActiveBidsHeading;
    @FXML private Label lblCompletedHeading;
    @FXML private Label lblSellingHeading;
    @FXML private AppHeader appHeader;

    // ========================== Service & State ==========================

    private final MyBidsService service = new MyBidsService();
    private final NumberFormat currencyFormat = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy");
    private final LoadingOverlay loadingOverlay = new LoadingOverlay();

    private List<DashboardAuctionRow> allActiveBids;
    private String currentFilter = "ALL";

    // ========================== Initialization ==========================

    @FXML
    public void initialize() {
        appHeader.configure(this);

        User user = SessionManager.getCurrentUser();
        if (user == null) {
            showEmptyState(activeBidsList, "Please log in to see your bids");
            showEmptyState(completedBidsList, "Please log in to see your history");
            showEmptyState(sellingItemsList, "Please log in to see your selling items");
            return;
        }

        registerForPushUpdates();
        Platform.runLater(() -> loadMyBidsData(user.getUsername()));
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

    private void loadMyBidsData(String username) {
        Task<MyBidsLoad> task = new Task<>() {
            @Override
            protected MyBidsLoad call() {
                return new MyBidsLoad(
                        service.loadActiveBids(username),
                        service.loadCompletedBids(username),
                        service.loadSellingItems(username));
            }
        };
        task.setOnSucceeded(event -> {
            loadingOverlay.hide();
            MyBidsLoad data = task.getValue();
            allActiveBids = data.active;
            renderActiveBids(data.active);
            renderCompletedBids(data.completed, username);
            renderSellingItems(data.selling);
        });
        task.setOnFailed(event -> {
            loadingOverlay.hide();
            showError("Could not load your bids.");
        });

        loadingOverlay.show(activeBidsList, "Loading bids...");
        Thread thread = new Thread(task, "my-bids-load");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Renders active bids into the activeBidsList container.
     */
    private void renderActiveBids(List<DashboardAuctionRow> bids) {
        activeBidsList.getChildren().clear();
        if (lblActiveBidsHeading != null) {
            lblActiveBidsHeading.setText("ACTIVE BIDS (" + bids.size() + ")");
        }

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
        applyImageBackground(image, firstImagePath(row));

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
        increaseBtn.setOnAction(event -> openAuctionDetail(event, row.getAuctionId()));
        actionBox.getChildren().add(increaseBtn);

        item.getChildren().addAll(image, info, right, actionBox);
        return item;
    }

    /**
     * Loads and displays completed bids for the user.
     */
    private void loadCompletedBids(String username) {
        List<DashboardAuctionRow> completed = service.loadCompletedBids(username);
        renderCompletedBids(completed, username);
    }

    private void renderCompletedBids(List<DashboardAuctionRow> completed, String username) {
        completedBidsList.getChildren().clear();
        if (lblCompletedHeading != null) {
            lblCompletedHeading.setText("COMPLETED BIDS (" + completed.size() + ")");
        }

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
        applyImageBackground(thumb, firstImagePath(row));

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
        Label dateLabel = new Label(row.getEndTime() != null ? dateFormat.format(row.getEndTime()) : "-");
        dateLabel.getStyleClass().add("table-value");
        dateLabel.setMinWidth(110); dateLabel.setPrefWidth(110);

        tableRow.getChildren().addAll(thumb, name, yourBid, finalPriceLabel, status, dateLabel);
        return tableRow;
    }

    /**
     * Loads and displays auctions created by the user.
     */
    private void loadSellingItems(String username) {
        List<SellerAuctionRowDTO> sellerItems = service.loadSellingItems(username);
        renderSellingItems(sellerItems);
    }

    private void renderSellingItems(List<SellerAuctionRowDTO> sellerItems) {
        sellingItemsList.getChildren().clear();
        if (lblSellingHeading != null) {
            lblSellingHeading.setText("SELLING / SOLD ITEMS (" + sellerItems.size() + ")");
        }

        if (sellerItems.isEmpty()) {
            showEmptyState(sellingItemsList, "No selling items yet");
            return;
        }

        for (SellerAuctionRowDTO row : sellerItems) {
            sellingItemsList.getChildren().add(createSellerAuctionRow(row));
        }
    }

    /**
     * Creates a seller-owned auction row for active and sold items.
     */
    private HBox createSellerAuctionRow(SellerAuctionRowDTO row) {
        HBox tableRow = new HBox(12);
        tableRow.setAlignment(Pos.CENTER_LEFT);
        tableRow.getStyleClass().add("table-row");

        Region thumb = new Region();
        thumb.setMinWidth(42); thumb.setMaxWidth(42); thumb.setPrefWidth(42);
        thumb.getStyleClass().add("table-item-image");
        applyImageBackground(thumb, firstImagePath(row));

        Label name = new Label(row.getItemName() != null ? row.getItemName() : "Unknown");
        name.getStyleClass().add("table-item-name");
        name.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(name, Priority.ALWAYS);

        Label price = new Label(formatCurrency(row.getCurrentPrice()));
        price.getStyleClass().add("table-value");
        price.setMinWidth(110); price.setPrefWidth(110);

        Label bids = new Label(String.valueOf(row.getBidCount()));
        bids.getStyleClass().add("table-value");
        bids.setMinWidth(70); bids.setPrefWidth(70);

        Label winner = new Label(row.getHighestBidderUsername() == null ? "-" : row.getHighestBidderUsername());
        winner.getStyleClass().add("table-value");
        winner.setMinWidth(110); winner.setPrefWidth(110);

        Label status = new Label(formatSellerStatus(row));
        status.setAlignment(Pos.CENTER);
        status.getStyleClass().addAll("status-badge", isSellerAuctionActive(row) ? "status-active" : "status-ended");
        status.setMinWidth(90); status.setPrefWidth(90);

        Label dateLabel = new Label(row.getEndTime() != null ? dateFormat.format(row.getEndTime()) : "-");
        dateLabel.getStyleClass().add("table-value");
        dateLabel.setMinWidth(110); dateLabel.setPrefWidth(110);

        Button viewButton = new Button("VIEW");
        viewButton.getStyleClass().add("table-action-button");
        viewButton.setOnAction(event -> openAuctionDetail(event, row.getAuctionId()));

        tableRow.getChildren().addAll(thumb, name, price, bids, winner, status, dateLabel, viewButton);
        tableRow.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                openAuctionDetail(new ActionEvent(tableRow, tableRow), row.getAuctionId());
            }
        });
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

    private void openAuctionDetail(ActionEvent event, int auctionId) {
        try {
            navigationService.openBiddingDetail(event, auctionId);
        } catch (IOException e) {
            showError("Could not open auction detail.");
        }
    }

    private String formatSellerStatus(SellerAuctionRowDTO row) {
        if (isSellerAuctionActive(row)) {
            return "ACTIVE";
        }
        if ("CANCELED".equalsIgnoreCase(row.getStatus())) {
            return "CANCELED";
        }
        return row.getBidCount() > 0 ? "SOLD" : "ENDED";
    }

    private boolean isSellerAuctionActive(SellerAuctionRowDTO row) {
        String status = row.getStatus();
        boolean openStatus = "OPEN".equalsIgnoreCase(status) || "RUNNING".equalsIgnoreCase(status);
        return openStatus && row.getEndTime() != null && row.getEndTime().getTime() > System.currentTimeMillis();
    }

    private String firstImagePath(DashboardAuctionRow rowData) {
        List<String> imagePaths = rowData.getImagePaths();
        return imagePaths.isEmpty() ? null : imagePaths.get(0);
    }

    private String firstImagePath(SellerAuctionRowDTO rowData) {
        List<String> imagePaths = rowData.getImagePaths();
        return imagePaths.isEmpty() ? null : imagePaths.get(0);
    }

    private void applyImageBackground(Region region, String path) {
        if (region == null || path == null || path.isBlank()) {
            return;
        }
        region.setStyle("-fx-background-image: url(\"" + toCssImageUrl(path) + "\"); "
                + "-fx-background-size: cover; -fx-background-position: center;");
    }

    private String toCssImageUrl(String path) {
        if (path.startsWith("http://") || path.startsWith("https://") || path.startsWith("file:")) {
            return path;
        }
        return Path.of(path).toUri().toString();
    }

    private String formatCurrency(float amount) {
        return currencyFormat.format((long) amount) + "VND";
    }

    private String formatTimeRemaining(DashboardAuctionRow row) {
        if (row.getEndTime() == null) return "-";

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
        if (container == null) {
            return;
        }
        container.getChildren().clear();
        Label empty = new Label(message);
        empty.getStyleClass().add("bid-time");
        container.getChildren().add(empty);
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.showAndWait();
    }

    private void refreshCurrentUserBids() {
        User user = SessionManager.getCurrentUser();
        if (user != null) {
            loadMyBidsData(user.getUsername());
        }
    }

    private static final class MyBidsLoad {
        private final List<DashboardAuctionRow> active;
        private final List<DashboardAuctionRow> completed;
        private final List<SellerAuctionRowDTO> selling;

        private MyBidsLoad(List<DashboardAuctionRow> active, List<DashboardAuctionRow> completed,
                           List<SellerAuctionRowDTO> selling) {
            this.active = active == null ? List.of() : active;
            this.completed = completed == null ? List.of() : completed;
            this.selling = selling == null ? List.of() : selling;
        }
    }

    @Override
    public void onAuctionUpdatePush(AuctionUpdatePushDTO payload) {
        refreshCurrentUserBids();
    }

    @Override
    public void onNotificationPush(NotificationPushDTO payload) {
        appHeader.refreshNotificationBadge();
    }

    @Override
    public void onWalletUpdatePush(WalletUpdatePushDTO payload) {
        appHeader.refreshWalletQuickInfo();
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
