package Client.app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class ClientApp extends Application {

    private static final String LOGIN_FXML = "/Client/views/auth/login.fxml";

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        try {
            Font.loadFont(getClass().getResourceAsStream("/Client/fonts/SVN-Canopee.otf"), 10);
            Font.loadFont(getClass().getResourceAsStream("/Client/fonts/SpaceMono-Regular.ttf"), 10);
            Font.loadFont(getClass().getResourceAsStream("/Client/fonts/Gotham-Black.otf"), 10);

            stage.initStyle(StageStyle.UNDECORATED);

            Parent root = FXMLLoader.load(getClass().getResource(LOGIN_FXML));
            Scene scene = new Scene(root);

            stage.setResizable(false);
            Image icon = new Image("/Client/images/logo.png");
            stage.getIcons().add(icon);
            stage.setTitle("Bidify Online Auction");

            stage.setScene(scene);
            stage.centerOnScreen();
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
