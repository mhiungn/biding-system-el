package Server.service;

import CommonClasses.User;
import CommonClasses.dto.WalletDTO;
import CommonClasses.Items.Electronics;
import CommonClasses.Items.Item;
import Server.dao.AuctionDAO;
import Server.dao.AuctionSnapshot;
import Server.dao.ItemDAO;
import Server.dao.NotificationDAO;
import Server.dao.TestDatabaseHelper;
import Server.dao.UserDAO;
import Server.dao.WalletDAO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WalletApplicationServiceTest {
    private static UserDAO userDAO;
    private static ItemDAO itemDAO;
    private static AuctionDAO auctionDAO;
    private static WalletApplicationService walletService;
    private static BiddingApplicationService biddingService;

    @BeforeAll
    static void setUpAll() throws Exception {
        TestDatabaseHelper.redirectToH2();
        TestDatabaseHelper.createAllTables();

        TestDatabaseHelper.resetSingleton(UserDAO.class);
        TestDatabaseHelper.resetSingleton(ItemDAO.class);
        TestDatabaseHelper.resetSingleton(AuctionDAO.class);
        TestDatabaseHelper.resetSingleton(WalletDAO.class);
        TestDatabaseHelper.resetSingleton(NotificationDAO.class);
        TestDatabaseHelper.resetSingleton(WalletApplicationService.class);
        TestDatabaseHelper.resetSingleton(NotificationApplicationService.class);
        TestDatabaseHelper.resetSingleton(BiddingApplicationService.class);

        userDAO = UserDAO.getInstance();
        itemDAO = ItemDAO.getInstance();
        auctionDAO = AuctionDAO.getInstance();
        walletService = WalletApplicationService.getInstance();
        biddingService = BiddingApplicationService.getInstance();
    }

    @AfterAll
    static void tearDownAll() throws Exception {
        TestDatabaseHelper.dropAllTables();
        TestDatabaseHelper.resetSingleton(UserDAO.class);
        TestDatabaseHelper.resetSingleton(ItemDAO.class);
        TestDatabaseHelper.resetSingleton(AuctionDAO.class);
        TestDatabaseHelper.resetSingleton(WalletDAO.class);
        TestDatabaseHelper.resetSingleton(NotificationDAO.class);
        TestDatabaseHelper.resetSingleton(WalletApplicationService.class);
        TestDatabaseHelper.resetSingleton(NotificationApplicationService.class);
        TestDatabaseHelper.resetSingleton(BiddingApplicationService.class);
    }

    @BeforeEach
    void clearData() throws SQLException {
        TestDatabaseHelper.clearAllTables();
    }

    @Test
    void newWalletStartsWithDefaultBalance() {
        userDAO.save("bidder", new User("bidder", "pass", "bidder@mail.com", "USER"));

        WalletDTO wallet = walletService.ensureWallet("bidder");

        assertTrue(wallet.isSuccess());
        assertEquals(100_000L, wallet.getBalance());
        assertEquals(100_000L, wallet.getAvailableBalance());
    }

    @Test
    void depositEnforcesPositiveAmountAndDailyCap() {
        userDAO.save("bidder", new User("bidder", "pass", "bidder@mail.com", "USER"));
        walletService.ensureWallet("bidder");

        assertFalse(walletService.deposit("bidder", 0).isSuccess());
        assertFalse(walletService.deposit("bidder", 10_000_000L).isSuccess());

        WalletDTO afterDeposit = walletService.deposit("bidder", 5_000L);
        assertTrue(afterDeposit.isSuccess());
        assertEquals(105_000L, afterDeposit.getBalance());
    }

    @Test
    void bidServiceReservesNewHoldAndReleasesPreviousHighestBidder() {
        createAuctionFixture();
        walletService.ensureWallet("bidder1");
        walletService.ensureWallet("bidder2");

        assertTrue(biddingService.placeBid("bidder1", 1, 1_100f));
        assertEquals(1_100L, walletService.getWallet("bidder1").getHeldAmount());

        assertTrue(biddingService.placeBid("bidder2", 1, 1_200f));

        assertEquals(0L, walletService.getWallet("bidder1").getHeldAmount());
        assertEquals(1_200L, walletService.getWallet("bidder2").getHeldAmount());
        assertEquals(2, NotificationApplicationService.getInstance().getUnreadCount("seller"));
        assertEquals(1, NotificationApplicationService.getInstance().getUnreadCount("bidder1"));
    }

    @Test
    void bidServiceRejectsOwnerAndInsufficientBalance() {
        createAuctionFixture();
        walletService.ensureWallet("seller");
        walletService.ensureWallet("bidder1");

        assertFalse(biddingService.placeBid("seller", 1, 1_100f));
        assertFalse(biddingService.placeBid("bidder1", 1, 101_000f));
    }

    private void createAuctionFixture() {
        userDAO.save("seller", new User("seller", "pass", "seller@mail.com", "USER"));
        userDAO.save("bidder1", new User("bidder1", "pass", "bidder1@mail.com", "USER"));
        userDAO.save("bidder2", new User("bidder2", "pass", "bidder2@mail.com", "USER"));

        Item item = new Electronics(1_000f, "Laptop", "New");
        itemDAO.save("item-001", item);

        AuctionSnapshot snapshot = new AuctionSnapshot(
                1,
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
        auctionDAO.save("1", snapshot);
    }
}
