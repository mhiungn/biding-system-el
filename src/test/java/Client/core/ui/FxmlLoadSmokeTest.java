package Client.core.ui;

import Packets.NetworkConfig;
import Server.dao.AuctionDAO;
import Server.dao.BidTransactionDAO;
import Server.dao.ItemDAO;
import Server.dao.NotificationDAO;
import Server.dao.TestDatabaseHelper;
import Server.dao.UserDAO;
import Server.dao.WalletDAO;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class FxmlLoadSmokeTest {
    private static final String[] FXML_VIEWS = {
            "/client/views/auth/login.fxml",
            "/client/views/auth/signup.fxml",
            "/client/views/dashboard/dashboard.fxml",
            "/client/views/bidding/mybids.fxml",
            "/client/views/bidding/bidding_detail.fxml",
            "/client/views/sell/sell_item.fxml",
            "/client/views/profile/user_profile.fxml"
    };

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
    void importantFxmlViewsLoadAndCreateControllers() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            for (String fxml : FXML_VIEWS) {
                URL resource = FxmlLoadSmokeTest.class.getResource(fxml);
                assertNotNull(resource, "Missing FXML resource: " + fxml);

                FXMLLoader loader = new FXMLLoader(resource);
                Parent root = loader.load();

                assertNotNull(root, "FXML root should load: " + fxml);
                assertNotNull(loader.getController(), "FXML controller should be created: " + fxml);
            }
            return null;
        });
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
