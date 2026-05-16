package CommonClasses;

import CommonClasses.Exceptions.*;
import CommonClasses.Items.Electronics;
import CommonClasses.Items.Item;
import org.junit.jupiter.api.*;

import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link Auction} class.
 * <p>
 * Tests cover the core business logic of the auction system:
 * lifecycle management, bid placement, participant handling,
 * cancellation, conclusion, and the observer pattern.
 * </p>
 */
@DisplayName("Auction - Unit Tests")
class AuctionTest {

    private Auction auction;
    private Item testItem;

    @BeforeEach
    void setUp() {
        testItem = new Electronics(100.0f, "Laptop Gaming", "Laptop MSI cao cấp");
        auction = new Auction("seller01", testItem, AuctionType.TIME_FIXED);
    }

    // ==================== 1. Khởi tạo Auction ====================

    @Nested
    @DisplayName("1. Khởi tạo Auction")
    class InitializationTests {

        @Test
        @DisplayName("Auction mới có trạng thái OPEN")
        void newAuction_shouldBeOpen() {
            assertEquals(AuctionState.OPEN, auction.getState());
        }

        @Test
        @DisplayName("Auction mới không có bid nào")
        void newAuction_shouldHaveNoBids() {
            assertTrue(auction.getBidList().isEmpty());
        }

        @Test
        @DisplayName("Auction mới không có participant")
        void newAuction_shouldHaveNoParticipants() {
            assertTrue(auction.getParticipants().isEmpty());
        }

        @Test
        @DisplayName("Auction mới có item đúng")
        void newAuction_shouldHaveCorrectItem() {
            assertEquals(testItem, auction.getItem());
            assertEquals("Laptop Gaming", auction.getItem().getName());
        }

        @Test
        @DisplayName("Auction mới có owner đúng")
        void newAuction_shouldHaveCorrectOwner() {
            assertEquals("seller01", auction.getOwnerUsername());
        }

        @Test
        @DisplayName("Auction mới isActive() = true")
        void newAuction_shouldBeActive() {
            assertTrue(auction.isActive());
        }

        @Test
        @DisplayName("findHighestBid() trả về sentinel khi chưa có bid")
        void newAuction_findHighestBid_shouldReturnSentinel() {
            Bid sentinel = auction.findHighestBid();
            assertEquals(0, sentinel.getBid());
            assertNull(sentinel.getBidderUsername());
        }
    }

    // ==================== 2. Quản lý trạng thái (Lifecycle) ====================

    @Nested
    @DisplayName("2. Quản lý trạng thái Lifecycle")
    class LifecycleTests {

        @Test
        @DisplayName("start() chuyển OPEN → RUNNING")
        void start_fromOpen_shouldBecomeRunning() {
            auction.start();
            assertEquals(AuctionState.RUNNING, auction.getState());
        }

        @Test
        @DisplayName("start() từ RUNNING → ném IllegalStateException")
        void start_fromRunning_shouldThrow() {
            auction.start();
            assertThrows(IllegalStateException.class, () -> auction.start());
        }

        @Test
        @DisplayName("start() từ FINISHED → ném IllegalStateException")
        void start_fromFinished_shouldThrow() {
            auction.conclude();
            assertThrows(IllegalStateException.class, () -> auction.start());
        }

        @Test
        @DisplayName("conclude() chuyển sang FINISHED")
        void conclude_shouldBecomeFinished() {
            auction.conclude();
            assertEquals(AuctionState.FINISHED, auction.getState());
            assertFalse(auction.isActive());
        }

        @Test
        @DisplayName("conclude() 2 lần → ném IllegalStateException")
        void conclude_twice_shouldThrow() {
            auction.conclude();
            assertThrows(IllegalStateException.class, () -> auction.conclude());
        }
    }

    // ==================== 3. Tham gia đấu giá (addParticipant) ====================

    @Nested
    @DisplayName("3. Tham gia đấu giá")
    class ParticipantTests {

        @Test
        @DisplayName("Thêm participant thành công")
        void addParticipant_validUser_shouldSucceed() throws Exception {
            auction.addParticipant("bidder01");
            assertTrue(auction.getParticipants().contains("bidder01"));
            assertEquals(1, auction.getParticipants().size());
        }

        @Test
        @DisplayName("Owner không thể tham gia auction của mình")
        void addParticipant_owner_shouldThrowOwnerException() {
            assertThrows(AuctionClientIsOwnerException.class,
                    () -> auction.addParticipant("seller01"));
        }

