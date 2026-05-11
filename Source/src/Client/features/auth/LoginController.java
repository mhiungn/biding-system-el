package Client.features.auth;

import Client.core.ui.NavigationController;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class LoginController extends NavigationController {
    @FXML private PasswordField hiddenPasswordField;
    @FXML private TextField visiblePasswordField;
    @FXML private CheckBox showPasswordCheckBox;
    @FXML private Label errorLabel;
    @FXML private Button loginButton;
    @FXML private TextField usernameField;

    @FXML
    public void initialize() {
        visiblePasswordField.textProperty().bindBidirectional(hiddenPasswordField.textProperty());
    }

    @FXML
    private void togglePasswordVisibility() {
        if (visiblePasswordField.isVisible()) {
            showPasswordField();
            showPasswordCheckBox.setText("Show Password");
        } else {
            showvisiblePasswordField();
            showPasswordCheckBox.setText("Hide Password");
        }
    }

    private void showvisiblePasswordField() {
        visiblePasswordField.setVisible(true);
        visiblePasswordField.setManaged(true);
        hiddenPasswordField.setVisible(false);
        hiddenPasswordField.setManaged(false);
        visiblePasswordField.requestFocus();
        visiblePasswordField.end();
    }

    private void showPasswordField() {
        hiddenPasswordField.setVisible(true);
        hiddenPasswordField.setManaged(true);
        visiblePasswordField.setVisible(false);
        visiblePasswordField.setManaged(false);
        hiddenPasswordField.requestFocus();
        hiddenPasswordField.end();
    }
}
