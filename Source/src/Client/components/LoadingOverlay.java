package Client.components;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.stage.Popup;
import javafx.stage.Window;

public class LoadingOverlay {
    private final Popup popup = new Popup();
    private final Label messageLabel = new Label("Loading...");

    public LoadingOverlay() {
        ProgressIndicator indicator = new ProgressIndicator();
        indicator.setMaxSize(24, 24);

        messageLabel.setStyle("-fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: 700;");

        HBox content = new HBox(10, indicator, messageLabel);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(12, 16, 12, 16));
        content.setStyle("-fx-background-color: rgba(18,18,18,0.92); -fx-background-radius: 8; "
                + "-fx-border-color: rgba(255,255,255,0.12); -fx-border-radius: 8;");

        popup.setAutoFix(true);
        popup.getContent().add(content);
    }

    public void show(Node owner, String message) {
        if (owner == null || owner.getScene() == null) {
            return;
        }
        Window window = owner.getScene().getWindow();
        if (window == null) {
            return;
        }
        messageLabel.setText(message == null || message.isBlank() ? "Loading..." : message);
        if (!popup.isShowing()) {
            popup.show(window);
        }
        reposition(window);
    }

    public void hide() {
        if (popup.isShowing()) {
            popup.hide();
        }
    }

    private void reposition(Window window) {
        Platform.runLater(() -> {
            double x = window.getX() + (window.getWidth() - popup.getWidth()) / 2;
            double y = window.getY() + Math.max(80, window.getHeight() * 0.22);
            popup.setX(x);
            popup.setY(y);
        });
    }
}
