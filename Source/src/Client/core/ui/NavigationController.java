package Client.core.ui;

import Client.features.bidding.BiddingDetailController;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;

/**
 * Loads other feature FXML roots onto the current {@link Stage}.
 */
public abstract class NavigationController extends BaseController {

    private static final String DASHBOARD = "/client/views/dashboard/dashboard.fxml";
    private static final String BIDDING_DETAIL = "/client/views/bidding/bidding_detail.fxml";
    private static final String MY_BIDS = "/client/views/bidding/mybids.fxml";
    private static final String SIGNUP = "/client/views/auth/signup.fxml";
    private static final String LOGIN = "/client/views/auth/login.fxml";
    private static final String USER_PROFILE = "/client/views/profile/user_profile.fxml";
    private static final String SELL_ITEM = "/client/views/sell/sell_item.fxml";

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
        FXMLLoader loader = new FXMLLoader(getClass().getResource(BIDDING_DETAIL));
        Parent root = loader.load();

        BiddingDetailController controller = loader.getController();
        controller.setAuctionId(auctionId);

        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();
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
        FXMLLoader loader = new FXMLLoader(getClass().getResource(USER_PROFILE));
        Parent root = loader.load();

        Stage profileStage = new Stage();
        profileStage.setTitle("User Profile");
        profileStage.initOwner(((Node) event.getSource()).getScene().getWindow());
        profileStage.initModality(Modality.WINDOW_MODAL);
        profileStage.initStyle(StageStyle.UNDECORATED);
        profileStage.setScene(new Scene(root));
        profileStage.setResizable(false);
        profileStage.show();
    }

    private void switchScene(ActionEvent event, String classpathFXML) throws IOException {
        Parent root = FXMLLoader.load(getClass().getResource(classpathFXML));
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();
    }
}
