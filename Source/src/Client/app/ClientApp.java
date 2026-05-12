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

    private static final String LOGIN_FXML = "/client/views/profile/user_profile.fxml";

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        try {
            Font.loadFont(getClass().getResourceAsStream("/client/fonts/SVN-Canopee.otf"), 10);
            Font.loadFont(getClass().getResourceAsStream("/client/fonts/SpaceMono-Regular.ttf"), 10);
            Font.loadFont(getClass().getResourceAsStream("/client/fonts/Gotham-Black.otf"), 10);

            stage.initStyle(StageStyle.UNDECORATED);

            Parent root = FXMLLoader.load(getClass().getResource(LOGIN_FXML));
            Scene scene = new Scene(root);

            stage.setResizable(false);
            Image icon = new Image("/client/images/logo.png");
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
