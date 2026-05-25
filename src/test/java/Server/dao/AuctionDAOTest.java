package Server.dao;

import CommonClasses.Bid;
import CommonClasses.User;
import CommonClasses.Items.Electronics;
import CommonClasses.Items.Item;
import CommonClasses.dto.DashboardAuctionRow;
import CommonClasses.dto.SellerAuctionRowDTO;

import org.junit.jupiter.api.*;

import java.sql.SQLException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests cho {@link AuctionDAO}.
 * <p>
 * Sử dụng H2 in-memory database (MySQL compatibility mode) thay cho MySQL thật.
 * Mỗi test case đều độc lập nhờ {@code @BeforeEach} xóa sạch dữ liệu.
 * </p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AuctionDAOTest {

    private static AuctionDAO auctionDAO;
    private static ItemDAO itemDAO;
    private static UserDAO userDAO;

    /** Item dùng chung cho test, được tạo lại ở mỗi test case. */
    private Item testItem;

    /** Item ID được kiểm soát trong test. */
    private static final String TEST_ITEM_ID = "item-001";

    @BeforeAll
    static void setUpAll() throws Exception {
        TestDatabaseHelper.redirectToH2();
        TestDatabaseHelper.createAllTables();

        TestDatabaseHelper.resetSingleton(AuctionDAO.class);
        TestDatabaseHelper.resetSingleton(ItemDAO.class);
        TestDatabaseHelper.resetSingleton(UserDAO.class);

        auctionDAO = AuctionDAO.getInstance();
        itemDAO = ItemDAO.getInstance();
        userDAO = UserDAO.getInstance();
    }

    @AfterAll
    static void tearDownAll() throws Exception {
        TestDatabaseHelper.dropAllTables();
        TestDatabaseHelper.resetSingleton(AuctionDAO.class);
        TestDatabaseHelper.resetSingleton(ItemDAO.class);
        TestDatabaseHelper.resetSingleton(UserDAO.class);
    }

    @BeforeEach
    void clearData() throws SQLException {
        TestDatabaseHelper.clearAllTables();

        // Tạo dữ liệu nền (User và Item) cho các FK constraints
        User seller = new User("seller_john", "pass", "john@mail.com", "USER");
        userDAO.save("seller_john", seller);

        User bidder1 = new User("bidder1", "pass", "b1@mail.com", "USER");
        userDAO.save("bidder1", bidder1);

        User bidder2 = new User("bidder2", "pass", "b2@mail.com", "USER");
        userDAO.save("bidder2", bidder2);

        // Dùng Electronics (concrete class) thay vì Item (abstract)
        testItem = new Electronics(1000f, "Laptop Gaming", "New");
        // Dùng save() với ID cố định để kiểm soát trong test
        itemDAO.save(TEST_ITEM_ID, testItem);
    }

    // ========================== Helper ==========================

    /**
     * Tạo AuctionSnapshot mẫu với dữ liệu chuẩn.
     *
     * @param auctionId ID cho snapshot
     * @return AuctionSnapshot đã sẵn sàng để lưu
     */
    private AuctionSnapshot createSampleSnapshot(int auctionId) {
        return new AuctionSnapshot(
                auctionId,
                "seller_john",
                new Date(),
                new Date(System.currentTimeMillis() + 86400000), // +1 day
                "Time_Fixed",
                "OPEN",
                testItem,
                new LinkedList<>(),
                new ArrayList<>(),
                false
        );
    }

    // ========================== Test save() ==========================

    @Test
    @Order(1)
    @DisplayName("save() - Lưu phiên đấu giá thành công")
    void testSaveSuccess() {
        AuctionSnapshot snapshot = createSampleSnapshot(1);
        auctionDAO.save("1", snapshot);

        AuctionSnapshot found = auctionDAO.findById("1");
        assertNotNull(found, "Phải tìm thấy phiên đấu giá sau khi save");
        assertEquals(1, found.getAuctionId());
        assertEquals("seller_john", found.getClientOwner());
        assertEquals("OPEN", found.getStatus());
        assertEquals("Time_Fixed", found.getType());
        assertNotNull(found.getItem(), "Item phải được load từ ItemDAO");
    }

    @Test
    @Order(2)
    @DisplayName("save() - Chuyển sang update nếu auctionId đã tồn tại")
    void testSaveDuplicateTriggersUpdate() {
        AuctionSnapshot snapshot = createSampleSnapshot(2);
        auctionDAO.save("2", snapshot);

        // Cập nhật status rồi save lại — phải chuyển sang update
        snapshot.setStatus("RUNNING");
        auctionDAO.save("2", snapshot);

        AuctionSnapshot found = auctionDAO.findById("2");
        assertEquals("RUNNING", found.getStatus(),
                "Status phải được cập nhật khi save trùng ID");
    }

    @Test
    @Order(3)
    @DisplayName("save() - Ném exception khi auctionId null")
    void testSaveNullId() {
        AuctionSnapshot snapshot = createSampleSnapshot(99);
        assertThrows(IllegalArgumentException.class,
                () -> auctionDAO.save(null, snapshot),
                "Phải ném IllegalArgumentException khi auctionId null");
    }

    @Test
    @Order(4)
    @DisplayName("save() - Ném exception khi snapshot null")
    void testSaveNullSnapshot() {
        assertThrows(IllegalArgumentException.class,
                () -> auctionDAO.save("99", null),
                "Phải ném IllegalArgumentException khi snapshot null");
    }

    // ========================== Test updateStatus() ==========================

    @Test
    @Order(10)
    @DisplayName("updateStatus() - Cập nhật trạng thái thành công")
    void testUpdateStatus() {
        AuctionSnapshot snapshot = createSampleSnapshot(3);
        auctionDAO.save("3", snapshot);

        boolean result = auctionDAO.updateStatus("3", "FINISHED");
        assertTrue(result, "updateStatus phải trả về true khi thành công");
        assertEquals("FINISHED", auctionDAO.findById("3").getStatus());
    }

    @Test
    @Order(11)
    @DisplayName("updateStatus() - Trả về false khi auctionId không tồn tại")
    void testUpdateStatusNotFound() {
        boolean result = auctionDAO.updateStatus("999", "FINISHED");
        assertFalse(result, "updateStatus phải trả về false khi ID không tồn tại");
    }

    // ========================== Test delete() ==========================

    @Test
    @Order(20)
    @DisplayName("delete() - Xóa phiên đấu giá thành công")
    void testDelete() {
        AuctionSnapshot snapshot = createSampleSnapshot(4);
        auctionDAO.save("4", snapshot);

        assertTrue(auctionDAO.delete("4"), "delete phải trả về true");
        assertNull(auctionDAO.findById("4"), "Phải không còn tìm thấy sau khi xóa");
    }

    @Test
    @Order(21)
    @DisplayName("delete() - Trả về false khi auctionId không tồn tại")
    void testDeleteNotFound() {
        boolean result = auctionDAO.delete("999");
        assertFalse(result, "delete phải trả về false khi ID không tồn tại");
    }

    // ========================== Test findAll() & count() ==========================

    @Test
    @Order(30)
    @DisplayName("findAll() và count() - Đếm và lấy danh sách")
    void testFindAllAndCount() {
        auctionDAO.save("5", createSampleSnapshot(5));
        auctionDAO.save("6", createSampleSnapshot(6));

        assertEquals(2, auctionDAO.count(), "count phải trả về đúng số lượng");
        assertEquals(2, auctionDAO.findAll().size(), "findAll phải trả về đúng số phần tử");
    }

    @Test
    @Order(31)
    @DisplayName("findAll() - Trả về danh sách rỗng khi chưa có dữ liệu")
    void testFindAllEmpty() {
        List<AuctionSnapshot> result = auctionDAO.findAll();
        assertNotNull(result, "findAll không được trả về null");
        assertTrue(result.isEmpty(), "Danh sách phải rỗng khi chưa có auction");
    }

    @Test
    @Order(32)
    @DisplayName("count() - Trả về 0 khi chưa có dữ liệu")
    void testCountEmpty() {
        assertEquals(0, auctionDAO.count());
    }

    // ========================== Test findById() ==========================

    @Test
    @Order(35)
    @DisplayName("findById() - Trả về null khi không tìm thấy")
    void testFindByIdNotFound() {
        assertNull(auctionDAO.findById("999"),
                "findById phải trả về null khi ID không tồn tại");
    }

    // ========================== Test exists() ==========================

    @Test
    @Order(36)
    @DisplayName("exists() - Trả về true khi phiên tồn tại")
    void testExistsTrue() {
        auctionDAO.save("20", createSampleSnapshot(20));
        assertTrue(auctionDAO.exists("20"));
    }

    @Test
    @Order(37)
    @DisplayName("exists() - Trả về false khi phiên không tồn tại")
    void testExistsFalse() {
        assertFalse(auctionDAO.exists("999"));
    }

    // ========================== Test addBid() ==========================

    @Test
    @Order(40)
    @DisplayName("addBid() - Thêm bid thành công, bid mới nhất ở đầu danh sách")
    void testAddBid() {
        AuctionSnapshot snapshot = createSampleSnapshot(7);
        auctionDAO.save("7", snapshot);

        Bid bid1 = new Bid(new Date(), 1100f, "bidder1");
        auctionDAO.addBid("7", bid1);

        Bid bid2 = new Bid(new Date(), 1200f, "bidder1");
        auctionDAO.addBid("7", bid2);

        AuctionSnapshot found = auctionDAO.findById("7");
        LinkedList<Bid> bids = found.getBidList();
        assertEquals(2, bids.size(), "Phải có đúng 2 bids");
        assertEquals(1200f, bids.get(0).getBid(), 0.01f,
                "Bid mới nhất (cao nhất) phải ở đầu danh sách (bid_order 0)");
        assertEquals(1100f, bids.get(1).getBid(), 0.01f,
                "Bid cũ hơn phải ở vị trí sau");
    }

    @Test
    @Order(41)
    @DisplayName("addBid() - Phiên chưa có bid trước đó")
    void testAddFirstBid() {
        AuctionSnapshot snapshot = createSampleSnapshot(12);
        auctionDAO.save("12", snapshot);

        Bid bid = new Bid(new Date(), 1050f, "bidder1");
        auctionDAO.addBid("12", bid);

        AuctionSnapshot found = auctionDAO.findById("12");
        assertEquals(1, found.getBidList().size());
        assertEquals("bidder1", found.getBidList().get(0).getBidderUsername());
    }

    // ========================== Test addParticipant() & removeParticipant() ==========================

    @Test
    @Order(50)
    @DisplayName("addParticipant() và removeParticipant() - Thêm và xóa người tham gia")
    void testParticipants() {
        AuctionSnapshot snapshot = createSampleSnapshot(8);
        auctionDAO.save("8", snapshot);

        auctionDAO.addParticipant("8", "bidder1");

        AuctionSnapshot found = auctionDAO.findById("8");
        assertTrue(found.getRegisteredUsernames().contains("bidder1"),
                "Danh sách phải chứa bidder1 sau khi add");

        auctionDAO.removeParticipant("8", "bidder1");
        found = auctionDAO.findById("8");
        assertFalse(found.getRegisteredUsernames().contains("bidder1"),
                "Danh sách không được chứa bidder1 sau khi remove");
    }

    @Test
    @Order(51)
    @DisplayName("removeParticipant() - Trả về false khi người tham gia không tồn tại")
    void testRemoveNonexistentParticipant() {
        AuctionSnapshot snapshot = createSampleSnapshot(13);
        auctionDAO.save("13", snapshot);

        boolean result = auctionDAO.removeParticipant("13", "nobody");
        assertFalse(result, "removeParticipant phải trả về false khi user không tồn tại");
    }

    // ========================== Test findByStatus() ==========================

    @Test
    @Order(60)
    @DisplayName("findByStatus() - Tìm theo trạng thái")
    void testFindByStatus() {
        AuctionSnapshot s1 = createSampleSnapshot(9);
        s1.setStatus("OPEN");
        auctionDAO.save("9", s1);

        AuctionSnapshot s2 = createSampleSnapshot(10);
        s2.setStatus("FINISHED");
        auctionDAO.save("10", s2);

        List<AuctionSnapshot> openAuctions = auctionDAO.findByStatus("OPEN");
        assertEquals(1, openAuctions.size(), "Phải tìm thấy đúng 1 phiên OPEN");
        assertEquals(9, openAuctions.get(0).getAuctionId());

        List<AuctionSnapshot> finishedAuctions = auctionDAO.findByStatus("FINISHED");
        assertEquals(1, finishedAuctions.size(), "Phải tìm thấy đúng 1 phiên FINISHED");
    }

    @Test
    @Order(61)
    @DisplayName("findByStatus() - Trả về danh sách rỗng khi không có kết quả")
    void testFindByStatusEmpty() {
        List<AuctionSnapshot> result = auctionDAO.findByStatus("CANCELED");
        assertNotNull(result);
        assertTrue(result.isEmpty(), "Phải trả về rỗng khi không có phiên nào CANCELED");
    }

    // ========================== Test findByClientOwner() ==========================

    @Test
    @Order(70)
    @DisplayName("findByClientOwner() - Tìm theo seller")
    void testFindByClientOwner() {
        AuctionSnapshot snapshot = createSampleSnapshot(11);
        auctionDAO.save("11", snapshot);

        List<AuctionSnapshot> list = auctionDAO.findByClientOwner("seller_john");
        assertEquals(1, list.size(), "Phải tìm thấy đúng 1 phiên của seller_john");
        assertEquals(11, list.get(0).getAuctionId());
    }

    @Test
    @Order(71)
    @DisplayName("findByClientOwner() - Trả về rỗng khi seller không có phiên nào")
    void testFindByClientOwnerEmpty() {
        List<AuctionSnapshot> list = auctionDAO.findByClientOwner("nobody");
        assertNotNull(list);
        assertTrue(list.isEmpty(), "Phải trả về rỗng khi seller không tồn tại");
    }

    @Test
    @Order(72)
    @DisplayName("findSellerAuctionRows() - returns seller auction rows")
    void testFindSellerAuctionRows() {
        AuctionSnapshot snapshot = createSampleSnapshot(21);
        auctionDAO.save("21", snapshot);
        auctionDAO.addBid("21", new Bid(new Date(), 1200f, "bidder1"));

        List<SellerAuctionRowDTO> rows = auctionDAO.findSellerAuctionRows("seller_john");

        assertEquals(1, rows.size());
        SellerAuctionRowDTO row = rows.get(0);
        assertEquals(21, row.getAuctionId());
        assertEquals("Laptop Gaming", row.getItemName());
        assertEquals(1200f, row.getCurrentPrice(), 0.01f);
        assertEquals(1, row.getBidCount());
        assertEquals("bidder1", row.getHighestBidderUsername());
    }

    @Test
    @Order(73)
    @DisplayName("countSoldByUser() - counts finished seller auctions with bids")
    void testCountSoldByUser() {
        AuctionSnapshot snapshot = createSampleSnapshot(22);
        snapshot.setStatus("FINISHED");
        auctionDAO.save("22", snapshot);
        auctionDAO.addBid("22", new Bid(new Date(), 1200f, "bidder1"));

        assertEquals(1, auctionDAO.countSoldByUser("seller_john"));
    }

    @Test
    @Order(74)
    @DisplayName("searchAuctionsByName() - finds active auctions by partial item name")
    void testSearchAuctionsByName() {
        Item phone = new Electronics(500f, "Smart Phone", "New phone");
        itemDAO.save("item-phone", phone);
        AuctionSnapshot phoneAuction = new AuctionSnapshot(
                23,
                "seller_john",
                new Date(),
                new Date(System.currentTimeMillis() + 86400000),
                "Time_Fixed",
                "OPEN",
                phone,
                new LinkedList<>(),
                new ArrayList<>(),
                false);
        auctionDAO.save("23", phoneAuction);
        auctionDAO.save("24", createSampleSnapshot(24));

        List<DashboardAuctionRow> rows = auctionDAO.searchAuctionsByName("phone", 10);

        assertEquals(1, rows.size());
        assertEquals(23, rows.get(0).getAuctionId());
        assertEquals("Smart Phone", rows.get(0).getItem().getName());
    }

    @Test
    @Order(75)
    @DisplayName("findExpiredOpenRunningAuctions() - returns only expired OPEN/RUNNING auctions")
    void testFindExpiredOpenRunningAuctions() {
        AuctionSnapshot expiredOpen = createSampleSnapshot(25);
        expiredOpen.setTerminateAt(new Date(System.currentTimeMillis() - 60_000L));
        expiredOpen.setStatus("OPEN");
        auctionDAO.save("25", expiredOpen);

        AuctionSnapshot expiredRunning = createSampleSnapshot(26);
        expiredRunning.setTerminateAt(new Date(System.currentTimeMillis() - 30_000L));
        expiredRunning.setStatus("RUNNING");
        auctionDAO.save("26", expiredRunning);

        AuctionSnapshot futureOpen = createSampleSnapshot(27);
        futureOpen.setTerminateAt(new Date(System.currentTimeMillis() + 60_000L));
        futureOpen.setStatus("OPEN");
        auctionDAO.save("27", futureOpen);

        AuctionSnapshot expiredFinished = createSampleSnapshot(28);
        expiredFinished.setTerminateAt(new Date(System.currentTimeMillis() - 60_000L));
        expiredFinished.setStatus("FINISHED");
        auctionDAO.save("28", expiredFinished);

        List<Integer> ids = auctionDAO.findExpiredOpenRunningAuctions().stream()
                .map(AuctionSnapshot::getAuctionId)
                .toList();

        assertEquals(List.of(25, 26), ids);
    }

    @Test
    @Order(76)
    @DisplayName("getHighestBidForAuction() and hasBids() - handles bid and no-bid auctions")
    void testHighestBidAndNoBidSupport() {
        auctionDAO.save("29", createSampleSnapshot(29));

        assertFalse(auctionDAO.hasBids(29));
        assertNull(auctionDAO.getHighestBidForAuction(29));

        auctionDAO.addBid("29", new Bid(new Date(), 1100f, "bidder1"));
        auctionDAO.addBid("29", new Bid(new Date(), 1250f, "bidder2"));

        assertTrue(auctionDAO.hasBids(29));
        Bid highest = auctionDAO.getHighestBidForAuction(29);
        assertNotNull(highest);
        assertEquals("bidder2", highest.getBidderUsername());
        assertEquals(1250f, highest.getBid(), 0.01f);
    }

    @Test
    @Order(77)
    @DisplayName("findLosingBiddersForAuction() - excludes current winner")
    void testFindLosingBiddersForAuction() {
        auctionDAO.save("30", createSampleSnapshot(30));

        auctionDAO.addBid("30", new Bid(new Date(), 1100f, "bidder1"));
        auctionDAO.addBid("30", new Bid(new Date(), 1200f, "bidder2"));
        auctionDAO.addBid("30", new Bid(new Date(), 1300f, "bidder1"));

        List<String> losingBidders = auctionDAO.findLosingBiddersForAuction(30);

        assertEquals(List.of("bidder2"), losingBidders);
    }

    @Test
    @Order(78)
    @DisplayName("findLosingParticipantsForAuction() - excludes current winner")
    void testFindLosingParticipantsForAuction() {
        auctionDAO.save("31", createSampleSnapshot(31));
        auctionDAO.addParticipant("31", "bidder1");
        auctionDAO.addParticipant("31", "bidder2");
        auctionDAO.addBid("31", new Bid(new Date(), 1100f, "bidder1"));

        List<String> losingParticipants = auctionDAO.findLosingParticipantsForAuction(31);

        assertEquals(List.of("bidder2"), losingParticipants);
    }

    @Test
    @Order(79)
    @DisplayName("markAuctionFinished() - marks auction FINISHED")
    void testMarkAuctionFinished() {
        auctionDAO.save("32", createSampleSnapshot(32));

        assertTrue(auctionDAO.markAuctionFinished(32));

        assertEquals("FINISHED", auctionDAO.findById("32").getStatus());
    }

    // ========================== Test findAllAsMap() ==========================

    @Test
    @Order(80)
    @DisplayName("findAllAsMap() - Trả về Map đúng cấu trúc")
    void testFindAllAsMap() {
        auctionDAO.save("14", createSampleSnapshot(14));
        auctionDAO.save("15", createSampleSnapshot(15));

        Map<String, AuctionSnapshot> map = auctionDAO.findAllAsMap();
        assertEquals(2, map.size(), "Map phải chứa đúng 2 phần tử");
        assertTrue(map.containsKey("14"), "Map phải chứa key '14'");
        assertTrue(map.containsKey("15"), "Map phải chứa key '15'");
    }

    // ========================== Test update() ==========================

    @Test
    @Order(90)
    @DisplayName("update() - Cập nhật toàn bộ snapshot thành công")
    void testUpdateFull() {
        AuctionSnapshot snapshot = createSampleSnapshot(16);
        auctionDAO.save("16", snapshot);

        snapshot.setStatus("RUNNING");
        snapshot.setWasInCountDown(true);
        boolean result = auctionDAO.update("16", snapshot);

        assertTrue(result, "update phải trả về true khi thành công");

        AuctionSnapshot found = auctionDAO.findById("16");
        assertEquals("RUNNING", found.getStatus());
        assertTrue(found.wasInCountDown());
    }

    @Test
    @Order(91)
    @DisplayName("update() - Trả về false khi phiên không tồn tại")
    void testUpdateNotFound() {
        AuctionSnapshot snapshot = createSampleSnapshot(999);
        boolean result = auctionDAO.update("999", snapshot);
        assertFalse(result, "update phải trả về false khi ID không tồn tại");
    }

    // ========================== Test lifecycle trạng thái ==========================

    @Test
    @Order(100)
    @DisplayName("Lifecycle - Chuyển đổi trạng thái OPEN → RUNNING → FINISHED")
    void testStatusLifecycle() {
        AuctionSnapshot snapshot = createSampleSnapshot(17);
        auctionDAO.save("17", snapshot);
        assertEquals("OPEN", auctionDAO.findById("17").getStatus());

        auctionDAO.updateStatus("17", "RUNNING");
        assertEquals("RUNNING", auctionDAO.findById("17").getStatus());

        auctionDAO.updateStatus("17", "FINISHED");
        assertEquals("FINISHED", auctionDAO.findById("17").getStatus());
    }
}
