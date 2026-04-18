package Client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.image.Image;
import javafx.scene.text.Font;

public class ClientApp extends Application {
    public static void main (String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        try {
            Font.loadFont(getClass().getResourceAsStream("/css/fonts/SVN-Canopee.otf"), 10);
            Font.loadFont(getClass().getResourceAsStream("/css/fonts/SpaceMono-Regular.ttf"), 10);
            
            Parent root = FXMLLoader.load(getClass().getResource("/views/bidding_detail.fxml"));
            Scene scene = new Scene(root);
            String css = this.getClass().getResource("/css/dashboard.css").toExternalForm();
            scene.getStylesheets().add(css);

            stage.setResizable(false);
            Image icon = new Image("/images/logo.png");
            stage.getIcons().add(icon);
            stage.setTitle("Giấu đá trực tuyến");

            stage.setScene(scene);
            stage.centerOnScreen();
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}