        @Test
        @DisplayName("Không thể đăng ký 2 lần")
        void addParticipant_duplicate_shouldThrowAlreadyRegistered() throws Exception {
            auction.addParticipant("bidder01");
            assertThrows(AuctionAlreadyRegisteredException.class,
                    () -> auction.addParticipant("bidder01"));
        }

        @Test
        @DisplayName("Không thể tham gia auction đã FINISHED")
        void addParticipant_finishedAuction_shouldThrow() {
            auction.conclude();
            assertThrows(IllegalStateException.class,
                    () -> auction.addParticipant("bidder01"));
        }

        @Test
        @DisplayName("Không thể tham gia auction đã CANCELED")
        void addParticipant_canceledAuction_shouldThrow() throws Exception {
            auction.cancel("seller01");
            assertThrows(IllegalStateException.class,
                    () -> auction.addParticipant("bidder01"));
        }
    }

    // ==================== 4. Rời đấu giá (removeParticipant) ====================

    @Nested
    @DisplayName("4. Rời đấu giá")
    class RemoveParticipantTests {

        @Test
        @DisplayName("Rời đấu giá thành công khi không giữ bid cao nhất")
        void removeParticipant_noBids_shouldSucceed() throws Exception {
            auction.addParticipant("bidder01");
            auction.removeParticipant("bidder01");
            assertFalse(auction.getParticipants().contains("bidder01"));
        }

        @Test
        @DisplayName("Không thể rời nếu chưa đăng ký")
        void removeParticipant_notRegistered_shouldThrow() {
            assertThrows(AuctionNotRegisteredException.class,
                    () -> auction.removeParticipant("stranger"));
        }

        @Test
        @DisplayName("Không thể rời nếu đang giữ bid cao nhất")
        void removeParticipant_highestBidder_shouldThrow() throws Exception {
            auction.addParticipant("bidder01");
            Bid bid = new Bid(new Date(), 150.0f, "bidder01");
            auction.placeBid(bid, "bidder01");

            assertThrows(AuctionHighBidException.class,
                    () -> auction.removeParticipant("bidder01"));
        }

        @Test
        @DisplayName("Có thể rời nếu đã bị outbid")
        void removeParticipant_outbid_shouldSucceed() throws Exception {
            auction.addParticipant("bidder01");
            auction.addParticipant("bidder02");

            auction.placeBid(new Bid(new Date(), 150.0f, "bidder01"), "bidder01");
            auction.placeBid(new Bid(new Date(), 200.0f, "bidder02"), "bidder02");

            // bidder01 không còn giữ bid cao nhất → có thể rời
            assertDoesNotThrow(() -> auction.removeParticipant("bidder01"));
        }
    }

    // ==================== 5. Đặt giá thầu (placeBid) ====================

    @Nested
    @DisplayName("5. Đặt giá thầu")
    class PlaceBidTests {

        @BeforeEach
        void registerBidder() throws Exception {
            auction.addParticipant("bidder01");
            auction.addParticipant("bidder02");
        }

        @Test
        @DisplayName("Đặt bid hợp lệ thành công")
        void placeBid_validBid_shouldSucceed() throws Exception {
            Bid bid = new Bid(new Date(), 150.0f, "bidder01");
            auction.placeBid(bid, "bidder01");

            assertEquals(1, auction.getBidList().size());
            assertEquals(150.0f, auction.findHighestBidAmount());
        }

        @Test
        @DisplayName("Đặt bid tự động chuyển OPEN → RUNNING")
        void placeBid_firstBid_shouldTransitionToRunning() throws Exception {
            assertEquals(AuctionState.OPEN, auction.getState());

            Bid bid = new Bid(new Date(), 150.0f, "bidder01");
            auction.placeBid(bid, "bidder01");

            assertEquals(AuctionState.RUNNING, auction.getState());
        }

        @Test
        @DisplayName("Bid thấp hơn hoặc bằng giá hiện tại → ném AuctionLowBidException")
        void placeBid_lowBid_shouldThrow() {
            // Starting price = 100.0f, bid 50.0f → quá thấp
            Bid lowBid = new Bid(new Date(), 50.0f, "bidder01");
            assertThrows(AuctionLowBidException.class,
                    () -> auction.placeBid(lowBid, "bidder01"));
        }

        @Test
        @DisplayName("Bid bằng giá hiện tại → ném AuctionLowBidException")
        void placeBid_equalBid_shouldThrow() {
            Bid equalBid = new Bid(new Date(), 100.0f, "bidder01");
            assertThrows(AuctionLowBidException.class,
                    () -> auction.placeBid(equalBid, "bidder01"));
        }

