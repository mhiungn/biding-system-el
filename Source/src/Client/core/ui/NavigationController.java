package Client.core.ui;

import Client.components.NotificationPopup;
import Client.components.HeaderSearchPopup;
import Client.core.network.NetworkRequestClient;
import Client.core.network.NetworkPushManager;
import Client.core.network.PushEventListener;
import Client.features.bidding.BiddingDetailController;
import Client.features.auth.SessionManager;
import Client.features.notifications.NotificationClientService;
import Client.features.profile.ProfileService;
import Client.features.search.SearchService;
import CommonClasses.User;
import CommonClasses.dto.NotificationDTO;
import CommonClasses.dto.WalletDTO;
import Server.service.NotificationApplicationService;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Loads other feature FXML roots onto the current {@link Stage}.
 */
public abstract class NavigationController extends BaseController implements PushEventListener {

    private static final String CONTROLLER_KEY = "client.controller";

    private static final String DASHBOARD = "/client/views/dashboard/dashboard.fxml";
    private static final String BIDDING_DETAIL = "/client/views/bidding/bidding_detail.fxml";
    private static final String MY_BIDS = "/client/views/bidding/mybids.fxml";
    private static final String SIGNUP = "/client/views/auth/signup.fxml";
    private static final String LOGIN = "/client/views/auth/login.fxml";
    private static final String USER_PROFILE = "/client/views/profile/user_profile.fxml";
    private static final String SELL_ITEM = "/client/views/sell/sell_item.fxml";
    private static final String NOTIFICATION_BADGE_KEY = "notification.badge";

    private final NotificationClientService notificationClientService = new NotificationClientService();
    private final SearchService searchService = new SearchService();
    private final ProfileService profileService = new ProfileService();

    public void switchToDashboard(ActionEvent event) throws IOException {
        switchScene(event, DASHBOARD);
    }

    public void switchToSellItem(ActionEvent event) throws IOException {
        switchScene(event, SELL_ITEM);
    }

    public void switchToBiddingDetails(ActionEvent event) throws IOException {
        switchScene(event, BIDDING_DETAIL);
    }

    public void switchToBiddingDetails(ActionEvent event, int auctionId) throws IOException {
        LoadedView loadedView = loadView(BIDDING_DETAIL);

        BiddingDetailController controller = (BiddingDetailController) loadedView.controller;
        Stage stage = getEventStage(event);
        replaceCurrentScene(stage, loadedView.root);
        controller.setAuctionId(auctionId);
    }

    public void switchToMyBids(ActionEvent event) throws IOException {
        switchScene(event, MY_BIDS);
    }

    public void switchToSignup(ActionEvent event) throws IOException {
        switchScene(event, SIGNUP);
    }

    public void switchToLogin(ActionEvent event) throws IOException {
        switchScene(event, LOGIN);
    }

    @FXML
    public void openUserProfile(ActionEvent event) throws IOException {
        LoadedView loadedView = loadView(USER_PROFILE);

        Stage profileStage = new Stage();
        profileStage.setTitle("User Profile");
        profileStage.initOwner(getEventStage(event));
        profileStage.initModality(Modality.WINDOW_MODAL);
        profileStage.initStyle(StageStyle.UNDECORATED);
        profileStage.setScene(new Scene(loadedView.root));
        profileStage.setResizable(false);
        profileStage.show();
        profileStage.sizeToScene();
        profileStage.centerOnScreen();
    }

    private void switchScene(ActionEvent event, String classpathFXML) throws IOException {
        LoadedView loadedView = loadView(classpathFXML);
        Stage stage = getEventStage(event);
        replaceCurrentScene(stage, loadedView.root);
    }

    protected void handleLogoutToLogin(ActionEvent event) throws IOException {
        NetworkPushManager.getInstance().stop();
        if (NetworkRequestClient.isEnabled()) {
            NetworkRequestClient.logout();
        } else {
            SessionManager.clear();
        }

        Stage logoutStage = getEventStage(event);
        Stage ownerStage = getOwnerStage(logoutStage);
        Stage targetStage = ownerStage != null ? ownerStage : logoutStage;

        LoadedView loadedView = loadView(LOGIN);
        cleanupStageController(targetStage);
        applyScene(targetStage, loadedView.root);

        if (logoutStage != targetStage) {
            logoutStage.close();
        }
    }

    protected void applyCurrentUserQuickInfo(Label label) {
        if (label == null) {
            return;
        }

        User currentUser = SessionManager.getCurrentUser();
        if (currentUser == null) {
            label.setText("Guest");
            return;
        }

        try {
            WalletDTO wallet = profileService.getWallet(currentUser.getUsername());
            NumberFormat format = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
            label.setText(currentUser.getUsername() + " | Available: " + format.format(wallet.getAvailableBalance()));
        } catch (Exception e) {
            label.setText(currentUser.getUsername());
        }
    }

    protected void setupNotificationButton(Button button) {
        if (button == null) {
            return;
        }
        Label badge = ensureNotificationBadge(button);
        refreshNotificationBadge(button, badge);
        button.setOnAction(event -> showNotificationPopup(button));
    }

