package Client.core.ui;

import Client.core.network.NetworkPushManager;
import Client.core.network.PushEventListener;
import Client.navigation.NavigationService;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;

import java.io.IOException;

/**
 * Compatibility base for FXML controllers that still expose navigation handlers.
 */
public abstract class NavigationController extends BaseController implements PushEventListener {
    protected final NavigationService navigationService = new NavigationService();

    public void switchToDashboard(ActionEvent event) throws IOException {
        navigationService.openDashboard(event);
    }

    public void switchToSellItem(ActionEvent event) throws IOException {
        navigationService.openSellItem(event);
    }

    public void switchToBiddingDetails(ActionEvent event) throws IOException {
        navigationService.openBiddingDetail(event);
    }

    public void switchToBiddingDetails(ActionEvent event, int auctionId) throws IOException {
        navigationService.openBiddingDetail(event, auctionId);
    }

    public void switchToMyBids(ActionEvent event) throws IOException {
        navigationService.openMyBids(event);
    }

    public void switchToSignup(ActionEvent event) throws IOException {
        navigationService.openSignup(event);
    }

    public void switchToLogin(ActionEvent event) throws IOException {
        navigationService.openLogin(event);
    }

    @FXML
    public void openUserProfile(ActionEvent event) throws IOException {
        navigationService.openProfile(event);
    }

    protected void handleLogoutToLogin(ActionEvent event) throws IOException {
        navigationService.logoutToLogin(event);
    }

    protected void registerForPushUpdates() {
        NetworkPushManager.getInstance().register(this);
    }

    protected void unregisterFromPushUpdates() {
        NetworkPushManager.getInstance().unregister(this);
    }

    @Override
    protected void onBeforeNavigate() {
        unregisterFromPushUpdates();
    }
}
