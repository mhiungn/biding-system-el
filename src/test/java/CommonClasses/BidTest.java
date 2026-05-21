package CommonClasses;

import org.junit.jupiter.api.*;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link Bid} class.
 */
@DisplayName("Bid - Unit Tests")
class BidTest {

    @Test
    @DisplayName("Tạo Bid với đầy đủ thông tin")
    void constructor_shouldSetAllFields() {
        Date now = new Date();
        Bid bid = new Bid(now, 150.0f, "user01");

        assertEquals(now, bid.getCreatedAt());
        assertEquals(150.0f, bid.getBid());
        assertEquals("user01", bid.getBidderUsername());
    }

    @Test
    @DisplayName("Sentinel Bid (null, 0, null) cho trường hợp không có bid")
    void sentinelBid_shouldHaveZeroAndNulls() {
        Bid sentinel = new Bid(null, 0, null);

        assertNull(sentinel.getCreatedAt());
        assertEquals(0, sentinel.getBid());
        assertNull(sentinel.getBidderUsername());
    }

    @Test
    @DisplayName("Hai Bid giống nhau → equals = true")
    void equals_sameBids_shouldBeTrue() {
        Date now = new Date();
        Bid bid1 = new Bid(now, 200.0f, "alice");
        Bid bid2 = new Bid(now, 200.0f, "alice");

        assertEquals(bid1, bid2);
        assertEquals(bid1.hashCode(), bid2.hashCode());
    }

    @Test
    @DisplayName("Hai Bid khác nhau → equals = false")
    void equals_differentBids_shouldBeFalse() {
        Date now = new Date();
        Bid bid1 = new Bid(now, 200.0f, "alice");
        Bid bid2 = new Bid(now, 300.0f, "bob");

        assertNotEquals(bid1, bid2);
    }

    @Test
    @DisplayName("Setter hoạt động đúng")
    void setters_shouldUpdateFields() {
        Bid bid = new Bid(null, 0, null);
        Date now = new Date();

        bid.setCreatedAt(now);
        bid.setBid(500.0f);
        bid.setBidderUsername("charlie");

        assertEquals(now, bid.getCreatedAt());
        assertEquals(500.0f, bid.getBid());
        assertEquals("charlie", bid.getBidderUsername());
    }

    @Test
    @DisplayName("toString() trả về chuỗi có ý nghĩa")
    void toString_shouldContainFieldValues() {
        Bid bid = new Bid(new Date(), 123.0f, "testUser");
        String str = bid.toString();

        assertTrue(str.contains("123.0"));
        assertTrue(str.contains("testUser"));
    }
}