    protected void setupSearchButton(Button button) {
        if (button == null) {
            return;
        }
        button.setOnAction(event -> showSearchPopup(button));
    }

    protected void registerForPushUpdates() {
        NetworkPushManager.getInstance().register(this);
    }

    protected void unregisterFromPushUpdates() {
        NetworkPushManager.getInstance().unregister(this);
    }

    protected void refreshNotificationBadge(Button button) {
        if (button == null) {
            return;
        }
        Object badge = button.getProperties().get(NOTIFICATION_BADGE_KEY);
        if (badge instanceof Label) {
            refreshNotificationBadge(button, (Label) badge);
        }
    }

    private Label ensureNotificationBadge(Button button) {
        Object existing = button.getProperties().get(NOTIFICATION_BADGE_KEY);
        if (existing instanceof Label) {
            return (Label) existing;
        }

        Node originalGraphic = button.getGraphic();
        StackPane wrapper = new StackPane();
        if (originalGraphic != null) {
            wrapper.getChildren().add(originalGraphic);
        }

        Label badge = new Label();
        badge.setMinSize(16, 16);
        badge.setPrefSize(16, 16);
        badge.setAlignment(Pos.CENTER);
        badge.setStyle("-fx-background-color: #e02424; -fx-background-radius: 999; "
                + "-fx-text-fill: white; -fx-font-size: 9px; -fx-font-weight: 700;");
        StackPane.setAlignment(badge, Pos.TOP_RIGHT);
        badge.setTranslateX(7);
        badge.setTranslateY(-6);
        wrapper.getChildren().add(badge);
        button.setGraphic(wrapper);
        button.getProperties().put(NOTIFICATION_BADGE_KEY, badge);
        return badge;
    }

    private void refreshNotificationBadge(Button button, Label badge) {
        User currentUser = SessionManager.getCurrentUser();
        int unread = currentUser == null ? 0 : notificationClientService.countUnread(currentUser.getUsername());
        badge.setVisible(unread > 0);
        badge.setManaged(unread > 0);
        badge.setText(unread > 9 ? "9+" : String.valueOf(unread));
    }

    private void showNotificationPopup(Button anchor) {
        User currentUser = SessionManager.getCurrentUser();
        if (currentUser == null) {
            return;
        }

        NotificationPopup popup = new NotificationPopup(
                notificationClientService,
                currentUser.getUsername(),
                notification -> openNotificationTarget(anchor, notification),
                () -> refreshNotificationBadge(anchor));
        popup.show(anchor);
    }

    private void showSearchPopup(Button anchor) {
        HeaderSearchPopup popup = new HeaderSearchPopup(
                searchService,
                row -> {
                    try {
                        switchToBiddingDetails(new ActionEvent(anchor, anchor), row.getAuctionId());
                    } catch (IOException e) {
                        System.err.println("[NavigationController] Cannot open search result: " + e.getMessage());
                    }
                });
        popup.show(anchor);
    }

    private void openNotificationTarget(Button anchor, NotificationDTO notification) {
        try {
            ActionEvent event = new ActionEvent(anchor, anchor);
            if (NotificationApplicationService.ACTION_AUCTION_DETAIL.equals(notification.getActionTarget())
                    && notification.getAuctionId() != null) {
                switchToBiddingDetails(event, notification.getAuctionId());
                return;
            }
            if (NotificationApplicationService.ACTION_MY_BIDS.equals(notification.getActionTarget())) {
                switchToMyBids(event);
            }
        } catch (IOException e) {
            System.err.println("[NavigationController] Cannot open notification target: " + e.getMessage());
        }
    }

    private void replaceCurrentScene(Stage stage, Parent root) {
        onBeforeNavigate();
        applyScene(stage, root);
    }

    @Override
    protected void onBeforeNavigate() {
        unregisterFromPushUpdates();
    }

    private void applyScene(Stage stage, Parent root) {
        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.sizeToScene();
        if (!stage.isShowing()) {
            stage.show();
        }
        stage.centerOnScreen();

        Platform.runLater(() -> {
            stage.sizeToScene();
            stage.centerOnScreen();
        });
    }

    private LoadedView loadView(String classpathFXML) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(classpathFXML));
        Parent root = loader.load();
        Object controller = loader.getController();
        root.getProperties().put(CONTROLLER_KEY, controller);
        return new LoadedView(root, controller);
    }

    private Stage getEventStage(ActionEvent event) {
        return (Stage) ((Node) event.getSource()).getScene().getWindow();
    }

    private Stage getOwnerStage(Stage stage) {
        Window owner = stage.getOwner();
        return owner instanceof Stage ? (Stage) owner : null;
    }

    private void cleanupStageController(Stage stage) {
        if (stage == null || stage.getScene() == null || stage.getScene().getRoot() == null) {
            return;
        }

        Object controller = stage.getScene().getRoot().getProperties().get(CONTROLLER_KEY);
        if (controller instanceof BaseController) {
            ((BaseController) controller).onBeforeNavigate();
        }
    }

    private static final class LoadedView {
        private final Parent root;
        private final Object controller;

        private LoadedView(Parent root, Object controller) {
            this.root = root;
            this.controller = controller;
        }
    }
}