        @Test
        @DisplayName("Owner không thể đặt bid trên auction của mình")
        void placeBid_byOwner_shouldThrowOwnerException() {
            Bid bid = new Bid(new Date(), 200.0f, "seller01");
            assertThrows(AuctionClientIsOwnerException.class,
                    () -> auction.placeBid(bid, "seller01"));
        }

        @Test
        @DisplayName("Người chưa đăng ký không thể bid")
        void placeBid_unregistered_shouldThrowNotRegistered() {
            Bid bid = new Bid(new Date(), 200.0f, "stranger");
            assertThrows(AuctionNotRegisteredException.class,
                    () -> auction.placeBid(bid, "stranger"));
        }

        @Test
        @DisplayName("Không thể bid khi auction đã FINISHED")
        void placeBid_finishedAuction_shouldThrow() {
            auction.conclude();
            Bid bid = new Bid(new Date(), 200.0f, "bidder01");
            assertThrows(IllegalStateException.class,
                    () -> auction.placeBid(bid, "bidder01"));
        }

        @Test
        @DisplayName("Không thể bid khi auction đã CANCELED")
        void placeBid_canceledAuction_shouldThrow() throws Exception {
            auction.cancel("seller01");
            Bid bid = new Bid(new Date(), 200.0f, "bidder01");
            assertThrows(IllegalStateException.class,
                    () -> auction.placeBid(bid, "bidder01"));
        }

        @Test
        @DisplayName("Nhiều bid liên tiếp tăng dần → bid list sắp xếp đúng")
        void placeBid_multipleBids_shouldMaintainOrder() throws Exception {
            auction.placeBid(new Bid(new Date(), 150.0f, "bidder01"), "bidder01");
            auction.placeBid(new Bid(new Date(), 200.0f, "bidder02"), "bidder02");
            auction.placeBid(new Bid(new Date(), 250.0f, "bidder01"), "bidder01");

            assertEquals(3, auction.getBidList().size());
            assertEquals(250.0f, auction.findHighestBidAmount());
            assertEquals("bidder01", auction.findHighestBid().getBidderUsername());
        }

        @Test
        @DisplayName("Bid mới phải cao hơn bid trước đó")
        void placeBid_lowerThanPrevious_shouldThrow() throws Exception {
            auction.placeBid(new Bid(new Date(), 200.0f, "bidder01"), "bidder01");

            // Bid 150 < 200 (highest) → phải ném exception
            Bid lowBid = new Bid(new Date(), 150.0f, "bidder02");
            assertThrows(AuctionLowBidException.class,
                    () -> auction.placeBid(lowBid, "bidder02"));
        }
    }

    // ==================== 6. Hủy đấu giá (cancel) ====================

    @Nested
    @DisplayName("6. Hủy đấu giá")
    class CancelTests {

        @Test
        @DisplayName("Owner hủy thành công khi chưa có bid")
        void cancel_byOwner_noBids_shouldSucceed() throws Exception {
            auction.cancel("seller01");
            assertEquals(AuctionState.CANCELED, auction.getState());
            assertFalse(auction.isActive());
        }

        @Test
        @DisplayName("Không phải owner → ném AuctionNotOwnerException")
        void cancel_byNonOwner_shouldThrow() {
            assertThrows(AuctionNotOwnerException.class,
                    () -> auction.cancel("bidder01"));
        }

        @Test
        @DisplayName("Đã có bid → không thể hủy")
        void cancel_withBids_shouldThrowActiveException() throws Exception {
            auction.addParticipant("bidder01");
            auction.placeBid(new Bid(new Date(), 150.0f, "bidder01"), "bidder01");

            assertThrows(AuctionActiveException.class,
                    () -> auction.cancel("seller01"));
        }
    }

    // ==================== 7. Kết thúc đấu giá (conclude) ====================

    @Nested
    @DisplayName("7. Kết thúc và xác định người thắng")
    class ConcludeTests {

        @Test
        @DisplayName("Kết thúc không có bid → không có winner")
        void conclude_noBids_noWinner() {
            auction.conclude();
            assertNull(auction.getWinnerUsername());
        }

