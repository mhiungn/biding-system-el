package Client.features.profile;

import Client.core.ui.NavigationController;
import Client.components.LoadingOverlay;
import Client.features.auth.SessionManager;
import CommonClasses.User;
import CommonClasses.dto.UserProfileStatsDTO;
import CommonClasses.dto.WalletDTO;
import CommonClasses.dto.WalletUpdatePushDTO;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Optional;

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
    @FXML private Label lblRoleStatus;
    @FXML private Label lblBidsPlaced;
    @FXML private Label lblAuctionsWon;
    @FXML private Label lblWinRate;
    @FXML private Label lblItemsBought;
    @FXML private Label lblItemsSold;
    @FXML private Label lblTotalSpent;
    @FXML private Label lblWalletBalance;
    @FXML private Label lblWalletAvailable;
    @FXML private TextField depositAmountField;
    @FXML private StackPane avatarPlaceholder;
    @FXML private ImageView avatarImageView;

    // ========================== Service & State ==========================

    private final ProfileService profileService = new ProfileService();
    private final NumberFormat currencyFormat = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
    private final DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.US);
    private final LoadingOverlay loadingOverlay = new LoadingOverlay();
    private static final long MAX_AVATAR_BYTES = 5L * 1024L * 1024L;

    // ========================== Initialization ==========================

    @FXML
    public void initialize() {
        configureAvatar();

        User user = SessionManager.getCurrentUser();
        if (user == null) {
            clearProfile();
            return;
        }

        populateProfile(user);
        registerForPushUpdates();
        Platform.runLater(() -> loadProfileData(user.getUsername()));
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
            var memberSince = profileService.getMemberSince(user.getUsername());
            lblMemberSince.setText(memberSince == null ? "Member" : "Member since " + dateFormat.format(memberSince));
        }

        if (lblRoleStatus != null) {
            lblRoleStatus.setText("ADMIN".equalsIgnoreCase(user.getRole()) ? "Active Admin" : "Active User");
        }

        if (lblPhone != null) {
            lblPhone.setText(displayProfileValue(user.getPhone()));
        }
        if (lblLocation != null) {
            lblLocation.setText(displayProfileValue(user.getLocation()));
        }
    }

    private void configureAvatar() {
        if (avatarImageView == null) {
            return;
        }

        avatarImageView.setClip(new Circle(44, 44, 44));
        if (avatarImageView.getImage() == null) {
            showAvatarPlaceholder();
            return;
        }

        showAvatarImage(avatarImageView.getImage());
    }

    private void showAvatarPlaceholder() {
        if (avatarImageView != null) {
            avatarImageView.setImage(null);
            avatarImageView.setVisible(false);
            avatarImageView.setManaged(false);
        }
        if (avatarPlaceholder != null) {
            avatarPlaceholder.setVisible(true);
            avatarPlaceholder.setManaged(true);
        }
    }

    private void showAvatarImage(Image image) {
        if (avatarImageView != null) {
            avatarImageView.setImage(image);
            avatarImageView.setVisible(true);
            avatarImageView.setManaged(true);
        }
        if (avatarPlaceholder != null) {
            avatarPlaceholder.setVisible(false);
            avatarPlaceholder.setManaged(false);
        }
    }

    @FXML
    public void handleChangePhoto(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose Profile Photo");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Image files (*.png, *.jpg, *.jpeg)",
                "*.png",
                "*.jpg",
                "*.jpeg"
        ));

        File selectedFile = chooser.showOpenDialog(resolveWindow(event));
        if (selectedFile == null) {
            return;
        }

        if (!isValidAvatarFile(selectedFile)) {
            showAvatarPlaceholder();
            return;
        }

        try {
            Image selectedImage = new Image(selectedFile.toURI().toString(), 88, 88, false, true, false);
            if (selectedImage.isError() || selectedImage.getWidth() <= 0 || selectedImage.getHeight() <= 0) {
                showAvatarPlaceholder();
                showMessage(Alert.AlertType.ERROR, "Could not load the selected image.");
                return;
            }

            showAvatarImage(selectedImage);
        } catch (RuntimeException e) {
            showAvatarPlaceholder();
            showMessage(Alert.AlertType.ERROR, "Could not load the selected image.");
        }
    }

    private Window resolveWindow(ActionEvent event) {
        if (event != null && event.getSource() instanceof Node node && node.getScene() != null) {
            return node.getScene().getWindow();
        }
        return null;
    }

    private boolean isValidAvatarFile(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            showMessage(Alert.AlertType.ERROR, "Selected file does not exist.");
            return false;
        }
        if (file.length() > MAX_AVATAR_BYTES) {
            showMessage(Alert.AlertType.ERROR, "Profile photo must be 5 MB or smaller.");
            return false;
        }
        if (!hasSupportedAvatarExtension(file)) {
            showMessage(Alert.AlertType.ERROR, "Choose a PNG, JPG, or JPEG image.");
            return false;
        }
        return true;
    }

    private boolean hasSupportedAvatarExtension(File file) {
        String fileName = file.getName().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".png") || fileName.endsWith(".jpg") || fileName.endsWith(".jpeg");
    }

    /**
     * Populates the statistics section with data from ProfileService.
     */
    private void populateStats(String username) {
        updateStatLabels(profileService.loadStats(username));
    }

    /**
     * Updates the stat value labels in the profile.
     * Since these are not bound by fx:id (they use generic stat-value class),
     * we need to look them up by position in the parent hierarchy.
     */
    private void updateStatLabels(UserProfileStatsDTO stats) {
        if (stats == null) {
            return;
        }
        if (lblBidsPlaced != null) lblBidsPlaced.setText(String.valueOf(stats.getBidsPlaced()));
        if (lblAuctionsWon != null) lblAuctionsWon.setText(String.valueOf(stats.getAuctionsWon()));
        if (lblWinRate != null) lblWinRate.setText(String.format("%.1f%%", stats.getWinRate()));
        if (lblItemsBought != null) lblItemsBought.setText(String.valueOf(stats.getItemsBought()));
        if (lblItemsSold != null) lblItemsSold.setText(String.valueOf(stats.getItemsSold()));
        if (lblTotalSpent != null) lblTotalSpent.setText(formatMoney(stats.getTotalSpent()));
        if (lblActiveBids != null) {
            lblActiveBids.setText(stats.getActiveParticipations() + " ongoing");
        }
    }

    private void populateWallet(String username) {
        WalletDTO wallet = profileService.getWallet(username);
        renderWallet(wallet);
    }

    private void renderWallet(WalletDTO wallet) {
        if (lblWalletBalance != null) {
            lblWalletBalance.setText(formatMoney(wallet.getBalance()));
        }
        if (lblWalletAvailable != null) {
            lblWalletAvailable.setText(formatMoney(wallet.getAvailableBalance()));
        }
    }

    private void loadProfileData(String username) {
        Task<ProfileLoad> task = new Task<>() {
            @Override
            protected ProfileLoad call() {
                return new ProfileLoad(profileService.getWallet(username), profileService.loadStats(username));
            }
        };
        task.setOnSucceeded(event -> {
            loadingOverlay.hide();
            ProfileLoad data = task.getValue();
            renderWallet(data.wallet);
            updateStatLabels(data.stats);
        });
        task.setOnFailed(event -> {
            loadingOverlay.hide();
            showMessage(Alert.AlertType.ERROR, "Could not load profile data.");
        });

        loadingOverlay.show(lblUsername, "Loading profile...");
        Thread thread = new Thread(task, "profile-load");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Clears all profile fields when no user is logged in.
     */
    private void clearProfile() {
        if (lblUsername != null) lblUsername.setText("Not logged in");
        if (lblMemberSince != null) lblMemberSince.setText("");
        if (lblEmail != null) lblEmail.setText("-");
        if (lblPhone != null) lblPhone.setText("-");
        if (lblLocation != null) lblLocation.setText("-");
        if (lblActiveBids != null) lblActiveBids.setText("0 ongoing");
        if (lblRoleStatus != null) lblRoleStatus.setText("");
        if (lblBidsPlaced != null) lblBidsPlaced.setText("0");
        if (lblAuctionsWon != null) lblAuctionsWon.setText("0");
        if (lblWinRate != null) lblWinRate.setText("0%");
        if (lblItemsBought != null) lblItemsBought.setText("0");
        if (lblItemsSold != null) lblItemsSold.setText("0");
        if (lblTotalSpent != null) lblTotalSpent.setText("0");
        if (lblWalletBalance != null) lblWalletBalance.setText("0");
        if (lblWalletAvailable != null) lblWalletAvailable.setText("0");
    }

    // ========================== Actions ==========================

    /**
     * Handles the logout button click.
     * Clears the session and navigates to the login screen.
     */
    @FXML
    public void handleLogout(ActionEvent event) {
        try {
            navigationService.logoutToLogin(event);
        } catch (IOException e) {
            System.err.println("[UserProfileController] Error navigating to login: " + e.getMessage());
        }
    }

    @FXML
    public void handleDeposit(ActionEvent event) {
        User user = SessionManager.getCurrentUser();
        if (user == null || depositAmountField == null) {
            return;
        }

        Long amount = parseMoney(depositAmountField.getText());
        if (amount == null) {
            showMessage(Alert.AlertType.ERROR, "Invalid deposit amount.");
            return;
        }

        Task<ProfileLoad> task = new Task<>() {
            @Override
            protected ProfileLoad call() {
                WalletDTO result = profileService.deposit(user.getUsername(), amount);
                if (!result.isSuccess()) {
                    return new ProfileLoad(result, null);
                }
                return new ProfileLoad(result, profileService.loadStats(user.getUsername()));
            }
        };
        task.setOnSucceeded(taskEvent -> {
            loadingOverlay.hide();
            ProfileLoad data = task.getValue();
            if (!data.wallet.isSuccess()) {
                showMessage(Alert.AlertType.ERROR, data.wallet.getMessage());
                return;
            }

            depositAmountField.clear();
            renderWallet(data.wallet);
            updateStatLabels(data.stats);
            showMessage(Alert.AlertType.INFORMATION, "Deposit successful.");
        });
        task.setOnFailed(taskEvent -> {
            loadingOverlay.hide();
            showMessage(Alert.AlertType.ERROR, "Could not deposit money.");
        });

        loadingOverlay.show(depositAmountField, "Depositing...");
        Thread thread = new Thread(task, "profile-deposit");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    public void handleEditEmail(ActionEvent event) {
        User user = SessionManager.getCurrentUser();
        if (user == null) {
            return;
        }

        TextInputDialog dialog = new TextInputDialog(user.getEmail());
        dialog.setTitle("Update Email");
        dialog.setHeaderText(null);
        dialog.setContentText("Email");
        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty()) {
            return;
        }

        User updated = profileService.updateEmail(user.getUsername(), result.get());
        if (updated == null) {
            showMessage(Alert.AlertType.ERROR, "Email is invalid or already in use.");
            return;
        }

        SessionManager.setCurrentUser(updated);
        populateProfile(updated);
        showMessage(Alert.AlertType.INFORMATION, "Email updated.");
    }

    @FXML
    public void handleChangePassword(ActionEvent event) {
        User user = SessionManager.getCurrentUser();
        if (user == null) {
            return;
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Change Password");
        dialog.setHeaderText(null);
        dialog.setContentText("New password");
        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty()) {
            return;
        }

        User updated = profileService.updatePassword(user.getUsername(), result.get());
        if (updated == null) {
            showMessage(Alert.AlertType.ERROR, "Password must be at least 4 characters.");
            return;
        }

        SessionManager.setCurrentUser(updated);
        showMessage(Alert.AlertType.INFORMATION, "Password updated.");
    }

    @FXML
    public void handleEditPhone(ActionEvent event) {
        User user = SessionManager.getCurrentUser();
        if (user == null) {
            return;
        }

        TextInputDialog dialog = new TextInputDialog(blankIfNull(user.getPhone()));
        dialog.setTitle("Update Phone");
        dialog.setHeaderText(null);
        dialog.setContentText("Phone");
        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty()) {
            return;
        }

        String phone = normalizeProfileText(result.get());
        if (phone != null && !isValidPhone(phone)) {
            showMessage(Alert.AlertType.ERROR, "Phone must be 7-30 characters and use only digits, spaces, +, -, (, ).");
            return;
        }

        User updated = profileService.updatePhone(user.getUsername(), phone);
        if (updated == null) {
            showMessage(Alert.AlertType.ERROR, "Could not update phone.");
            return;
        }

        SessionManager.setCurrentUser(updated);
        populateProfile(updated);
        showMessage(Alert.AlertType.INFORMATION, "Phone updated.");
    }

    @FXML
    public void handleEditLocation(ActionEvent event) {
        User user = SessionManager.getCurrentUser();
        if (user == null) {
            return;
        }

        TextInputDialog dialog = new TextInputDialog(blankIfNull(user.getLocation()));
        dialog.setTitle("Update Location");
        dialog.setHeaderText(null);
        dialog.setContentText("Location");
        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty()) {
            return;
        }

        String location = normalizeProfileText(result.get());
        if (location != null && location.length() > 255) {
            showMessage(Alert.AlertType.ERROR, "Location must be 255 characters or fewer.");
            return;
        }

        User updated = profileService.updateLocation(user.getUsername(), location);
        if (updated == null) {
            showMessage(Alert.AlertType.ERROR, "Could not update location.");
            return;
        }

        SessionManager.setCurrentUser(updated);
        populateProfile(updated);
        showMessage(Alert.AlertType.INFORMATION, "Location updated.");
    }

    @FXML
    public void handleUnavailableProfileField(ActionEvent event) {
        showMessage(Alert.AlertType.INFORMATION, "This profile field is not stored in the current database schema.");
    }

    private String displayProfileValue(String value) {
        return value == null || value.isBlank() ? "Not set" : value;
    }

    private String blankIfNull(String value) {
        return value == null ? "" : value;
    }

    private String normalizeProfileText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isValidPhone(String phone) {
        return phone.matches("[0-9+()\\-\\s]{7,30}") && phone.matches(".*\\d.*");
    }

    private Long parseMoney(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String formatMoney(long amount) {
        return currencyFormat.format(amount);
    }

    private void showMessage(Alert.AlertType type, String message) {
        Alert alert = new Alert(type);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private static final class ProfileLoad {
        private final WalletDTO wallet;
        private final UserProfileStatsDTO stats;

        private ProfileLoad(WalletDTO wallet, UserProfileStatsDTO stats) {
            this.wallet = wallet;
            this.stats = stats;
        }
    }

    @Override
    public void onWalletUpdatePush(WalletUpdatePushDTO payload) {
        User user = SessionManager.getCurrentUser();
        if (user == null || payload == null || !user.getUsername().equals(payload.getUsername())) {
            return;
        }
        if (payload.getWallet() != null) {
            renderWallet(payload.getWallet());
        }
        loadProfileData(user.getUsername());
    }
}
