package Client.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.*;

public class LoginController extends Controller{
    @FXML private PasswordField hiddenPasswordField;
    @FXML private TextField visiblePasswordField;
    @FXML private CheckBox showPasswordCheckBox;

    @FXML
    public void initialize() {
        // sync 2 layer password
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
        visiblePasswordField.end(); // Move cursor to end
    }

    private void showPasswordField() {
        hiddenPasswordField.setVisible(true);
        hiddenPasswordField.setManaged(true);
        visiblePasswordField.setVisible(false);
        visiblePasswordField.setManaged(false);
        hiddenPasswordField.requestFocus();
        hiddenPasswordField.end(); // Move cursor to end
    }
}
