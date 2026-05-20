package Client.features.auth;

import CommonClasses.User;
import javafx.event.ActionEvent;
import Client.core.ui.NavigationController;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import java.io.IOException;

public class LoginController extends NavigationController {
    private final AuthService authService = new AuthService();

    @FXML private PasswordField hiddenPasswordField;
    @FXML private TextField visiblePasswordField;
    @FXML private CheckBox showPasswordCheckBox;
    @FXML private Label errorLabel;
    @FXML private Button loginButton;
    @FXML private TextField usernameField;

    @FXML
    public void initialize() {
        visiblePasswordField.textProperty().bindBidirectional(hiddenPasswordField.textProperty());
        errorLabel.setText("");
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);
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

    @FXML
    private void handleLogin(ActionEvent event) throws IOException {
        clearError();
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String password = hiddenPasswordField.getText() == null ? "" : hiddenPasswordField.getText();

        if (!authService.isNotBlank(username) || !authService.isNotBlank(password)) {
            showError("Username and password cannot be blank.");
            return;
        }

        User loggedIn = authService.login(username, password);
        if (loggedIn == null) {
            showError("Invalid username or password.");
            return;
        }
        showError("Logged in successfully! Loading...");
        switchToDashboard(event);
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setManaged(true);
        errorLabel.setVisible(true);
    }

    private void clearError() {
        errorLabel.setText("");
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);
    }
}
