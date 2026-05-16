package Client.features.bidding;

import Client.core.ui.NavigationController;
import Client.features.auth.SessionManager;
import CommonClasses.Bid;
import CommonClasses.User;
import Server.dao.DashboardAuctionRow;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.text.NumberFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Controller for the Bidding Detail screen.
 * <p>
 * Loads auction data from {@link AuctionDetailService} and populates FXML
 * fields. Manages a countdown timer and dynamically builds bid history.
 * When NetworkClient integration is ready, only the service layer changes —
 * this controller stays the same.
 * </p>
 */
public class BiddingDetailController extends NavigationController {

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

    // Seller
    @FXML private Label lblSellerName;

    // Bid history container
    @FXML private VBox bidHistoryList;

    // ========================== Service & State ==========================

    private final AuctionDetailService service = new AuctionDetailService();
    private final NumberFormat currencyFormat = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    private int currentAuctionId = -1;
    private DashboardAuctionRow auctionDetail;
    private Timer countdownTimer;

    // ========================== Initialization ==========================

    /**
     * Called after FXML loading. Loads auction data if an ID has been set.
     */
    @FXML
    public void initialize() {
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

    // ========================== Data Loading ==========================

    /**
     * Loads all auction data from the service and populates the UI.
     */
    private void loadAuctionData(int auctionId) {
        auctionDetail = service.loadAuctionDetail(auctionId);
        if (auctionDetail == null) {
            showError("Auction not found");
            return;
        }

        populateItemInfo();
        populateBidInfo();
        populateSellerInfo();
        populateBidHistory();
        startCountdown();
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

        // Condition and location are not in the DB schema yet — show defaults
        lblCondition.setText("—");
        lblLocation.setText("—");
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
        int participants = service.getParticipantCount(currentAuctionId);
        lblWatcherCount.setText(participants + " participant" + (participants != 1 ? "s" : ""));

        // Minimum bid = current price + 1
        float minBid = currentPrice + 1;
        lblMinBid.setText("Minimum bid: " + formatCurrency(minBid));
    }

    /**
     * Populates the seller name (masked for privacy).
     */
    private void populateSellerInfo() {
        String owner = service.getAuctionOwner(currentAuctionId);
        lblSellerName.setText(maskUsername(owner));
    }

    /**
     * Dynamically builds the bid history list from database data.
     * Clears any FXML-hardcoded placeholder items first.
     */
    private void populateBidHistory() {
        bidHistoryList.getChildren().clear();

        List<Bid> bids = service.loadBidHistory(currentAuctionId);

        if (bids.isEmpty()) {
            Label noBids = new Label("No bids yet — be the first!");
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

    // ========================== Utility Methods ==========================

    /**
     * Formats a float as VND currency string.
     */
    private String formatCurrency(float amount) {
        return currencyFormat.format((long) amount) + "VND";
    }

    /**
     * Masks a username for privacy display (e.g. "seller_john" → "sell****ohn").
     */
    private String maskUsername(String username) {
        if (username == null || username.length() <= 4) {
            return username != null ? username : "Unknown";
        }
        int show = Math.min(4, username.length() / 3);
        return username.substring(0, show) + "****" + username.substring(username.length() - Math.min(2, show));
    }

    /**
     * Formats a Date as a relative "time ago" string.
     */
    private String formatTimeAgo(Date date) {
        if (date == null) return "—";

        long diff = System.currentTimeMillis() - date.getTime();
        long minutes = diff / 60_000;
        long hours = diff / 3_600_000;
        long days = diff / 86_400_000;

        if (minutes < 1) return "Just now";
        if (minutes < 60) return minutes + " min" + (minutes != 1 ? "s" : "") + " ago";
        if (hours < 24) return hours + " hour" + (hours != 1 ? "s" : "") + " ago";
        return days + " day" + (days != 1 ? "s" : "") + " ago";
    }

    /**
     * Shows an error message in the item title.
     */
    private void showError(String message) {
        lblItemTitle.setText(message);
    }

    // ========================== Cleanup ==========================

    @Override
    protected boolean onBeforeClose() {
        stopCountdown();
        return true;
    }
}
