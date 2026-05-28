package Client.features.bidding;

import Client.core.ui.NavigationController;
import Client.core.ui.AvatarService;
import Client.components.AppHeader;
import Client.components.LoadingOverlay;
import Client.core.ui.FxDebouncer;
import Client.core.ui.ItemImageUrl;
import Client.core.ui.RefreshablePage;
import Client.features.auth.SessionManager;
import CommonClasses.Bid;
import CommonClasses.dto.AuctionUpdatePushDTO;
import CommonClasses.dto.DashboardAuctionRow;
import CommonClasses.User;
import CommonClasses.dto.NotificationPushDTO;
import CommonClasses.dto.WalletUpdatePushDTO;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.text.NumberFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Controller for the Bidding Detail screen.
 * <p>
 * Loads auction data from {@link AuctionDetailService} and populates FXML
 * fields. Manages a countdown timer and dynamically builds bid history.
 * When NetworkClient integration is ready, only the service layer changes -
 * this controller stays the same.
 * </p>
 */
public class BiddingDetailController extends NavigationController implements RefreshablePage {

    // ========================== FXML Fields ==========================

    // Item info
    @FXML private Label lblItemTitle;
    @FXML private Label lblDescription;
    @FXML private Label lblCondition;
    @FXML private Label lblCategory;
    @FXML private Label lblLocation;
    @FXML private Label lblItemId;

    // Timer
    @FXML private Label lblTimerValue;
    @FXML private Label lblHours;
    @FXML private Label lblMinutes;
    @FXML private Label lblSeconds;

    // Bid info
    @FXML private Label lblCurrentBid;
    @FXML private Label lblBidCount;
    @FXML private Label lblWatcherCount;
    @FXML private Label lblMinBid;
    @FXML private TextField txtBidAmount;
    @FXML private Button btnPlaceBid;
    @FXML private AppHeader appHeader;
    @FXML private Region mainImage;
    @FXML private Region thumbnail1;
    @FXML private Region thumbnail2;
    @FXML private Region thumbnail3;
    @FXML private Region thumbnail4;

    // Seller
    @FXML private Label lblSellerName;
    @FXML private ImageView sellerAvatarImageView;

    // Bid history container
    @FXML private VBox bidHistoryList;

    // ========================== Service & State ==========================

    private final AuctionDetailService service = new AuctionDetailService();
    private final AvatarService avatarService = AvatarService.getInstance();
    private final NumberFormat currencyFormat = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
    private final LoadingOverlay loadingOverlay = new LoadingOverlay();
    private static final Duration PUSH_REFRESH_DELAY = Duration.millis(400);
    private final FxDebouncer auctionPushRefreshDebouncer = new FxDebouncer(PUSH_REFRESH_DELAY);

    private int currentAuctionId = -1;
    private DashboardAuctionRow auctionDetail;
    private int participantCount;
    private String auctionOwner;
    private String auctionOwnerProfileImageUrl;
    private List<Bid> bidHistory = List.of();
    private Timer countdownTimer;

    // ========================== Initialization ==========================

    /**
     * Called after FXML loading. Loads auction data if an ID has been set.
     */
    @FXML
    public void initialize() {
        appHeader.configure(this);
        configureSellerAvatar();
        registerForPushUpdates();

        if (btnPlaceBid != null) {
            btnPlaceBid.setOnAction(e -> handlePlaceBid());
        }

        // If auctionId was set before initialize (e.g. via static), load it
        if (currentAuctionId > 0) {
            loadAuctionData(currentAuctionId);
        }
    }

    /**
     * Sets the auction ID and triggers data loading.
     * Call this before or after the scene is shown.
     *
     * @param auctionId the auction ID to display
     */
    public void setAuctionId(int auctionId) {
        this.currentAuctionId = auctionId;
        loadAuctionData(auctionId);
    }

    @Override
    public void onPageShown() {
        if (appHeader != null) {
            appHeader.refreshDynamicUserInfo();
        }
    }

    // ========================== Data Loading ==========================

