package Client.core.ui;

import javafx.fxml.FXML;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

/**
 * Custom undecorated window: drag region and window controls.
 */
public abstract class BaseController {
    @FXML
    protected HBox titleBar;

    private double dragOffsetX;
    private double dragOffsetY;

    @FXML
    protected void onTitleBarPressed(MouseEvent e) {
        Stage stage = getStage();
        if (stage != null) {
            dragOffsetX = stage.getX() - e.getScreenX();
            dragOffsetY = stage.getY() - e.getScreenY();
        }
    }

    @FXML
    protected void onTitleBarDragged(MouseEvent e) {
        Stage stage = getStage();
        if (stage != null) {
            stage.setX(e.getScreenX() + dragOffsetX);
            stage.setY(e.getScreenY() + dragOffsetY);
        }
    }

    @FXML
    protected void handleMinimize() {
        Stage stage = getStage();
        if (stage != null) {
            stage.setIconified(true);
        }
    }

    @FXML
    protected void handleMaximize() {
        Stage stage = getStage();
        if (stage != null) {
            stage.setMaximized(!stage.isMaximized());
        }
    }

    @FXML
    protected void handleClose() {
        if (onBeforeClose()) {
            onBeforeNavigate();
            Stage stage = getStage();
            if (stage != null) {
                stage.close();
            }
        }
    }

    protected Stage getStage() {
        if (titleBar != null && titleBar.getScene() != null) {
            return (Stage) titleBar.getScene().getWindow();
        }
        return null;
    }

    protected boolean onBeforeClose() {
        return true;
    }

    protected void onBeforeNavigate() {
    }
}
