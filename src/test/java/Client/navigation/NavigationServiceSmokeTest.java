package Client.navigation;

import Client.core.ui.FxTestSupport;
import Packets.NetworkConfig;
import Server.dao.AuctionDAO;
import Server.dao.BidTransactionDAO;
import Server.dao.ItemDAO;
import Server.dao.NotificationDAO;
import Server.dao.TestDatabaseHelper;
import Server.dao.UserDAO;
import Server.dao.WalletDAO;
import javafx.event.ActionEvent;
import javafx.event.EventTarget;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NavigationServiceSmokeTest {
    private static String previousNetworkEnabled;

    @BeforeAll
    static void setUpAll() throws Exception {
        previousNetworkEnabled = System.getProperty(NetworkConfig.NETWORK_ENABLED_PROPERTY);
        System.setProperty(NetworkConfig.NETWORK_ENABLED_PROPERTY, "false");
        TestDatabaseHelper.redirectToH2();
        TestDatabaseHelper.createAllTables();
        resetPersistenceSingletons();
        clearSession();
    }

    @AfterAll
    static void tearDownAll() throws Exception {
        clearSession();
        if (previousNetworkEnabled == null) {
            System.clearProperty(NetworkConfig.NETWORK_ENABLED_PROPERTY);
        } else {
            System.setProperty(NetworkConfig.NETWORK_ENABLED_PROPERTY, previousNetworkEnabled);
        }
    }

    @Test
    void cacheableMainRoutesOpenWithoutThrowing() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            Object navigation = Class.forName("Client.navigation.NavigationService")
                    .getDeclaredConstructor()
                    .newInstance();
            Stage stage = createStageWithEventSource();

            assertDoesNotThrow(() -> invokeNavigation(navigation, "openLogin", eventFor(stage)));
            assertSceneLoaded(stage);
            assertDoesNotThrow(() -> invokeNavigation(navigation, "openSignup", eventFor(stage)));
            assertSceneLoaded(stage);
            assertDoesNotThrow(() -> invokeNavigation(navigation, "openDashboard", eventFor(stage)));
            assertSceneLoaded(stage);
            assertDoesNotThrow(() -> invokeNavigation(navigation, "openMyBids", eventFor(stage)));
            assertSceneLoaded(stage);
            assertDoesNotThrow(() -> invokeNavigation(navigation, "openSellItem", eventFor(stage)));
            assertSceneLoaded(stage);

            stage.close();
            return null;
        });
    }

    private static Stage createStageWithEventSource() {
        Button source = new Button("source");
        Stage stage = new Stage();
        stage.setScene(new Scene(source));
        stage.show();
        return stage;
    }

    private static void assertSceneLoaded(Stage stage) {
        assertNotNull(stage.getScene(), "Navigation should leave the stage with a scene.");
        assertNotNull(stage.getScene().getRoot(), "Navigation should leave the stage with a root.");
    }

    private static ActionEvent eventFor(Stage stage) {
        EventTarget source = stage.getScene().getRoot();
        return new ActionEvent(source, source);
    }

    private static void invokeNavigation(Object navigation, String methodName, ActionEvent event) throws Exception {
        navigation.getClass()
                .getMethod(methodName, ActionEvent.class)
                .invoke(navigation, event);
    }

    private static void resetPersistenceSingletons() throws Exception {
        for (Class<?> singleton : Stream.of(
                UserDAO.class,
                ItemDAO.class,
                AuctionDAO.class,
                WalletDAO.class,
                NotificationDAO.class,
                BidTransactionDAO.class).toList()) {
            TestDatabaseHelper.resetSingleton(singleton);
        }
    }

    private static void clearSession() throws Exception {
        Class.forName("Client.features.auth.SessionManager")
                .getMethod("clear")
                .invoke(null);
    }
}