    /**
     * Loads all auction data from the service and populates the UI.
     */
    private void loadAuctionData(int auctionId) {
        Task<AuctionDetailLoad> task = new Task<>() {
            @Override
            protected AuctionDetailLoad call() {
                DashboardAuctionRow detail = service.loadAuctionDetail(auctionId);
                if (detail == null) {
                    return new AuctionDetailLoad(null, 0, null, null, List.of());
                }
                String owner = detail.getOwnerUsername();
                if (owner == null || owner.isBlank()) {
                    owner = service.getAuctionOwner(auctionId);
                }
                return new AuctionDetailLoad(
                        detail,
                        service.getParticipantCount(auctionId),
                        owner,
                        detail.getOwnerProfileImageUrl(),
                        service.loadBidHistory(auctionId));
            }
        };
        task.setOnSucceeded(event -> {
            loadingOverlay.hide();
            AuctionDetailLoad loaded = task.getValue();
            auctionDetail = loaded.detail;
            participantCount = loaded.participantCount;
            auctionOwner = loaded.owner;
            auctionOwnerProfileImageUrl = loaded.ownerProfileImageUrl;
            bidHistory = loaded.bids;
            if (auctionDetail == null) {
                showError("Auction not found");
                return;
            }

            populateItemInfo();
            populateBidInfo();
            populateSellerInfo();
            renderItemImages();
            populateBidHistory();
            startCountdown();
        });
        task.setOnFailed(event -> {
            loadingOverlay.hide();
            showError("Auction could not be loaded");
        });

        loadingOverlay.show(lblItemTitle, "Loading auction...");
        Thread thread = new Thread(task, "auction-detail-load");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Populates item title, description, category, and specifications.
     */
    private void populateItemInfo() {
        if (auctionDetail.getItem() == null) return;

        var item = auctionDetail.getItem();
        lblItemTitle.setText(item.getName());
        lblDescription.setText(item.getDescription() != null ? item.getDescription() : "No description available");

        // Category from item type
        String category = item.getClass().getSimpleName();
        lblCategory.setText(category);

        // Item ID
        lblItemId.setText("AUC-" + auctionDetail.getAuctionId());

        lblCondition.setText(item.getItemCondition() != null && !item.getItemCondition().isBlank()
                ? item.getItemCondition() : "-");
        lblLocation.setText(item.getLocation() != null && !item.getLocation().isBlank()
                ? item.getLocation() : "-");
    }

    /**
     * Populates current bid, bid count, minimum bid, and watcher count.
     */
    private void populateBidInfo() {
        float currentPrice = auctionDetail.getItem().getCurrentHighestPrice();
        int bidCount = auctionDetail.getBidCount();

        lblCurrentBid.setText(formatCurrency(currentPrice));
        lblBidCount.setText(bidCount + " bid" + (bidCount != 1 ? "s" : ""));

        // Watcher count = participant count
        lblWatcherCount.setText(participantCount + " participant" + (participantCount != 1 ? "s" : ""));

        float minBid = currentPrice + auctionDetail.getMinimumBidIncrement();
        lblMinBid.setText("Minimum bid: " + formatCurrency(minBid));

        if (btnPlaceBid != null) {
            btnPlaceBid.setDisable(isAuctionEnded());
        }
    }

    /**
     * Populates the seller name (masked for privacy).
     */
    private void populateSellerInfo() {
        lblSellerName.setText(maskUsername(auctionOwner));
        renderSellerAvatar();
    }

    private void configureSellerAvatar() {
        if (sellerAvatarImageView == null) {
            return;
        }

        sellerAvatarImageView.setClip(new Circle(20, 20, 20));
        renderSellerAvatar();
    }

    private void renderSellerAvatar() {
        if (sellerAvatarImageView == null) {
            return;
        }

        Image image = avatarService.getAvatarImage(auctionOwner, auctionOwnerProfileImageUrl);
        showSellerAvatarImage(image);
    }

    private void showSellerAvatarImage(Image image) {
        avatarService.applyAvatarImage(sellerAvatarImageView, image);
    }

    /**
     * Dynamically builds the bid history list from database data.
     * Clears any FXML-hardcoded placeholder items first.
     */
    private void populateBidHistory() {
        bidHistoryList.getChildren().clear();

        List<Bid> bids = bidHistory;

        if (bids.isEmpty()) {
            Label noBids = new Label("No bids yet - be the first!");
            noBids.getStyleClass().add("bid-time");
            bidHistoryList.getChildren().add(noBids);
            return;
        }

        for (Bid bid : bids) {
            HBox row = createBidHistoryRow(bid);
            bidHistoryList.getChildren().add(row);
        }
    }

    /**
     * Creates a single bid history row matching the FXML structure.
     */
    private HBox createBidHistoryRow(Bid bid) {
        HBox row = new HBox();
        row.getStyleClass().add("bid-history-item");

        // Left: bidder name + time
        VBox left = new VBox(2);
        left.getStyleClass().add("bid-history-item");
        HBox.setHgrow(left, Priority.ALWAYS);

        Label bidderName = new Label(maskUsername(bid.getBidderUsername()));
        bidderName.getStyleClass().add("bidder-name");

        Label bidTime = new Label(formatTimeAgo(bid.getCreatedAt()));
        bidTime.getStyleClass().add("bid-time");

        left.getChildren().addAll(bidderName, bidTime);

        // Right: bid price
        Label bidPrice = new Label(formatCurrency(bid.getBid()));
        bidPrice.getStyleClass().add("bid-price");

        row.getChildren().addAll(left, bidPrice);
        return row;
    }

    // ========================== Countdown Timer ==========================

    /**
     * Starts a 1-second countdown timer to the auction end time.
     */
    private void startCountdown() {
        stopCountdown();

        Date endTime = auctionDetail.getEndTime();
        if (endTime == null) {
            updateTimerDisplay(0, 0, 0);
            lblTimerValue.setText("No end time");
            return;
        }

        countdownTimer = new Timer(true);
        countdownTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                long remaining = endTime.getTime() - System.currentTimeMillis();
                if (remaining <= 0) {
                    Platform.runLater(() -> {
                        updateTimerDisplay(0, 0, 0);
                        lblTimerValue.setText("ENDED");
                        if (btnPlaceBid != null) {
                            btnPlaceBid.setDisable(true);
                        }
                    });
                    cancel();
                    return;
                }

                long totalSeconds = remaining / 1000;
                int hours = (int) (totalSeconds / 3600);
                int minutes = (int) ((totalSeconds % 3600) / 60);
                int seconds = (int) (totalSeconds % 60);

                Platform.runLater(() -> updateTimerDisplay(hours, minutes, seconds));
            }
        }, 0, 1000);
    }

    /**
     * Stops the countdown timer if running.
     */
    private void stopCountdown() {
        if (countdownTimer != null) {
            countdownTimer.cancel();
            countdownTimer = null;
        }
    }

    /**
     * Updates the timer display labels.
     */
    private void updateTimerDisplay(int hours, int minutes, int seconds) {
        String h = String.format("%02d", hours);
        String m = String.format("%02d", minutes);
        String s = String.format("%02d", seconds);

        lblHours.setText(h);
        lblMinutes.setText(m);
        lblSeconds.setText(s);
        lblTimerValue.setText(h + ":" + m + ":" + s);
    }

    // ========================== Actions ==========================

    @FXML
    private void handlePlaceBid() {
        if (auctionDetail == null || currentAuctionId <= 0) {
            showError("Auction data is not loaded yet");
            return;
        }

        User user = SessionManager.getCurrentUser();
        if (user == null) {
            showError("Please log in before placing a bid");
            return;
        }

        if (isAuctionEnded()) {
            if (btnPlaceBid != null) {
                btnPlaceBid.setDisable(true);
            }
            showError("This auction has ended");
            return;
        }

        if (auctionOwner != null && auctionOwner.equals(user.getUsername())) {
            showError("You cannot bid on your own auction.");
            return;
        }

        String rawAmount = txtBidAmount.getText() == null ? "" : txtBidAmount.getText().trim();
        float amount;
        try {
            amount = Float.parseFloat(rawAmount);
        } catch (NumberFormatException e) {
            showError("Please enter a valid bid amount");
            return;
        }

        float currentPrice = auctionDetail.getItem().getCurrentHighestPrice();
        float minimumBid = currentPrice + auctionDetail.getMinimumBidIncrement();
        if (amount < minimumBid) {
            showError("Bid must be at least " + formatCurrency(minimumBid));
            return;
        }

        if (btnPlaceBid != null) {
            btnPlaceBid.setDisable(true);
        }
        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() {
                return service.placeBid(currentAuctionId, user.getUsername(), amount);
            }
        };
        task.setOnSucceeded(event -> {
            loadingOverlay.hide();
            if (btnPlaceBid != null) {
                btnPlaceBid.setDisable(false);
            }
            if (!task.getValue()) {
                showError("Bid failed. The auction may have ended or the amount is too low.");
                return;
            }

            txtBidAmount.clear();
            loadAuctionData(currentAuctionId);
            appHeader.refreshWalletQuickInfo();
        });
        task.setOnFailed(event -> {
            loadingOverlay.hide();
            if (btnPlaceBid != null) {
                btnPlaceBid.setDisable(false);
            }
            showError("Bid failed. Please try again.");
        });

        loadingOverlay.show(btnPlaceBid, "Placing bid...");
        Thread thread = new Thread(task, "auction-place-bid");
        thread.setDaemon(true);
        thread.start();
    }

    // ========================== Utility Methods ==========================

    /**
     * Formats a float as VND currency string.
     */
    private String formatCurrency(float amount) {
        return currencyFormat.format((long) amount) + "VND";
    }

    /**
     * Masks a username for privacy display (e.g. "seller_john" -> "sell****ohn").
     */
    private String maskUsername(String username) {
        if (username == null || username.length() <= 4) {
            return username != null ? username : "Unknown";
        }
        int show = Math.min(4, username.length() / 3);
        return username.substring(0, show) + "****" + username.substring(username.length() - Math.min(2, show));
    }

    private void renderItemImages() {
        List<String> images = auctionDetail == null ? List.of() : auctionDetail.getImagePaths();
        applyImageBackground(mainImage, images.isEmpty() ? null : images.get(0), false);

        Region[] thumbnails = {thumbnail1, thumbnail2, thumbnail3, thumbnail4};
        for (int i = 0; i < thumbnails.length; i++) {
            Region thumbnail = thumbnails[i];
            if (thumbnail == null) {
                continue;
            }
            if (i < images.size()) {
                String imagePath = images.get(i);
                applyImageBackground(thumbnail, imagePath, true);
                thumbnail.setOnMouseClicked(event -> applyImageBackground(mainImage, imagePath, false));
            } else {
                thumbnail.setStyle("");
                thumbnail.setOnMouseClicked(null);
            }
        }
    }

    private void applyImageBackground(Region region, String path, boolean thumbnail) {
        if (region == null) {
            return;
        }
        if (path == null || path.isBlank()) {
            region.setStyle("");
            return;
        }
        String imageUrl = thumbnail ? ItemImageUrl.thumbnail(path) : ItemImageUrl.detail(path);
        region.setStyle(
                "-fx-background-image: url('" + imageUrl + "'); " +
                        "-fx-background-size: cover; " +
                        "-fx-background-position: center;"
        );
    }

    /**
     * Formats a Date as a relative "time ago" string.
     */
    private String formatTimeAgo(Date date) {
        if (date == null) return "-";

        long diff = System.currentTimeMillis() - date.getTime();
        long minutes = diff / 60_000;
        long hours = diff / 3_600_000;
        long days = diff / 86_400_000;

        if (minutes < 1) return "Just now";
        if (minutes < 60) return minutes + " min" + (minutes != 1 ? "s" : "") + " ago";
        if (hours < 24) return hours + " hour" + (hours != 1 ? "s" : "") + " ago";
        return days + " day" + (days != 1 ? "s" : "") + " ago";
    }

    private boolean isAuctionEnded() {
        return auctionDetail == null
                || auctionDetail.getEndTime() == null
                || auctionDetail.getEndTime().getTime() <= System.currentTimeMillis();
    }

    @Override
    public void onAuctionUpdatePush(AuctionUpdatePushDTO payload) {
        if (payload != null && payload.getAuctionId() == currentAuctionId) {
            auctionPushRefreshDebouncer.run(() -> loadAuctionData(currentAuctionId));
        }
    }

    @Override
    public void onNotificationPush(NotificationPushDTO payload) {
        appHeader.refreshNotificationBadge();
    }

    @Override
    public void onWalletUpdatePush(WalletUpdatePushDTO payload) {
        appHeader.refreshWalletQuickInfo();
    }

    /**
     * Shows an error message in the item title.
     */
    private void showError(String message) {
        if (lblItemTitle != null) {
            lblItemTitle.setText(message);
        }
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.showAndWait();
    }

    private static final class AuctionDetailLoad {
        private final DashboardAuctionRow detail;
        private final int participantCount;
        private final String owner;
        private final String ownerProfileImageUrl;
        private final List<Bid> bids;

        private AuctionDetailLoad(DashboardAuctionRow detail, int participantCount, String owner,
                                  String ownerProfileImageUrl, List<Bid> bids) {
            this.detail = detail;
            this.participantCount = participantCount;
            this.owner = owner;
            this.ownerProfileImageUrl = ownerProfileImageUrl;
            this.bids = bids == null ? List.of() : bids;
        }
    }

    // ========================== Cleanup ==========================

    @Override
    protected void onBeforeNavigate() {
        auctionPushRefreshDebouncer.cancel();
        super.onBeforeNavigate();
        stopCountdown();
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
