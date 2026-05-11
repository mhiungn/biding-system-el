package Client.core.ui;

import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

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

    public void switchToDashboard(ActionEvent event) throws IOException {
        switchScene(event, DASHBOARD);
    }

    public void switchToBiddingDetails(ActionEvent event) throws IOException {
        switchScene(event, BIDDING_DETAIL);
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

    private void switchScene(ActionEvent event, String classpathFXML) throws IOException {
        Parent root = FXMLLoader.load(getClass().getResource(classpathFXML));
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();
    }
}
