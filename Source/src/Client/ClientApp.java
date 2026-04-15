package Client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.image.Image;

import javax.swing.*;
import java.awt.*;

public class ClientApp extends Application {
    public static void main (String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/views/main_view.fxml"));
            Scene scene = new Scene(root);
//          scene.getStylesheets().add(getClass().getResource("/css/main_style.css").toExternalForm());
            String css = this.getClass().getResource("/css/main_style.css").toExternalForm();
            scene.getStylesheets().add(css);

            stage.setResizable(false);
            Image icon = new Image("/images/logo.png");
            stage.getIcons().add(icon);
            stage.setTitle("Giấu đá trực tuyến");

            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}