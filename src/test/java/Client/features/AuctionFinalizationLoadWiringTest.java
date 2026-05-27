package Client.features;

import CommonClasses.Items.Electronics;
import CommonClasses.Items.Item;
import CommonClasses.User;
import CommonClasses.dto.DashboardAuctionRow;
import CommonClasses.dto.DashboardPageResult;
import CommonClasses.dto.NotificationDTO;
import CommonClasses.dto.WalletDTO;
import Packets.NetworkConfig;
import Server.dao.AuctionDAO;
import Server.dao.AuctionSnapshot;
import Server.dao.ItemDAO;
import Server.dao.NotificationDAO;
import Server.dao.TestDatabaseHelper;
import Server.dao.UserDAO;
import Server.dao.WalletDAO;
import Server.service.AuctionFinalizationService;
import Server.service.BiddingApplicationService;
import Server.service.NotificationApplicationService;
import Server.service.WalletApplicationService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AuctionFinalizationLoadWiringTest {
    private static String previousNetworkEnabled;

    private static UserDAO userDAO;
    private static ItemDAO itemDAO;
    private static AuctionDAO auctionDAO;
    private static WalletApplicationService walletService;
    private static BiddingApplicationService biddingService;
    private static NotificationApplicationService notificationService;

    @BeforeAll
    static void setUpAll() throws Exception {
        previousNetworkEnabled = System.getProperty(NetworkConfig.NETWORK_ENABLED_PROPERTY);
        System.setProperty(NetworkConfig.NETWORK_ENABLED_PROPERTY, "false");

        TestDatabaseHelper.redirectToH2();
        TestDatabaseHelper.createAllTables();
        resetSingletons();

        userDAO = UserDAO.getInstance();
        itemDAO = ItemDAO.getInstance();
        auctionDAO = AuctionDAO.getInstance();
        walletService = WalletApplicationService.getInstance();
        biddingService = BiddingApplicationService.getInstance();
        notificationService = NotificationApplicationService.getInstance();
    }

    @AfterAll
    static void tearDownAll() throws Exception {
        TestDatabaseHelper.dropAllTables();
        resetSingletons();
        if (previousNetworkEnabled == null) {
            System.clearProperty(NetworkConfig.NETWORK_ENABLED_PROPERTY);
        } else {
            System.setProperty(NetworkConfig.NETWORK_ENABLED_PROPERTY, previousNetworkEnabled);
        }
    }

    @BeforeEach
    void clearData() throws SQLException {
        TestDatabaseHelper.clearAllTables();
    }

    @Test
    void dashboardLoadFinalizesExpiredAuctionBeforeReturningRows() {
        createUsers();
        createAuction(101, new Date(System.currentTimeMillis() - 1_000L));

        DashboardPageResult result = loadDashboardPage();

        assertEquals(0, result.getTotalItems());
        assertEquals(0, result.getRows().size());
        assertEquals("FINISHED", auctionDAO.findById("101").getStatus());
        assertEquals(1, countNotificationType("seller", "AUCTION_ENDED_NO_BIDS"));
    }

    @Test
    void biddingDetailLoadFinalizesExpiredAuctionBeforeReturningDetail() {
        createUsers();
        createAuction(102, new Date(System.currentTimeMillis() + 86_400_000L));
        walletService.ensureWallet("bidder1");
        walletService.ensureWallet("bidder2");

        biddingService.placeBid("bidder1", 102, 1_100f);
        biddingService.placeBid("bidder2", 102, 1_200f);
        auctionDAO.updateTerminateAt("102", new Date(System.currentTimeMillis() - 1_000L));

        DashboardAuctionRow detail = loadAuctionDetail(102);

        assertNotNull(detail);
        assertEquals("FINISHED", detail.getStatus());
        WalletDTO winnerWallet = walletService.getWallet("bidder2");
        assertEquals(98_800L, winnerWallet.getBalance());
        assertEquals(0L, winnerWallet.getHeldAmount());
        assertEquals(1_200L, winnerWallet.getTotalSpent());
        assertEquals(1, countNotificationType("seller", "AUCTION_SOLD"));
        assertEquals(1, countNotificationType("bidder2", "AUCTION_WON"));
        assertEquals(1, countNotificationType("bidder1", "AUCTION_LOST"));
    }

    @Test
    void repeatedLoadFinalizationDoesNotDuplicateEffects() {
        createUsers();
        createAuction(103, new Date(System.currentTimeMillis() + 86_400_000L));
        walletService.ensureWallet("bidder1");
        walletService.ensureWallet("bidder2");

        biddingService.placeBid("bidder1", 103, 1_100f);
        biddingService.placeBid("bidder2", 103, 1_200f);
        auctionDAO.updateTerminateAt("103", new Date(System.currentTimeMillis() - 1_000L));

        Object detailService = createAuctionDetailService();
        loadAuctionDetail(detailService, 103);
        WalletDTO afterFirstLoad = walletService.getWallet("bidder2");
        loadAuctionDetail(detailService, 103);
        WalletDTO afterSecondLoad = walletService.getWallet("bidder2");

        assertEquals(afterFirstLoad.getBalance(), afterSecondLoad.getBalance());
        assertEquals(afterFirstLoad.getHeldAmount(), afterSecondLoad.getHeldAmount());
        assertEquals(afterFirstLoad.getTotalSpent(), afterSecondLoad.getTotalSpent());
        assertEquals(1, countNotificationType("seller", "AUCTION_SOLD"));
        assertEquals(1, countNotificationType("bidder2", "AUCTION_WON"));
        assertEquals(1, countNotificationType("bidder1", "AUCTION_LOST"));
    }

    private void createUsers() {
        userDAO.save("seller", new User("seller", "pass", "seller@mail.com", "USER"));
        userDAO.save("bidder1", new User("bidder1", "pass", "bidder1@mail.com", "USER"));
        userDAO.save("bidder2", new User("bidder2", "pass", "bidder2@mail.com", "USER"));
        walletService.ensureWallet("seller");
    }

    private void createAuction(int auctionId, Date endTime) {
        Item item = new Electronics(1_000f, "Laptop " + auctionId, "New");
        itemDAO.save("item-" + auctionId, item);

        AuctionSnapshot snapshot = new AuctionSnapshot(
                auctionId,
                "seller",
                new Date(System.currentTimeMillis() - 3_600_000L),
                endTime,
                "Time_Fixed",
                "OPEN",
                item,
                new LinkedList<>(),
                new ArrayList<>(),
                false);
        snapshot.setMinimumBidIncrement(50f);
        auctionDAO.save(String.valueOf(auctionId), snapshot);
    }

    private int countNotificationType(String username, String type) {
        List<NotificationDTO> notifications = notificationService.getRecentNotifications(username);
        return (int) notifications.stream()
                .filter(notification -> type.equals(notification.getType()))
                .count();
    }

    private DashboardPageResult loadDashboardPage() {
        try {
            Object service = Class.forName("Client.features.dashboard.DashboardService")
                    .getDeclaredConstructor()
                    .newInstance();
            Object result = service.getClass()
                    .getMethod("loadAuctionPage", int.class, String.class, boolean.class, Float.class, Float.class)
                    .invoke(service, 0, "ALL", false, null, null);
            return (DashboardPageResult) result;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Cannot invoke DashboardService.loadAuctionPage", e);
        }
    }

    private DashboardAuctionRow loadAuctionDetail(int auctionId) {
        return loadAuctionDetail(createAuctionDetailService(), auctionId);
    }

    private Object createAuctionDetailService() {
        try {
            return Class.forName("Client.features.bidding.AuctionDetailService")
                    .getDeclaredConstructor()
                    .newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Cannot create AuctionDetailService", e);
        }
    }

    private DashboardAuctionRow loadAuctionDetail(Object service, int auctionId) {
        try {
            Object result = service.getClass()
                    .getMethod("loadAuctionDetail", int.class)
                    .invoke(service, auctionId);
            return (DashboardAuctionRow) result;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Cannot invoke AuctionDetailService.loadAuctionDetail", e);
        }
    }

    private static void resetSingletons() throws Exception {
        TestDatabaseHelper.resetSingleton(UserDAO.class);
        TestDatabaseHelper.resetSingleton(ItemDAO.class);
        TestDatabaseHelper.resetSingleton(AuctionDAO.class);
        TestDatabaseHelper.resetSingleton(WalletDAO.class);
        TestDatabaseHelper.resetSingleton(NotificationDAO.class);
        TestDatabaseHelper.resetSingleton(WalletApplicationService.class);
        TestDatabaseHelper.resetSingleton(NotificationApplicationService.class);
        TestDatabaseHelper.resetSingleton(BiddingApplicationService.class);
        TestDatabaseHelper.resetSingleton(AuctionFinalizationService.class);
    }
}
