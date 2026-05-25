package Server.service;

import CommonClasses.Items.Electronics;
import CommonClasses.Items.Item;
import CommonClasses.User;
import CommonClasses.dto.NotificationDTO;
import Packets.MessageType;
import Packets.PacketMessage;
import Server.dao.AuctionDAO;
import Server.dao.AuctionSnapshot;
import Server.dao.DatabaseConnection;
import Server.dao.ItemDAO;
import Server.dao.NotificationDAO;
import Server.dao.TestDatabaseHelper;
import Server.dao.UserDAO;
import Server.dao.WalletDAO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BiddingApplicationServiceTransactionTest {
    private static UserDAO userDAO;
    private static ItemDAO itemDAO;
    private static AuctionDAO auctionDAO;
    private static WalletApplicationService walletService;
    private static NotificationApplicationService notificationService;
    private static BiddingApplicationService biddingService;
    private List<DeliveredPush> deliveredPushes;

    @BeforeAll
    static void setUpAll() throws Exception {
        TestDatabaseHelper.redirectToH2();
        TestDatabaseHelper.createAllTables();
        resetSingletons();

        userDAO = UserDAO.getInstance();
        itemDAO = ItemDAO.getInstance();
        auctionDAO = AuctionDAO.getInstance();
        walletService = WalletApplicationService.getInstance();
        notificationService = NotificationApplicationService.getInstance();
        biddingService = BiddingApplicationService.getInstance();
    }

    @AfterAll
    static void tearDownAll() throws Exception {
        TestDatabaseHelper.dropAllTables();
        resetSingletons();
    }

    @BeforeEach
    void clearData() throws SQLException {
        TestDatabaseHelper.createAllTables();
        TestDatabaseHelper.clearAllTables();
        deliveredPushes = new CopyOnWriteArrayList<>();
        NetworkPushService.getInstance().setDeliveryOverrideForTests(
                (username, packet) -> deliveredPushes.add(new DeliveredPush(username, packet)));
    }

    @AfterEach
    void clearPushOverride() {
        NetworkPushService.getInstance().clearDeliveryOverrideForTests();
    }

    @Test
    void successfulBidCommitsBidAuctionWalletHoldAndSellerNotification() {
        createFixture(1);

        assertTrue(biddingService.placeBid("bidder1", 1, 1_100f));

        assertEquals(1, auctionDAO.getBidHistoryForAuction(1).size());
        assertEquals("bidder1", auctionDAO.getHighestBidderUsername(1));
        assertEquals(1_100f, currentPrice(1));
        assertEquals("RUNNING", auctionDAO.findById("1").getStatus());
        assertEquals(1_100L, walletService.getWallet("bidder1").getHeldAmount());
        assertEquals(1, countNotification("seller", 1, "NEW_BID_ON_SELLER_ITEM"));
    }

    @Test
    void successfulBidPublishesAuctionNotificationAndWalletPushes() {
        createFixture(10);

        assertTrue(biddingService.placeBid("bidder1", 10, 1_100f));

        assertDeliveredTo("bidder1", MessageType.WALLET_UPDATE_PUSH);
        assertDeliveredTo("seller", MessageType.NOTIFICATION_PUSH);
        assertDelivered(MessageType.AUCTION_UPDATE_PUSH);
    }

    @Test
    void bidBelowMinimumIncrementRollsBackEverything() {
        createFixture(2);

        assertFalse(biddingService.placeBid("bidder1", 2, 1_020f));

        assertNoBidWalletAuctionOrNotificationChange(2, "bidder1");
    }

    @Test
    void bidWithoutEnoughAvailableBalanceRollsBackEverything() {
        createFixture(3);

        assertFalse(biddingService.placeBid("bidder1", 3, 101_000f));

        assertNoBidWalletAuctionOrNotificationChange(3, "bidder1");
    }

    @Test
    void ownerBiddingOnOwnAuctionRollsBackEverything() {
        createFixture(4);

        assertFalse(biddingService.placeBid("seller", 4, 1_100f));

        assertNoBidWalletAuctionOrNotificationChange(4, "seller");
    }

    @Test
    void failedBidCreatesNoNotification() {
        createFixture(5);

        assertFalse(biddingService.placeBid("bidder1", 5, 1_000f));

        assertEquals(0, notificationService.getRecentNotifications("seller").size());
        assertEquals(0, notificationService.getRecentNotifications("bidder1").size());
    }

    @Test
    void successfulOutbidReleasesPreviousHoldAndReservesNewHold() {
        createFixture(6);

        assertTrue(biddingService.placeBid("bidder1", 6, 1_100f));
        assertTrue(biddingService.placeBid("bidder2", 6, 1_200f));

        assertEquals(0L, walletService.getWallet("bidder1").getHeldAmount());
        assertEquals(1_200L, walletService.getWallet("bidder2").getHeldAmount());
        assertEquals("bidder2", auctionDAO.getHighestBidderUsername(6));
        assertEquals(1, countNotification("bidder1", 6, "OUTBID"));
    }

    @Test
    void successfulOutbidPublishesWalletAndNotificationPushesForAffectedUsers() {
        createFixture(11);

        assertTrue(biddingService.placeBid("bidder1", 11, 1_100f));
        deliveredPushes.clear();

        assertTrue(biddingService.placeBid("bidder2", 11, 1_200f));

        assertDeliveredTo("bidder1", MessageType.WALLET_UPDATE_PUSH);
        assertDeliveredTo("bidder2", MessageType.WALLET_UPDATE_PUSH);
        assertDeliveredTo("bidder1", MessageType.NOTIFICATION_PUSH);
        assertDeliveredTo("seller", MessageType.NOTIFICATION_PUSH);
        assertDelivered(MessageType.AUCTION_UPDATE_PUSH);
    }

    @Test
    void sameHighestBidderIncreasingOwnBidUpdatesHoldWithoutOutbidNotification() {
        createFixture(7);

        assertTrue(biddingService.placeBid("bidder1", 7, 1_100f));
        assertTrue(biddingService.placeBid("bidder1", 7, 1_200f));

        assertEquals(1_200L, walletService.getWallet("bidder1").getHeldAmount());
        assertEquals("bidder1", auctionDAO.getHighestBidderUsername(7));
        assertEquals(0, countNotification("bidder1", 7, "OUTBID"));
    }

    @Test
    void notificationFailureRollsBackBidWalletAndAuctionChanges() throws Exception {
        createFixture(8);
        dropNotificationsTable();

        try {
            assertFalse(biddingService.placeBid("bidder1", 8, 1_100f));

            assertEquals(0, auctionDAO.getBidHistoryForAuction(8).size());
            assertEquals(1_000f, currentPrice(8));
            assertEquals("OPEN", auctionDAO.findById("8").getStatus());
            assertEquals(0L, walletService.getWallet("bidder1").getHeldAmount());
        } finally {
            TestDatabaseHelper.createAllTables();
        }

        assertEquals(0, notificationService.getRecentNotifications("seller").size());
    }

    @Test
    void twoConcurrentEqualBidsCannotBothWin() throws Exception {
        createFixture(9);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = executor.submit(() -> placeBidAfterStart("bidder1", 9, 1_100f, ready, start));
            Future<Boolean> second = executor.submit(() -> placeBidAfterStart("bidder2", 9, 1_100f, ready, start));

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            int successes = 0;
            if (first.get(10, TimeUnit.SECONDS)) {
                successes++;
            }
            if (second.get(10, TimeUnit.SECONDS)) {
                successes++;
            }

            assertEquals(1, successes);
            assertEquals(1, auctionDAO.getBidHistoryForAuction(9).size());
            assertEquals(1_100f, currentPrice(9));
            assertNotNull(auctionDAO.getHighestBidderUsername(9));
            assertEquals(1_100L,
                    walletService.getWallet("bidder1").getHeldAmount()
                            + walletService.getWallet("bidder2").getHeldAmount());
        } finally {
            executor.shutdownNow();
        }
    }

    private Boolean placeBidAfterStart(String username, int auctionId, float amount,
                                       CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            return false;
        }
        return biddingService.placeBid(username, auctionId, amount);
    }

    private void createFixture(int auctionId) {
        userDAO.save("seller", new User("seller", "pass", "seller@mail.com", "USER"));
        userDAO.save("bidder1", new User("bidder1", "pass", "bidder1@mail.com", "USER"));
        userDAO.save("bidder2", new User("bidder2", "pass", "bidder2@mail.com", "USER"));
        walletService.ensureWallet("seller");
        walletService.ensureWallet("bidder1");
        walletService.ensureWallet("bidder2");

        Item item = new Electronics(1_000f, "Laptop " + auctionId, "New");
        itemDAO.save("item-" + auctionId, item);

        AuctionSnapshot snapshot = new AuctionSnapshot(
                auctionId,
                "seller",
                new Date(),
                new Date(System.currentTimeMillis() + 86_400_000L),
                "Time_Fixed",
                "OPEN",
                item,
                new LinkedList<>(),
                new ArrayList<>(),
                false);
        snapshot.setMinimumBidIncrement(50f);
        auctionDAO.save(String.valueOf(auctionId), snapshot);
    }

    private float currentPrice(int auctionId) {
        return auctionDAO.findFullAuctionDetail(auctionId).getItem().getCurrentHighestPrice();
    }

    private void assertNoBidWalletAuctionOrNotificationChange(int auctionId, String username) {
        assertEquals(0, auctionDAO.getBidHistoryForAuction(auctionId).size());
        assertEquals(1_000f, currentPrice(auctionId));
        assertEquals("OPEN", auctionDAO.findById(String.valueOf(auctionId)).getStatus());
        assertEquals(0L, walletService.getWallet(username).getHeldAmount());
        assertEquals(0, notificationService.getRecentNotifications("seller").size());
        assertEquals(0, notificationService.getRecentNotifications(username).size());
    }

    private int countNotification(String username, int auctionId, String type) {
        List<NotificationDTO> notifications = notificationService.getRecentNotifications(username);
        return (int) notifications.stream()
                .filter(notification -> Integer.valueOf(auctionId).equals(notification.getAuctionId()))
                .filter(notification -> type.equals(notification.getType()))
                .count();
    }

    private void assertDelivered(MessageType type) {
        assertTrue(deliveredPushes.stream()
                        .anyMatch(delivered -> delivered.packet().getMessageType() == type),
                "Expected push type " + type);
    }

    private void assertDeliveredTo(String username, MessageType type) {
        assertTrue(deliveredPushes.stream()
                        .anyMatch(delivered -> username.equals(delivered.username())
                                && delivered.packet().getMessageType() == type),
                "Expected push type " + type + " for " + username);
    }

    private void dropNotificationsTable() throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE notifications");
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
        TestDatabaseHelper.resetSingleton(NetworkPushService.class);
    }

    private record DeliveredPush(String username, PacketMessage packet) {
    }
}
