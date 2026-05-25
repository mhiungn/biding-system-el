package Server.service;

import CommonClasses.Items.Electronics;
import CommonClasses.Items.Item;
import CommonClasses.User;
import CommonClasses.dto.NotificationDTO;
import CommonClasses.dto.WalletDTO;
import Packets.MessageType;
import Packets.PacketMessage;
import Server.dao.AuctionDAO;
import Server.dao.AuctionSnapshot;
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

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionFinalizationServiceTest {
    private static UserDAO userDAO;
    private static ItemDAO itemDAO;
    private static AuctionDAO auctionDAO;
    private static WalletApplicationService walletService;
    private static BiddingApplicationService biddingService;
    private static NotificationApplicationService notificationService;
    private static AuctionFinalizationService finalizationService;
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
        biddingService = BiddingApplicationService.getInstance();
        notificationService = NotificationApplicationService.getInstance();
        finalizationService = AuctionFinalizationService.getInstance();
    }

    @AfterAll
    static void tearDownAll() throws Exception {
        TestDatabaseHelper.dropAllTables();
        resetSingletons();
    }

    @BeforeEach
    void clearData() throws SQLException {
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
    void expiredAuctionWithWinnerIsFinishedAndWinnerHoldBecomesSpent() {
        createUsers();
        createAuction(1, new Date(System.currentTimeMillis() + 86_400_000L));
        walletService.ensureWallet("bidder1");
        walletService.ensureWallet("bidder2");

        assertTrue(biddingService.placeBid("bidder1", 1, 1_100f));
        assertTrue(biddingService.placeBid("bidder2", 1, 1_200f));
        auctionDAO.updateTerminateAt("1", new Date(System.currentTimeMillis() - 1_000L));

        int finalized = finalizationService.finalizeEndedAuctions();

        assertEquals(1, finalized);
        assertEquals("FINISHED", auctionDAO.findById("1").getStatus());
        WalletDTO winnerWallet = walletService.getWallet("bidder2");
        assertEquals(98_800L, winnerWallet.getBalance());
        assertEquals(0L, winnerWallet.getHeldAmount());
        assertEquals(1_200L, winnerWallet.getTotalSpent());
        assertNotification("seller", 1, "AUCTION_SOLD", "MY_BIDS");
        assertNotification("bidder2", 1, "AUCTION_WON", "MY_BIDS");
    }

    @Test
    void auctionFinalizationPublishesAuctionNotificationAndWalletPushes() {
        createUsers();
        createAuction(20, new Date(System.currentTimeMillis() + 86_400_000L));
        walletService.ensureWallet("bidder1");
        walletService.ensureWallet("bidder2");

        assertTrue(biddingService.placeBid("bidder1", 20, 1_100f));
        assertTrue(biddingService.placeBid("bidder2", 20, 1_200f));
        auctionDAO.updateTerminateAt("20", new Date(System.currentTimeMillis() - 1_000L));
        deliveredPushes.clear();

        assertEquals(1, finalizationService.finalizeEndedAuctions());

        assertDelivered(MessageType.AUCTION_UPDATE_PUSH);
        assertDeliveredTo("seller", MessageType.NOTIFICATION_PUSH);
        assertDeliveredTo("bidder2", MessageType.NOTIFICATION_PUSH);
        assertDeliveredTo("bidder1", MessageType.NOTIFICATION_PUSH);
        assertDeliveredTo("bidder2", MessageType.WALLET_UPDATE_PUSH);
    }

    @Test
    void expiredAuctionWithNoBidsIsFinishedAndSellerIsNotified() {
        createUsers();
        createAuction(2, new Date(System.currentTimeMillis() - 1_000L));

        int finalized = finalizationService.finalizeEndedAuctions();

        assertEquals(1, finalized);
        assertEquals("FINISHED", auctionDAO.findById("2").getStatus());
        assertNotification("seller", 2, "AUCTION_ENDED_NO_BIDS", "MY_BIDS");
    }

    @Test
    void losingBidderReceivesAuctionLostNotification() {
        createUsers();
        createAuction(3, new Date(System.currentTimeMillis() + 86_400_000L));
        walletService.ensureWallet("bidder1");
        walletService.ensureWallet("bidder2");

        assertTrue(biddingService.placeBid("bidder1", 3, 1_100f));
        assertTrue(biddingService.placeBid("bidder2", 3, 1_200f));
        auctionDAO.updateTerminateAt("3", new Date(System.currentTimeMillis() - 1_000L));

        int finalized = finalizationService.finalizeEndedAuctions();

        assertEquals(1, finalized);
        assertNotification("bidder1", 3, "AUCTION_LOST", "MY_BIDS");
        assertFalse(hasNotification("bidder2", 3, "AUCTION_LOST"));
    }

    @Test
    void finalizerCanBeCalledTwiceWithoutDuplicatePaymentsOrResultNotifications() {
        createUsers();
        createAuction(4, new Date(System.currentTimeMillis() + 86_400_000L));
        walletService.ensureWallet("bidder1");
        walletService.ensureWallet("bidder2");

        assertTrue(biddingService.placeBid("bidder1", 4, 1_100f));
        assertTrue(biddingService.placeBid("bidder2", 4, 1_200f));
        auctionDAO.updateTerminateAt("4", new Date(System.currentTimeMillis() - 1_000L));

        int firstRun = finalizationService.finalizeEndedAuctions();
        WalletDTO afterFirstRun = walletService.getWallet("bidder2");
        int sellerSoldCount = countNotification("seller", 4, "AUCTION_SOLD");
        int winnerWonCount = countNotification("bidder2", 4, "AUCTION_WON");
        int loserLostCount = countNotification("bidder1", 4, "AUCTION_LOST");

        int secondRun = finalizationService.finalizeEndedAuctions();
        WalletDTO afterSecondRun = walletService.getWallet("bidder2");

        assertEquals(1, firstRun);
        assertEquals(0, secondRun);
        assertEquals(afterFirstRun.getBalance(), afterSecondRun.getBalance());
        assertEquals(afterFirstRun.getHeldAmount(), afterSecondRun.getHeldAmount());
        assertEquals(afterFirstRun.getTotalSpent(), afterSecondRun.getTotalSpent());
        assertEquals(98_800L, afterSecondRun.getBalance());
        assertEquals(0L, afterSecondRun.getHeldAmount());
        assertEquals(1_200L, afterSecondRun.getTotalSpent());
        assertEquals(sellerSoldCount, countNotification("seller", 4, "AUCTION_SOLD"));
        assertEquals(winnerWonCount, countNotification("bidder2", 4, "AUCTION_WON"));
        assertEquals(loserLostCount, countNotification("bidder1", 4, "AUCTION_LOST"));
        assertEquals(1, countNotification("seller", 4, "AUCTION_SOLD"));
        assertEquals(1, countNotification("bidder2", 4, "AUCTION_WON"));
        assertEquals(1, countNotification("bidder1", 4, "AUCTION_LOST"));
        assertFalse(hasNotification("bidder2", 4, "AUCTION_LOST"));
    }

    @Test
    void finalizerCanBeCalledTwiceForNoBidAuctionWithoutDuplicateNoBidNotification() {
        createUsers();
        createAuction(5, new Date(System.currentTimeMillis() - 1_000L));

        int firstRun = finalizationService.finalizeEndedAuctions();
        int secondRun = finalizationService.finalizeEndedAuctions();

        assertEquals(1, firstRun);
        assertEquals(0, secondRun);
        assertEquals("FINISHED", auctionDAO.findById("5").getStatus());
        assertEquals(1, countNotification("seller", 5, "AUCTION_ENDED_NO_BIDS"));
        assertNotification("seller", 5, "AUCTION_ENDED_NO_BIDS", "MY_BIDS");
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

    private void assertNotification(String username, int auctionId, String type, String actionTarget) {
        List<NotificationDTO> notifications = notificationService.getRecentNotifications(username);
        assertTrue(notifications.stream().anyMatch(notification ->
                        username.equals(notification.getUsername())
                                && Integer.valueOf(auctionId).equals(notification.getAuctionId())
                                && type.equals(notification.getType())
                                && actionTarget.equals(notification.getActionTarget())),
                "Expected notification type " + type + " for " + username + " on auction " + auctionId);
    }

    private boolean hasNotification(String username, int auctionId, String type) {
        return notificationService.getRecentNotifications(username).stream()
                .anyMatch(notification ->
                        Integer.valueOf(auctionId).equals(notification.getAuctionId())
                                && type.equals(notification.getType()));
    }

    private int countNotification(String username, int auctionId, String type) {
        return (int) notificationService.getRecentNotifications(username).stream()
                .filter(notification ->
                        Integer.valueOf(auctionId).equals(notification.getAuctionId())
                                && type.equals(notification.getType()))
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
        TestDatabaseHelper.resetSingleton(NetworkPushService.class);
    }

    private record DeliveredPush(String username, PacketMessage packet) {
    }
}
