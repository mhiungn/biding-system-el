package Client.features.profile;

import Client.core.ui.NavigationController;
import Client.features.auth.SessionManager;
import CommonClasses.User;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Controller for the User Profile screen.
 * <p>
 * Loads the logged-in user's data and statistics from {@link ProfileService}
 * and populates the FXML labels. Handles the logout flow.
 * </p>
 */
public class UserProfileController extends NavigationController {

    // ========================== FXML Fields ==========================

    @FXML private Label lblUsername;
    @FXML private Label lblMemberSince;
    @FXML private Label lblEmail;
    @FXML private Label lblPhone;
    @FXML private Label lblLocation;
    @FXML private Label lblActiveBids;
    @FXML private Label lblLanguage;
    @FXML private Label lblCurrency;

    // ========================== Service & State ==========================

    private final ProfileService profileService = new ProfileService();
    private final NumberFormat currencyFormat = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    // ========================== Initialization ==========================

    @FXML
    public void initialize() {
        User user = SessionManager.getCurrentUser();
        if (user == null) {
            clearProfile();
            return;
        }

        populateProfile(user);
        populateStats(user.getUsername());
    }

    // ========================== Profile Population ==========================

    /**
     * Populates the profile section with user data.
     */
    private void populateProfile(User user) {
        if (lblUsername != null) {
            lblUsername.setText(user.getUsername());
        }

        if (lblEmail != null) {
            lblEmail.setText(user.getEmail());
        }

        if (lblMemberSince != null) {
            // The users table doesn't store created_at in the User model yet
            // so we show a placeholder until the schema is extended
            lblMemberSince.setText("Member");
        }

        // Phone and Location are not in the DB schema yet
        if (lblPhone != null) {
            lblPhone.setText("Not set");
        }
        if (lblLocation != null) {
            lblLocation.setText("Not set");
        }
    }

    /**
     * Populates the statistics section with data from ProfileService.
     */
    private void populateStats(String username) {
        int bidsPlaced = profileService.getBidsPlaced(username);
        int auctionsWon = profileService.getAuctionsWon(username);
        int auctionsCreated = profileService.getAuctionsCreated(username);
        int activeParticipations = profileService.getActiveParticipations(username);

        // Calculate win rate
        double winRate = bidsPlaced > 0 ? ((double) auctionsWon / bidsPlaced) * 100 : 0;

        // Update stats labels by looking up the parent VBox structure
        // The FXML uses a stats-row layout where each stat-card has stat-value and stat-label children
        updateStatLabels(bidsPlaced, auctionsWon, winRate, auctionsCreated, activeParticipations);
    }

    /**
     * Updates the stat value labels in the profile.
     * Since these are not bound by fx:id (they use generic stat-value class),
     * we need to look them up by position in the parent hierarchy.
     */
    private void updateStatLabels(int bidsPlaced, int auctionsWon, double winRate,
                                  int auctionsCreated, int activeParticipations) {
        // The stat values are in the FXML without individual fx:id bindings.
        // We find them by traversing the scene graph.
        // This is safe because the FXML structure is fixed.
        if (lblUsername != null && lblUsername.getScene() != null) {
            var root = lblUsername.getScene().getRoot();

            // Find all labels with class "stat-value" and update them in order
            var statValues = root.lookupAll(".stat-value");
            var statList = new java.util.ArrayList<>(statValues);

            if (statList.size() >= 6) {
                // Row 1: BIDS PLACED, AUCTIONS WON, WIN RATE
                ((Label) statList.get(0)).setText(String.valueOf(bidsPlaced));
                ((Label) statList.get(1)).setText(String.valueOf(auctionsWon));
                ((Label) statList.get(2)).setText(String.format("%.1f%%", winRate));

                // Row 2: ITEMS BOUGHT (=auctions won), ITEMS SOLD (=auctions created), TOTAL SPENT
                ((Label) statList.get(3)).setText(String.valueOf(auctionsWon));
                ((Label) statList.get(4)).setText(String.valueOf(auctionsCreated));
                ((Label) statList.get(5)).setText("—");
            }
        }

        // Active bids has an fx:id
        if (lblActiveBids != null) {
            lblActiveBids.setText(activeParticipations + " ongoing");
        }
    }

    /**
     * Clears all profile fields when no user is logged in.
     */
    private void clearProfile() {
        if (lblUsername != null) lblUsername.setText("Not logged in");
        if (lblMemberSince != null) lblMemberSince.setText("");
        if (lblEmail != null) lblEmail.setText("—");
        if (lblPhone != null) lblPhone.setText("—");
        if (lblLocation != null) lblLocation.setText("—");
        if (lblActiveBids != null) lblActiveBids.setText("0 ongoing");
    }

    // ========================== Actions ==========================

    /**
     * Handles the logout button click.
     * Clears the session and navigates to the login screen.
     */
    @FXML
    public void handleLogout(ActionEvent event) {
        SessionManager.clear();

        try {
            switchToLogin(event);
        } catch (IOException e) {
            System.err.println("[UserProfileController] Error navigating to login: " + e.getMessage());
        }
    }
}
