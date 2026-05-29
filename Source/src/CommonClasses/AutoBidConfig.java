package CommonClasses;

import java.io.Serializable;
import java.util.Date;
import java.util.Objects;

/**
 * Cấu hình Auto-Bid (Đấu giá tự động) cho một người dùng trên một phiên đấu giá.
 * <p>
 * Mỗi {@code AutoBidConfig} đại diện cho ý định tự động đặt giá của một Bidder:
 * hệ thống sẽ tự động đặt giá thay cho Bidder với bước giá {@code increment}
 * cho đến khi đạt giới hạn {@code maxBid}.
 * </p>
 *
 * <h3>PriorityQueue Ordering:</h3>
 * Implements {@link Comparable} để sắp xếp trong {@link java.util.PriorityQueue}:
 * <ol>
 *   <li><b>maxBid giảm dần</b> — người sẵn sàng trả giá cao hơn được ưu tiên trước.</li>
 *   <li><b>createdAt tăng dần</b> — nếu cùng maxBid, người đăng ký sớm hơn được ưu tiên.</li>
 * </ol>
 *
 * <h3>Serializable:</h3>
 * Hỗ trợ truyền qua Socket trong kiến trúc Client-Server.
 *
 * @see java.util.PriorityQueue
 */
public class AutoBidConfig implements Comparable<AutoBidConfig>, Serializable {

    private static final long serialVersionUID = 1L;

    // ========================== Attributes ==========================

    /** Username của Bidder đăng ký auto-bid. */
    private final String username;

    /** ID của phiên đấu giá áp dụng auto-bid. */
    private final int auctionId;

    /** Giá tối đa mà Bidder sẵn sàng trả (giới hạn trên). */
    private final float maxBid;

    /** Bước giá — số tiền tự động cộng thêm vào giá hiện tại khi đặt giá. */
    private final float increment;

    /** Thời điểm đăng ký auto-bid (dùng để phân chia ưu tiên khi cùng maxBid). */
    private final Date createdAt;

    /** Trạng thái hoạt động: {@code true} nếu auto-bid còn hiệu lực. */
    private volatile boolean active;

    // ========================== Constructor ==========================

    /**
     * Khởi tạo một cấu hình Auto-Bid mới.
     *
     * @param username  username của Bidder
     * @param auctionId ID phiên đấu giá
     * @param maxBid    giá tối đa sẵn sàng trả
     * @param increment bước giá tự động cộng thêm
     * @throws IllegalArgumentException nếu maxBid hoặc increment <= 0
     */
    public AutoBidConfig(String username, int auctionId, float maxBid, float increment) {
        if (maxBid <= 0) {
            throw new IllegalArgumentException("maxBid phải lớn hơn 0");
        }
        if (increment <= 0) {
            throw new IllegalArgumentException("increment phải lớn hơn 0");
        }
        this.username = username;
        this.auctionId = auctionId;
        this.maxBid = maxBid;
        this.increment = increment;
        this.createdAt = new Date();
        this.active = true;
    }

    // ========================== PriorityQueue Ordering ==========================

    /**
     * So sánh để sắp xếp trong PriorityQueue.
     * <p>
     * Java PriorityQueue là <b>min-heap</b> (phần tử nhỏ nhất ở đầu),
     * nên để người có {@code maxBid} cao nhất được ưu tiên (poll ra trước),
     * ta đảo thứ tự so sánh maxBid (other.maxBid - this.maxBid).
     * </p>
     * <p>
     * Nếu cùng maxBid, người đăng ký sớm hơn (createdAt nhỏ hơn) được ưu tiên.
     * </p>
     *
     * @param other cấu hình auto-bid khác để so sánh
     * @return giá trị âm nếu this được ưu tiên hơn other
     */
    @Override
    public int compareTo(AutoBidConfig other) {
        // maxBid giảm dần (cao hơn = ưu tiên hơn = giá trị compareTo nhỏ hơn)
        int cmp = Float.compare(other.maxBid, this.maxBid);
        if (cmp != 0) {
            return cmp;
        }
        // createdAt tăng dần (sớm hơn = ưu tiên hơn)
        return this.createdAt.compareTo(other.createdAt);
    }

    // ========================== Business Methods ==========================

    /**
     * Hủy kích hoạt auto-bid này.
     * Sau khi gọi, hệ thống sẽ không tự động đặt giá thay cho user nữa.
     */
    public void deactivate() {
        this.active = false;
    }

    /**
     * Kiểm tra xem auto-bid này có thể đặt giá ở mức giá cho trước không.
     *
     * @param currentPrice giá hiện tại cao nhất của phiên đấu giá
     * @return {@code true} nếu currentPrice + increment <= maxBid và auto-bid còn active
     */
    public boolean canBidAt(float currentPrice) {
        return active && (currentPrice + increment) <= maxBid;
    }

    /**
     * Tính mức giá tự động sẽ đặt dựa trên giá hiện tại.
     *
     * @param currentPrice giá hiện tại cao nhất
     * @return giá auto-bid = currentPrice + increment
     */
    public float calculateAutoBidAmount(float currentPrice) {
        return currentPrice + increment;
    }

    // ========================== Getters ==========================

    public String getUsername() {
        return username;
    }

    public int getAuctionId() {
        return auctionId;
    }

    public float getMaxBid() {
        return maxBid;
    }

    public float getIncrement() {
        return increment;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public boolean isActive() {
        return active;
    }

    // ========================== Object Overrides ==========================

    /**
     * Hai AutoBidConfig bằng nhau nếu cùng username và cùng auctionId
     * (mỗi user chỉ có tối đa 1 cấu hình auto-bid trên mỗi phiên đấu giá).
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AutoBidConfig that = (AutoBidConfig) o;
        return auctionId == that.auctionId
                && Objects.equals(username, that.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, auctionId);
    }

    @Override
    public String toString() {
        return "AutoBidConfig{" +
                "username='" + username + '\'' +
                ", auctionId=" + auctionId +
                ", maxBid=" + maxBid +
                ", increment=" + increment +
                ", active=" + active +
                ", createdAt=" + createdAt +
                '}';
    }
}
