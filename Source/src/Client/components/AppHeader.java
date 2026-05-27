package Client.components;

import Client.core.ui.NavigationController;
import Client.features.auth.SessionManager;
import Client.features.notifications.NotificationClientService;
import Client.features.profile.ProfileService;
import Client.features.search.SearchService;
import Client.navigation.NavigationService;
import CommonClasses.User;
import CommonClasses.dto.NotificationDTO;
import CommonClasses.dto.WalletDTO;
import Server.service.NotificationApplicationService;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.Locale;

public class AppHeader extends HBox {
    private static final String NOTIFICATION_BADGE_KEY = "notification.badge";

    private final NotificationClientService notificationClientService = new NotificationClientService();
    private final SearchService searchService = new SearchService();
    private final ProfileService profileService = new ProfileService();
    private final NavigationService navigationService = new NavigationService();

    private final Button dashboardButton = new Button("Browse Auctions");
    private final Button myBidsButton = new Button("My Bids");
    private final Button sellItemButton = new Button("Sell Item");
    private final Label currentUserQuickInfoLabel = new Label("Guest");
    private final Button searchButton = new Button();
    private final Button notificationsButton = new Button();
    private final Button profileButton = new Button();

    private boolean configured;
    private String activePage;

    public AppHeader() {
        build();
    }

    public void configure(NavigationController owner) {
        if (!configured) {
            wireActions();
            configured = true;
        }
        refreshWalletQuickInfo();
        refreshNotificationBadge();
    }

    public void setActivePage(String activePage) {
        this.activePage = activePage;
        updateActiveNavigation();
    }

    public String getActivePage() {
        return activePage;
    }

    public void refreshWalletQuickInfo() {
        User currentUser = SessionManager.getCurrentUser();
        if (currentUser == null) {
            currentUserQuickInfoLabel.setText("Guest");
            return;
        }

        try {
            WalletDTO wallet = profileService.getWallet(currentUser.getUsername());
            NumberFormat format = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
            currentUserQuickInfoLabel.setText(currentUser.getUsername()
                    + " | Available: " + format.format(wallet.getAvailableBalance()));
        } catch (Exception e) {
            currentUserQuickInfoLabel.setText(currentUser.getUsername());
        }
    }

    public void refreshNotificationBadge() {
        Label badge = ensureNotificationBadge();
        User currentUser = SessionManager.getCurrentUser();
        int unread = currentUser == null ? 0 : notificationClientService.countUnread(currentUser.getUsername());
        badge.setVisible(unread > 0);
        badge.setManaged(unread > 0);
        badge.setText(unread > 9 ? "9+" : String.valueOf(unread));
    }

    private void build() {
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(20);
        getStyleClass().add("header");

        HBox brand = new HBox(10);
        brand.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(brand, Priority.ALWAYS);
        Region logo = new Region();
        logo.setMinSize(40, 40);
        logo.setPrefSize(40, 40);
        logo.getStyleClass().add("logo");
        Label title = new Label("Bidify");
        title.getStyleClass().add("app-title");
        brand.getChildren().addAll(logo, title);

        HBox navigation = new HBox(8);
        navigation.setAlignment(Pos.CENTER);
        HBox.setHgrow(navigation, Priority.ALWAYS);
        dashboardButton.getStyleClass().add("nav-button");
        myBidsButton.getStyleClass().add("nav-button");
        sellItemButton.getStyleClass().add("nav-button");
        navigation.getChildren().addAll(dashboardButton, myBidsButton, sellItemButton);

        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_RIGHT);
        currentUserQuickInfoLabel.getStyleClass().add("auth-footer-muted");
        searchButton.getStyleClass().add("icon-button");
        searchButton.setGraphic(imageIcon("/client/images/search.png"));
        notificationsButton.getStyleClass().add("icon-button-badge");
        notificationsButton.setGraphic(imageIcon("/client/images/notification.png"));
        profileButton.getStyleClass().add("icon-button");
        profileButton.setGraphic(imageIcon("/client/images/user.png"));
        actions.getChildren().addAll(currentUserQuickInfoLabel, searchButton, notificationsButton, profileButton);