        @Test
        @DisplayName("Kết thúc có bid → winner là người bid cao nhất")
        void conclude_withBids_winnerIsHighestBidder() throws Exception {
            auction.addParticipant("bidder01");
            auction.addParticipant("bidder02");

            auction.placeBid(new Bid(new Date(), 150.0f, "bidder01"), "bidder01");
            auction.placeBid(new Bid(new Date(), 250.0f, "bidder02"), "bidder02");

            auction.conclude();

            assertEquals(AuctionState.FINISHED, auction.getState());
            assertEquals("bidder02", auction.getWinnerUsername());
        }

        @Test
        @DisplayName("getWinnerUsername() trả null khi auction chưa FINISHED")
        void getWinner_whileRunning_shouldReturnNull() throws Exception {
            auction.addParticipant("bidder01");
            auction.placeBid(new Bid(new Date(), 150.0f, "bidder01"), "bidder01");

            // Auction đang RUNNING, chưa conclude
            assertNull(auction.getWinnerUsername());
        }
    }

    // ==================== 8. Observer Pattern ====================

    @Nested
    @DisplayName("8. Observer Pattern")
    class ObserverTests {

        @Test
        @DisplayName("Observer nhận thông báo khi có bid mới")
        void observer_shouldBeNotified_onNewBid() throws Exception {
            AtomicReference<Bid> receivedBid = new AtomicReference<>();
            auction.addObserver(receivedBid::set);

            auction.addParticipant("bidder01");
            Bid bid = new Bid(new Date(), 200.0f, "bidder01");
            auction.placeBid(bid, "bidder01");

            assertNotNull(receivedBid.get());
            assertEquals(200.0f, receivedBid.get().getBid());
            assertEquals("bidder01", receivedBid.get().getBidderUsername());
        }

        @Test
        @DisplayName("Observer bị remove không nhận thông báo nữa")
        void removedObserver_shouldNotBeNotified() throws Exception {
            AtomicReference<Bid> receivedBid = new AtomicReference<>();
            BidObserver observer = receivedBid::set;

            auction.addObserver(observer);
            auction.removeObserver(observer);

            auction.addParticipant("bidder01");
            auction.placeBid(new Bid(new Date(), 200.0f, "bidder01"), "bidder01");

            assertNull(receivedBid.get());
        }

        @Test
        @DisplayName("Thêm observer null → không crash")
        void addNullObserver_shouldNotThrow() {
            assertDoesNotThrow(() -> auction.addObserver(null));
        }
    }

    // ==================== 9. Tình huống tích hợp ====================

    @Nested
    @DisplayName("9. Tình huống đấu giá hoàn chỉnh")
    class IntegrationScenarioTests {

        @Test
        @DisplayName("Kịch bản đấu giá đầy đủ: tạo → tham gia → bid → kết thúc")
        void fullAuctionScenario() throws Exception {
            // 1. Tạo auction (đã tạo trong setUp)
            assertEquals(AuctionState.OPEN, auction.getState());

            // 2. Bidder đăng ký
            auction.addParticipant("alice");
            auction.addParticipant("bob");
            assertEquals(2, auction.getParticipants().size());

            // 3. Alice đặt bid đầu tiên → OPEN → RUNNING
            auction.placeBid(new Bid(new Date(), 120.0f, "alice"), "alice");
            assertEquals(AuctionState.RUNNING, auction.getState());

            // 4. Bob outbid Alice
            auction.placeBid(new Bid(new Date(), 180.0f, "bob"), "bob");
            assertEquals("bob", auction.findHighestBid().getBidderUsername());

            // 5. Alice outbid lại
            auction.placeBid(new Bid(new Date(), 250.0f, "alice"), "alice");
            assertEquals(250.0f, auction.findHighestBidAmount());

            // 6. Kết thúc → Alice thắng
            auction.conclude();
            assertEquals(AuctionState.FINISHED, auction.getState());
            assertEquals("alice", auction.getWinnerUsername());
            assertFalse(auction.isActive());

            // 7. Tổng số bid = 3
            assertEquals(3, auction.getBidList().size());
        }

        @Test
        @DisplayName("Kịch bản hủy đấu giá: tạo → tham gia → hủy trước khi bid")
        void cancelScenario_beforeBids() throws Exception {
            auction.addParticipant("alice");

            // Owner hủy trước khi có bid
            auction.cancel("seller01");
            assertEquals(AuctionState.CANCELED, auction.getState());
            assertFalse(auction.isActive());

            // Không thể bid sau khi hủy
            assertThrows(IllegalStateException.class,
                    () -> auction.placeBid(
                            new Bid(new Date(), 200.0f, "alice"), "alice"));
        }
    }
}
