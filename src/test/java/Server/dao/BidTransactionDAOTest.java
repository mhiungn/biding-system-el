package Server.dao;

import CommonClasses.*;

import org.junit.jupiter.api.*;

import java.sql.SQLException;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests cho {@link BidTransactionDAO}.
 * <p>
 * Sử dụng H2 in-memory database (MySQL compatibility mode) thay cho MySQL thật.
 * </p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BidTransactionDAOTest {

    private static BidTransactionDAO bidTxDAO;

    @BeforeAll
    static void setUpAll() throws Exception {
        TestDatabaseHelper.redirectToH2();
        TestDatabaseHelper.createAllTables();

        TestDatabaseHelper.resetSingleton(BidTransactionDAO.class);
        bidTxDAO = BidTransactionDAO.getInstance();
    }

    @AfterAll
    static void tearDownAll() throws Exception {
        TestDatabaseHelper.dropAllTables();
        TestDatabaseHelper.resetSingleton(BidTransactionDAO.class);
    }

    @BeforeEach
    void clearData() throws SQLException {
        TestDatabaseHelper.clearAllTables();
    }

    // ========================== Helper ==========================

    /**
     * Tạo một BidTransaction mẫu.
     */
    private BidTransaction createSampleTransaction(int auctionId, float amount,
                                                    String bidder, boolean successful) {
        Bid bid = new Bid(new Date(), amount, bidder);
        return new BidTransaction(auctionId, bid, bidder, successful);
    }

    // ========================== Test save() ==========================

    @Test
    @Order(1)
    @DisplayName("save() - Lưu giao dịch thành công")
    void testSaveSuccess() {
        BidTransaction tx = createSampleTransaction(1, 500f, "john", true);
        bidTxDAO.save(tx.getTransactionId(), tx);

        BidTransaction found = bidTxDAO.findById(tx.getTransactionId());
        assertNotNull(found, "Giao dịch phải được tìm thấy sau khi save");
        assertEquals(tx.getTransactionId(), found.getTransactionId());
        assertEquals(1, found.getAuctionId());
        assertEquals(500f, found.getBid().getBid(), 0.01f);
        assertEquals("john", found.getBidderUsername());
        assertTrue(found.isSuccessful());
    }

    @Test
    @Order(2)
    @DisplayName("save() - Lưu giao dịch thất bại (successful=false)")
    void testSaveFailedTransaction() {
        BidTransaction tx = createSampleTransaction(1, 100f, "bob", false);
        bidTxDAO.save(tx.getTransactionId(), tx);

        BidTransaction found = bidTxDAO.findById(tx.getTransactionId());
        assertNotNull(found);
        assertFalse(found.isSuccessful(), "Giao dịch phải được lưu với successful=false");
    }

    @Test
    @Order(3)
    @DisplayName("save() - Từ chối khi transactionId null")
    void testSaveNullId() {
        BidTransaction tx = createSampleTransaction(1, 500f, "john", true);
        assertThrows(IllegalArgumentException.class,
                () -> bidTxDAO.save(null, tx));
    }

    @Test
    @Order(4)
    @DisplayName("save() - Từ chối khi transactionId rỗng")
    void testSaveEmptyId() {
        BidTransaction tx = createSampleTransaction(1, 500f, "john", true);
        assertThrows(IllegalArgumentException.class,
                () -> bidTxDAO.save("  ", tx));
    }

    @Test
    @Order(5)
    @DisplayName("save() - Từ chối khi transaction null")
    void testSaveNullTransaction() {
        assertThrows(IllegalArgumentException.class,
                () -> bidTxDAO.save("tx-001", null));
    }

    @Test
    @Order(6)
    @DisplayName("save() - Bỏ qua khi transactionId đã tồn tại")
    void testSaveDuplicate() {
        BidTransaction tx1 = createSampleTransaction(1, 500f, "john", true);
        String txId = tx1.getTransactionId();
        bidTxDAO.save(txId, tx1);

        BidTransaction tx2 = createSampleTransaction(2, 1000f, "bob", false);
        bidTxDAO.save(txId, tx2); // Phải bị bỏ qua

        BidTransaction found = bidTxDAO.findById(txId);
        assertEquals(1, found.getAuctionId(),
                "AuctionId phải giữ nguyên giá trị ban đầu vì save trùng bị từ chối");
    }

    // ========================== Test findById() ==========================

    @Test
    @Order(10)
    @DisplayName("findById() - Trả về null khi không tìm thấy")
    void testFindByIdNotFound() {
        assertNull(bidTxDAO.findById("nonexistent"));
    }

    @Test
    @Order(11)
    @DisplayName("findById() - Mapping dữ liệu Bid chính xác")
    void testFindByIdBidMapping() {
        Date bidDate = new Date(1700000000000L); // Thời điểm cố định
        Bid bid = new Bid(bidDate, 750f, "alice");
        BidTransaction tx = new BidTransaction(5, bid, "alice", true);
        bidTxDAO.save(tx.getTransactionId(), tx);

        BidTransaction found = bidTxDAO.findById(tx.getTransactionId());
        assertNotNull(found);
        assertNotNull(found.getBid(), "Bid phải được tái tạo từ ResultSet");
        assertEquals(750f, found.getBid().getBid(), 0.01f);
        assertEquals("alice", found.getBid().getBidderUsername());
    }

    // ========================== Test findAll() ==========================

    @Test
    @Order(20)
    @DisplayName("findAll() - Trả về danh sách rỗng khi chưa có giao dịch")
    void testFindAllEmpty() {
        List<BidTransaction> txs = bidTxDAO.findAll();
        assertNotNull(txs);
        assertTrue(txs.isEmpty());
    }

    @Test
    @Order(21)
    @DisplayName("findAll() - Trả về tất cả giao dịch")
    void testFindAllMultiple() {
        BidTransaction tx1 = createSampleTransaction(1, 500f, "john", true);
        BidTransaction tx2 = createSampleTransaction(2, 600f, "bob", true);
        BidTransaction tx3 = createSampleTransaction(1, 300f, "alice", false);

        bidTxDAO.save(tx1.getTransactionId(), tx1);
        bidTxDAO.save(tx2.getTransactionId(), tx2);
        bidTxDAO.save(tx3.getTransactionId(), tx3);

        List<BidTransaction> all = bidTxDAO.findAll();
        assertEquals(3, all.size());
    }

    // ========================== Test update() ==========================

    @Test
    @Order(30)
    @DisplayName("update() - Cập nhật giao dịch thành công")
    void testUpdateSuccess() {
        BidTransaction tx = createSampleTransaction(1, 500f, "john", true);
        bidTxDAO.save(tx.getTransactionId(), tx);

        // Tạo transaction mới với dữ liệu cập nhật
        Bid newBid = new Bid(new Date(), 800f, "john");
        BidTransaction updatedTx = new BidTransaction(1, newBid, "john", false);

        boolean result = bidTxDAO.update(tx.getTransactionId(), updatedTx);
        assertTrue(result);

        BidTransaction found = bidTxDAO.findById(tx.getTransactionId());
        assertEquals(800f, found.getBid().getBid(), 0.01f);
        assertFalse(found.isSuccessful(), "successful phải được cập nhật thành false");
    }

    @Test
    @Order(31)
    @DisplayName("update() - Trả về false khi giao dịch không tồn tại")
    void testUpdateNotFound() {
        BidTransaction tx = createSampleTransaction(1, 500f, "john", true);
        boolean result = bidTxDAO.update("nonexistent-id", tx);
        assertFalse(result);
    }

    // ========================== Test delete() ==========================

    @Test
    @Order(40)
    @DisplayName("delete() - Xóa giao dịch thành công")
    void testDeleteSuccess() {
        BidTransaction tx = createSampleTransaction(1, 500f, "john", true);
        bidTxDAO.save(tx.getTransactionId(), tx);

        boolean result = bidTxDAO.delete(tx.getTransactionId());
        assertTrue(result);
        assertNull(bidTxDAO.findById(tx.getTransactionId()));
    }

    @Test
    @Order(41)
    @DisplayName("delete() - Trả về false khi giao dịch không tồn tại")
    void testDeleteNotFound() {
        assertFalse(bidTxDAO.delete("nonexistent"));
    }

    // ========================== Test exists() & count() ==========================

    @Test
    @Order(50)
    @DisplayName("exists() - Kiểm tra tồn tại chính xác")
    void testExists() {
        BidTransaction tx = createSampleTransaction(1, 500f, "john", true);
        assertFalse(bidTxDAO.exists(tx.getTransactionId()));

        bidTxDAO.save(tx.getTransactionId(), tx);
        assertTrue(bidTxDAO.exists(tx.getTransactionId()));
    }

    @Test
    @Order(51)
    @DisplayName("count() - Đếm đúng số giao dịch")
    void testCount() {
        assertEquals(0, bidTxDAO.count());

        BidTransaction tx1 = createSampleTransaction(1, 500f, "john", true);
        BidTransaction tx2 = createSampleTransaction(2, 600f, "bob", true);
        bidTxDAO.save(tx1.getTransactionId(), tx1);
        bidTxDAO.save(tx2.getTransactionId(), tx2);

        assertEquals(2, bidTxDAO.count());
    }

    // ========================== Test findByAuctionId() ==========================

    @Test
    @Order(60)
    @DisplayName("findByAuctionId() - Tìm tất cả giao dịch của một phiên")
    void testFindByAuctionId() {
        BidTransaction tx1 = createSampleTransaction(1, 500f, "john", true);
        BidTransaction tx2 = createSampleTransaction(1, 600f, "bob", true);
        BidTransaction tx3 = createSampleTransaction(2, 300f, "alice", true);

        bidTxDAO.save(tx1.getTransactionId(), tx1);
        bidTxDAO.save(tx2.getTransactionId(), tx2);
        bidTxDAO.save(tx3.getTransactionId(), tx3);

        List<BidTransaction> auction1Txs = bidTxDAO.findByAuctionId(1);
        assertEquals(2, auction1Txs.size(), "Phiên 1 phải có 2 giao dịch");

        List<BidTransaction> auction2Txs = bidTxDAO.findByAuctionId(2);
        assertEquals(1, auction2Txs.size(), "Phiên 2 phải có 1 giao dịch");
    }

    @Test
    @Order(61)
    @DisplayName("findByAuctionId() - Trả về rỗng khi phiên không có giao dịch")
    void testFindByAuctionIdEmpty() {
        List<BidTransaction> txs = bidTxDAO.findByAuctionId(999);
        assertNotNull(txs);
        assertTrue(txs.isEmpty());
    }

    // ========================== Test findByBidderUsername() ==========================

    @Test
    @Order(70)
    @DisplayName("findByBidderUsername() - Tìm tất cả giao dịch của một bidder")
    void testFindByBidderUsername() {
        BidTransaction tx1 = createSampleTransaction(1, 500f, "john", true);
        BidTransaction tx2 = createSampleTransaction(2, 600f, "john", true);
        BidTransaction tx3 = createSampleTransaction(1, 300f, "bob", true);

        bidTxDAO.save(tx1.getTransactionId(), tx1);
        bidTxDAO.save(tx2.getTransactionId(), tx2);
        bidTxDAO.save(tx3.getTransactionId(), tx3);

        List<BidTransaction> johnTxs = bidTxDAO.findByBidderUsername("john");
        assertEquals(2, johnTxs.size(), "John phải có 2 giao dịch");

        List<BidTransaction> bobTxs = bidTxDAO.findByBidderUsername("bob");
        assertEquals(1, bobTxs.size(), "Bob phải có 1 giao dịch");
    }

    // ========================== Test findSuccessfulByAuctionId() ==========================

    @Test
    @Order(80)
    @DisplayName("findSuccessfulByAuctionId() - Chỉ trả về giao dịch thành công")
    void testFindSuccessfulByAuctionId() {
        BidTransaction txSuccess = createSampleTransaction(1, 500f, "john", true);
        BidTransaction txFail = createSampleTransaction(1, 300f, "bob", false);
        BidTransaction txSuccess2 = createSampleTransaction(1, 700f, "alice", true);

        bidTxDAO.save(txSuccess.getTransactionId(), txSuccess);
        bidTxDAO.save(txFail.getTransactionId(), txFail);
        bidTxDAO.save(txSuccess2.getTransactionId(), txSuccess2);

        List<BidTransaction> successful = bidTxDAO.findSuccessfulByAuctionId(1);
        assertEquals(2, successful.size(), "Chỉ có 2 giao dịch thành công");

        for (BidTransaction tx : successful) {
            assertTrue(tx.isSuccessful(), "Mỗi giao dịch trả về phải có successful=true");
        }
    }

    // ========================== Test deleteByAuctionId() ==========================

    @Test
    @Order(90)
    @DisplayName("deleteByAuctionId() - Xóa tất cả giao dịch của một phiên")
    void testDeleteByAuctionId() {
        BidTransaction tx1 = createSampleTransaction(1, 500f, "john", true);
        BidTransaction tx2 = createSampleTransaction(1, 600f, "bob", true);
        BidTransaction tx3 = createSampleTransaction(2, 300f, "alice", true);

        bidTxDAO.save(tx1.getTransactionId(), tx1);
        bidTxDAO.save(tx2.getTransactionId(), tx2);
        bidTxDAO.save(tx3.getTransactionId(), tx3);

        int deletedCount = bidTxDAO.deleteByAuctionId(1);
        assertEquals(2, deletedCount, "Phải xóa đúng 2 giao dịch của phiên 1");

        // Phiên 1 giờ rỗng
        assertTrue(bidTxDAO.findByAuctionId(1).isEmpty());

        // Phiên 2 vẫn còn nguyên
        assertEquals(1, bidTxDAO.findByAuctionId(2).size());
    }

    @Test
    @Order(91)
    @DisplayName("deleteByAuctionId() - Trả về 0 khi phiên không có giao dịch")
    void testDeleteByAuctionIdEmpty() {
        int deletedCount = bidTxDAO.deleteByAuctionId(999);
        assertEquals(0, deletedCount);
    }
}
