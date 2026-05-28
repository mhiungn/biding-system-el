package Client.navigation;

import Client.core.network.NetworkRequestClient;
import Client.core.network.NetworkPushManager;
import Client.core.ui.BaseController;
import Client.core.ui.RefreshablePage;
import Client.features.auth.SessionManager;
import Client.features.bidding.BiddingDetailController;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class NavigationService {
    private static final String CONTROLLER_KEY = "client.controller";
    private static final Map<String, LoadedView> VIEW_CACHE = new HashMap<>();

    private static final String DASHBOARD = "/client/views/dashboard/dashboard.fxml";
    private static final String BIDDING_DETAIL = "/client/views/bidding/bidding_detail.fxml";
    private static final String MY_BIDS = "/client/views/bidding/mybids.fxml";
    private static final String SIGNUP = "/client/views/auth/signup.fxml";
    private static final String LOGIN = "/client/views/auth/login.fxml";
    private static final String USER_PROFILE = "/client/views/profile/user_profile.fxml";
    private static final String SELL_ITEM = "/client/views/sell/sell_item.fxml";

    public void openDashboard(ActionEvent event) throws IOException {
        switchScene(event, DASHBOARD);
    }

    public void openSellItem(ActionEvent event) throws IOException {
        switchScene(event, SELL_ITEM);
    }

    public void openBiddingDetail(ActionEvent event) throws IOException {
        switchScene(event, BIDDING_DETAIL);
    }

    public void openBiddingDetail(ActionEvent event, long auctionId) throws IOException {
        LoadedView loadedView = loadView(BIDDING_DETAIL);
        Stage stage = getEventStage(event);
        replaceCurrentScene(stage, loadedView);

        if (loadedView.controller instanceof BiddingDetailController) {
            ((BiddingDetailController) loadedView.controller).setAuctionId(Math.toIntExact(auctionId));
        }
    }

    public void openMyBids(ActionEvent event) throws IOException {
        switchScene(event, MY_BIDS);
    }

    public void openSignup(ActionEvent event) throws IOException {
        switchScene(event, SIGNUP);
    }

    public void openLogin(ActionEvent event) throws IOException {
        switchScene(event, LOGIN);
    }

    public void openProfile(ActionEvent event) throws IOException {
        LoadedView loadedView = loadView(USER_PROFILE);

        Stage profileStage = new Stage();
        profileStage.setTitle("User Profile");
        profileStage.initOwner(getEventStage(event));
        profileStage.initModality(Modality.WINDOW_MODAL);
        profileStage.initStyle(StageStyle.UNDECORATED);
        profileStage.setScene(loadedView.scene);
        profileStage.setResizable(false);
        profileStage.show();
        profileStage.sizeToScene();
        profileStage.centerOnScreen();
        notifyAfterShow(loadedView);
    }

    public void logoutToLogin(ActionEvent event) throws IOException {
        NetworkPushManager.getInstance().stop();
        if (NetworkRequestClient.isEnabled()) {
            NetworkRequestClient.logout();
        } else {
            SessionManager.clear();
        }

        Stage logoutStage = getEventStage(event);
        Stage ownerStage = getOwnerStage(logoutStage);
        Stage targetStage = ownerStage != null ? ownerStage : logoutStage;

        LoadedView loadedView = loadCachedView(LOGIN);
        cleanupStageController(targetStage);
        applyScene(targetStage, loadedView);

        if (logoutStage != targetStage) {
            logoutStage.close();
        }
    }

    private void switchScene(ActionEvent event, String classpathFXML) throws IOException {
        LoadedView loadedView = loadCachedView(classpathFXML);
        Stage stage = getEventStage(event);
        replaceCurrentScene(stage, loadedView);
    }

    private void replaceCurrentScene(Stage stage, LoadedView loadedView) {
        cleanupStageController(stage);
        applyScene(stage, loadedView);
    }

    private void applyScene(Stage stage, LoadedView loadedView) {
        stage.setScene(loadedView.scene);
        stage.sizeToScene();
        if (!stage.isShowing()) {
            stage.show();
        }
        stage.centerOnScreen();
        notifyAfterShow(loadedView);

        Platform.runLater(() -> {
            stage.sizeToScene();
            stage.centerOnScreen();
        });
    }

    private LoadedView loadCachedView(String classpathFXML) throws IOException {
        if (!isCacheable(classpathFXML)) {
            return loadView(classpathFXML);
        }
        LoadedView cached = VIEW_CACHE.get(classpathFXML);
        if (cached != null) {
            return cached;
        }
        LoadedView loadedView = loadView(classpathFXML);
        VIEW_CACHE.put(classpathFXML, loadedView);
        return loadedView;
    }

    private LoadedView loadView(String classpathFXML) throws IOException {
        FXMLLoader loader = new FXMLLoader(NavigationService.class.getResource(classpathFXML));
        Parent root = loader.load();
        Object controller = loader.getController();
        root.getProperties().put(CONTROLLER_KEY, controller);
        return new LoadedView(controller, new Scene(root));
    }

    private boolean isCacheable(String classpathFXML) {
        return DASHBOARD.equals(classpathFXML)
                || MY_BIDS.equals(classpathFXML)
                || SELL_ITEM.equals(classpathFXML)
                || SIGNUP.equals(classpathFXML)
                || LOGIN.equals(classpathFXML);
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
            ((BaseController) controller).beforeExternalNavigation();
        }
    }

    private void notifyAfterShow(LoadedView loadedView) {
        if (loadedView.controller instanceof BaseController) {
            ((BaseController) loadedView.controller).afterExternalNavigation();
        }
        if (loadedView.controller instanceof RefreshablePage) {
            ((RefreshablePage) loadedView.controller).onPageShown();
        }
    }

    private static final class LoadedView {
        private final Object controller;
        private final Scene scene;

        private LoadedView(Object controller, Scene scene) {
            this.controller = controller;
            this.scene = scene;
        }
    }
}
