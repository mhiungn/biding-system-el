package CommonClasses.dto;

import java.io.Serializable;

/**
 * DTO cho thông báo Auto-Bid đẩy qua Socket (Server → Client).
 * <p>
 * Được gửi khi hệ thống tự động đặt giá thay cho user hoặc khi
 * auto-bid đạt giới hạn maxBid và bị vô hiệu hóa.
 * </p>
 *
 * <h3>Các loại thông báo ({@code type}):</h3>
 * <ul>
 *   <li>{@code AUTO_BID_PLACED} — Hệ thống đã tự động đặt giá thành công.</li>
 *   <li>{@code AUTO_BID_LIMIT_REACHED} — Giá hiện tại vượt quá maxBid, auto-bid dừng lại.</li>
 *   <li>{@code AUTO_BID_CANCELLED} — User đã hủy cấu hình auto-bid.</li>
 *   <li>{@code AUTO_BID_REGISTERED} — User đã đăng ký auto-bid thành công.</li>
 * </ul>
 */
public class AutoBidNotificationDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Username nhận thông báo. */
    private final String username;

    /** ID phiên đấu giá liên quan. */
    private final int auctionId;

    /** Loại thông báo: AUTO_BID_PLACED, AUTO_BID_LIMIT_REACHED, etc. */
    private final String type;

    /** Số tiền đã đặt (nếu type = PLACED) hoặc giá hiện tại (nếu type = LIMIT_REACHED). */
    private final float bidAmount;

    /** Giới hạn maxBid của cấu hình auto-bid. */
    private final float maxBid;

    /** Thông điệp mô tả chi tiết sự kiện. */
    private final String message;

    /**
     * Khởi tạo DTO thông báo Auto-Bid.
     *
     * @param username  username nhận thông báo
     * @param auctionId ID phiên đấu giá
     * @param type      loại thông báo
     * @param bidAmount số tiền liên quan
     * @param maxBid    giới hạn maxBid
     * @param message   thông điệp chi tiết
     */
    public AutoBidNotificationDTO(String username, int auctionId, String type,
                                  float bidAmount, float maxBid, String message) {
        this.username = username;
        this.auctionId = auctionId;
        this.type = type;
        this.bidAmount = bidAmount;
        this.maxBid = maxBid;
        this.message = message;
    }

    // ========================== Getters ==========================

    public String getUsername() {
        return username;
    }

    public int getAuctionId() {
        return auctionId;
    }

    public String getType() {
        return type;
    }

    public float getBidAmount() {
        return bidAmount;
    }

    public float getMaxBid() {
        return maxBid;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "AutoBidNotificationDTO{" +
                "username='" + username + '\'' +
                ", auctionId=" + auctionId +
                ", type='" + type + '\'' +
                ", bidAmount=" + bidAmount +
                ", maxBid=" + maxBid +
                ", message='" + message + '\'' +
                '}';
    }
}
