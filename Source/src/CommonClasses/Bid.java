//Donat Salihu
//Nikolaos Lintas
//Memli Restelica
//Philippos Kalatzis

package CommonClasses;

import java.io.Serializable;
import java.util.Date;
import java.util.Objects;

/**
 * Đại diện cho một lượt đặt giá (bid) của một người dùng trong phiên đấu giá.
 * <p>
 * Một {@code Bid} ghi nhận ba phần thông tin:
 * <ul>
 *   <li><b>createdAt</b> — thời điểm khi giá thầu được đặt.</li>
 *   <li><b>bid</b> — giá trị tiền tệ của giá thầu (số thực - float).</li>
 *   <li><b>bidderUsername</b> — tên đăng nhập của người dùng đã đặt mức giá này,
 *       nhằm định danh duy nhất người đặt.</li>
 * </ul>
 * Các bid được lưu trong một {@link java.util.LinkedList} bên trong mỗi {@link Auction},
 * sắp xếp từ cao nhất (đầu tiên) đến thấp nhất (cuối cùng). Lớp này implement
 * {@link Serializable} để có thể truyền tải qua mạng bên trong
 * các payload {@link Packets.PacketMessage}.
 * </p>
 *
 * <h3>Ví dụ sử dụng:</h3>
 * <pre>{@code
 *   Bid bid = new Bid(new Date(), 250.0f, "john_doe");
 *   float amount = bid.getBid();              // 250.0
 *   String who   = bid.getBidderUsername();    // "john_doe"
 * }</pre>
 *
 * @see Auction#addBid(Bid, Server.Client)
 * @see Auction#findHighestBid()
 */
public class Bid implements Serializable {

    // ========================== Thuộc tính ==========================

    /** Ngày/giờ lúc giá thầu này được tạo. */
    private Date createdAt;

    /** Giá trị tiền tệ của mức giá được đưa ra. */
    private float bid;

    /**
     * Tên đăng nhập của người dùng (client) đã đặt mức giá này.
     * Dùng làm mã định danh duy nhất vì mỗi tài khoản đều có username khác biệt.
     */
    private String bidderUsername;

    // ========================== Constructor ==========================

    /**
     * Khởi tạo một {@code Bid} mới với thời gian, số tiền và tên người dùng tương ứng.
     *
     * @param createdAt ngày/giờ thiết lập lượt đặt giá (có thể truyền {@code null} nếu là giá thầu "ảo"/sentinel)
     * @param bid       giá trị tiền mặt của lượt đặt
     * @param bidderUsername  username của người đấu giá (có thể {@code null} khi tạo sentinel bid)
     */
    public Bid(Date createdAt, float bid, String bidderUsername) {
        this.createdAt = createdAt;
        this.bid = bid;
        this.bidderUsername = bidderUsername;
    }

    // ========================== Getter & Setter ==========================

    /**
     * Trả về thời điểm mà giá thầu này được sinh ra.
     *
     * @return ngày và giờ khởi tạo (Date)
     */
    public Date getCreatedAt() {
        return createdAt;
    }

    /**
     * Cập nhật thời điểm khởi tạo của giá thầu này.
     *
     * @param createdAt thời gian tạo mới
     */
    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Trả về giá trị của mức giá thầu.
     *
     * @return số tiền đặt cược (số thực - float)
     */
    public float getBid() {
        return bid;
    }

    /**
     * Cập nhật số tiền đặt giá.
     *
     * @param bid số tiền thầu mới
     */
    public void setBid(float bid) {
        this.bid = bid;
    }

    /**
     * Trả về tên đăng nhập của người dùng đã đặt mức giá này.
     *
     * @return username của bidder, hoặc {@code null} nếu đây là bid rỗng (sentinel bid)
     */
    public String getBidderUsername() {
        return bidderUsername;
    }

    /**
     * Cập nhật username của người đặt giá.
     *
     * @param bidderUsername username mới
     */
    public void setBidderUsername(String bidderUsername) {
        this.bidderUsername = bidderUsername;
    }

    // ========================== Phương thức Định dạng / Tiện ích ==========================

    /**
     * Trả về chuỗi đại diện thân thiện cho giá thầu này.
     *
     * @return chuỗi định dạng hiển thị thời gian, số tiền và username
     */
    @Override
    public String toString() {
        return "Bid{" +
                "createdAt=" + createdAt +
                ", bid=" + bid +
                ", bidderUsername='" + bidderUsername + '\'' +
                '}';
    }

    /**
     * So sánh giá thầu này với một đối tượng khác xem có bằng nhau hay không.
     * Hai bid được coi là bằng nhau nếu ngày khởi tạo, số tiền đặt và username đều khớp.
     *
     * @param o đối tượng cần so sánh
     * @return {@code true} nếu cả 2 bid giống nhau hoàn toàn, {@code false} nếu không
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Bid bid1 = (Bid) o;
        return Float.compare(bid1.bid, bid) == 0 &&
                Objects.equals(createdAt, bid1.createdAt) &&
                Objects.equals(bidderUsername, bid1.bidderUsername);
    }

    /**
     * Sinh ra mã băm (hash code) dựa trên ngày giờ, số tiền và username.
     *
     * @return mã băm nguyên (int)
     */
    @Override
    public int hashCode() {
        return Objects.hash(createdAt, bid, bidderUsername);
    }
}
