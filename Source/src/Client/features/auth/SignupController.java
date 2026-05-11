package Client.features.auth;

import Client.core.ui.NavigationController;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class SignupController extends NavigationController {

    private final AuthService authService = new AuthService();

    @FXML private TextField usernameField;
    @FXML private TextField emailField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label errorLabel;

    @FXML
    public void initialize() {
        errorLabel.setText("");
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);
    }

    @FXML
    private void handleSignup(ActionEvent event) {
        errorLabel.setText("");
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);

        String user = usernameField.getText() != null ? usernameField.getText().trim() : "";
        String email = emailField.getText() != null ? emailField.getText().trim() : "";
        String p1 = newPasswordField.getText() != null ? newPasswordField.getText() : "";
        String p2 = confirmPasswordField.getText() != null ? confirmPasswordField.getText() : "";

        if (user.isEmpty() || email.isEmpty() || p1.isEmpty() || p2.isEmpty()) {
            showError("Please fill in all fields.");
            return;
        }
        if (!authService.passwordsMatch(p1, p2)) {
            showError("Passwords do not match.");
            return;
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setManaged(true);
        errorLabel.setVisible(true);
    }
}
