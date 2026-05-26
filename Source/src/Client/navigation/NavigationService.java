package Client.navigation;

import Client.core.network.NetworkRequestClient;
import Client.core.network.NetworkPushManager;
import Client.core.ui.BaseController;
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

public class NavigationService {
    private static final String CONTROLLER_KEY = "client.controller";

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
        replaceCurrentScene(stage, loadedView.root);

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
        profileStage.setScene(new Scene(loadedView.root));
        profileStage.setResizable(false);
        profileStage.show();
        profileStage.sizeToScene();
        profileStage.centerOnScreen();
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

        LoadedView loadedView = loadView(LOGIN);
        cleanupStageController(targetStage);
        applyScene(targetStage, loadedView.root);

        if (logoutStage != targetStage) {
            logoutStage.close();
        }
    }

    private void switchScene(ActionEvent event, String classpathFXML) throws IOException {
        LoadedView loadedView = loadView(classpathFXML);
        Stage stage = getEventStage(event);
        replaceCurrentScene(stage, loadedView.root);
    }

    private void replaceCurrentScene(Stage stage, Parent root) {
        cleanupStageController(stage);
        applyScene(stage, root);
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
        FXMLLoader loader = new FXMLLoader(NavigationService.class.getResource(classpathFXML));
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
            ((BaseController) controller).beforeExternalNavigation();
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
