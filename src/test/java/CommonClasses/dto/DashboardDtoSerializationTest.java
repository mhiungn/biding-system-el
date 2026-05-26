package CommonClasses.dto;

import CommonClasses.Items.Electronics;
import CommonClasses.Items.Item;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class DashboardDtoSerializationTest {

    @Test
    void dashboardPagePayloadIsSerializable() {
        Item item = new Electronics(1000f, "Laptop", "New");
        DashboardAuctionRow row = new DashboardAuctionRow(
                1,
                "OPEN",
                new Date(),
                new Date(System.currentTimeMillis() + 60_000),
                item,
                2,
                50f);
        DashboardPageResult page = new DashboardPageResult(List.of(row), 1);

        assertDoesNotThrow(() -> serialize(page));
    }

    @Test
    void dashboardStatsPayloadIsSerializable() {
        assertDoesNotThrow(() -> serialize(new DashboardStats(1, 2, 3)));
    }

    @Test
    void sellerAuctionPayloadIsSerializable() {
        SellerAuctionRowDTO row = new SellerAuctionRowDTO(
                1,
                "Laptop",
                "FINISHED",
                new Date(),
                new Date(),
                1200f,
                3,
                "bidder",
                List.of("uploads/items/item-001/image.png"));

        assertDoesNotThrow(() -> serialize(List.of(row)));
    }

    @Test
    void userProfileStatsPayloadIsSerializable() {
        UserProfileStatsDTO stats = new UserProfileStatsDTO(3, 1, 33.3, 1, 2, 1, 1200, new Date());

        assertDoesNotThrow(() -> serialize(stats));
    }

    @Test
    void pushPayloadsAreSerializable() {
        Item item = new Electronics(1000f, "Laptop", "New");
        DashboardAuctionRow row = new DashboardAuctionRow(
                1,
                "OPEN",
                new Date(),
                new Date(System.currentTimeMillis() + 60_000),
                item,
                2,
                50f);

        assertDoesNotThrow(() -> serialize(new AuctionUpdatePushDTO(1, row, "BID_PLACED")));
        assertDoesNotThrow(() -> serialize(new NotificationPushDTO("bidder", 1, "OUTBID", 2)));
        assertDoesNotThrow(() -> serialize(new WalletUpdatePushDTO(
                "bidder",
                WalletDTO.success("bidder", 100_000L, 1_100L, 0L),
                "BID_HOLD_UPDATED")));
    }

    private void serialize(Object payload) throws Exception {
        try (ObjectOutputStream out = new ObjectOutputStream(new ByteArrayOutputStream())) {
            out.writeObject(payload);
        }
    }
}