        getChildren().addAll(brand, navigation, actions);
        updateActiveNavigation();
    }

    private ImageView imageIcon(String imagePath) {
        ImageView imageView = new ImageView(new Image(imagePath));
        imageView.setFitWidth(24);
        imageView.setFitHeight(24);
        imageView.setPreserveRatio(true);
        return imageView;
    }

    private void wireActions() {
        dashboardButton.setOnAction(event -> navigate(event, () -> navigationService.openDashboard(event)));
        myBidsButton.setOnAction(event -> navigate(event, () -> navigationService.openMyBids(event)));
        sellItemButton.setOnAction(event -> navigate(event, () -> navigationService.openSellItem(event)));
        searchButton.setOnAction(event -> showSearchPopup());
        notificationsButton.setOnAction(event -> showNotificationPopup());
        profileButton.setOnAction(event -> navigate(event, () -> navigationService.openProfile(event)));
    }

    private void navigate(ActionEvent event, NavigationAction action) {
        if (action == null) {
            return;
        }
        try {
            action.run();
        } catch (IOException e) {
            System.err.println("[AppHeader] Navigation failed: " + e.getMessage());
        }
    }

    private void showSearchPopup() {
        HeaderSearchPopup popup = new HeaderSearchPopup(
                searchService,
                row -> {
                    try {
                        navigationService.openBiddingDetail(
                                new ActionEvent(searchButton, searchButton),
                                row.getAuctionId());
                    } catch (IOException e) {
                        System.err.println("[AppHeader] Cannot open search result: " + e.getMessage());
                    }
                });
        popup.show(searchButton);
    }

    private void showNotificationPopup() {
        User currentUser = SessionManager.getCurrentUser();
        if (currentUser == null) {
            return;
        }

        NotificationPopup popup = new NotificationPopup(
                notificationClientService,
                currentUser.getUsername(),
                this::openNotificationTarget,
                this::refreshNotificationBadge);
        popup.show(notificationsButton);
    }

    private void openNotificationTarget(NotificationDTO notification) {
        try {
                ActionEvent event = new ActionEvent(notificationsButton, notificationsButton);
            if (NotificationApplicationService.ACTION_AUCTION_DETAIL.equals(notification.getActionTarget())
                    && notification.getAuctionId() != null) {
                navigationService.openBiddingDetail(event, notification.getAuctionId());
                return;
            }
            if (NotificationApplicationService.ACTION_MY_BIDS.equals(notification.getActionTarget())) {
                navigationService.openMyBids(event);
            }
        } catch (IOException e) {
            System.err.println("[AppHeader] Cannot open notification target: " + e.getMessage());
        }
    }

    private Label ensureNotificationBadge() {
        Object existing = notificationsButton.getProperties().get(NOTIFICATION_BADGE_KEY);
        if (existing instanceof Label) {
            return (Label) existing;
        }

        Node originalGraphic = notificationsButton.getGraphic();
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
        notificationsButton.setGraphic(wrapper);
        notificationsButton.getProperties().put(NOTIFICATION_BADGE_KEY, badge);
        return badge;
    }

    private void updateActiveNavigation() {
        applyActiveState(dashboardButton, "DASHBOARD");
        applyActiveState(myBidsButton, "MY_BIDS");
        applyActiveState(sellItemButton, "SELL_ITEM");
    }

    private void applyActiveState(Button button, String page) {
        button.getStyleClass().remove("nav-button-active");
        if (page.equalsIgnoreCase(activePage)) {
            button.getStyleClass().add("nav-button-active");
        }
    }

    @FunctionalInterface
    private interface NavigationAction {
        void run() throws IOException;
    }
}
