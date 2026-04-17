package Server.dao;

import CommonClasses.Bid;
import CommonClasses.Item;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/**
 * Bản chụp (snapshot) có thể serialize của trạng thái {@link CommonClasses.Auction}.
 * <p>
 * Lớp {@link CommonClasses.Auction} chứa các trường không thể serialize
 * ({@link java.util.Timer}, {@code AuctionCountdownTask}, đối tượng {@code Client})
 * vì chúng gắn liền với runtime của server và không thể lưu trữ trực tiếp.
 * Lớp này chỉ lưu lại dữ liệu bền vững, có thể serialize, cần thiết để
 * khôi phục phiên đấu giá sau khi server khởi động lại.
 * </p>
 *
 * <h3>Những gì ĐƯỢC lưu:</h3>
 * <ul>
 *   <li>Định danh phiên: {@code id}, {@code clientOwner}</li>
 *   <li>Thời gian: {@code createdAt}, {@code terminateAt}</li>
 *   <li>Cấu hình: {@code type} (Time_Fixed / Time_With_Reset)</li>
 *   <li>Trạng thái: {@code status} (OPEN, RUNNING, FINISHED, PAID, CANCELED)</li>
 *   <li>Lịch sử bid: danh sách đầy đủ các đối tượng {@link Bid}</li>
 *   <li>Thông tin sản phẩm: {@link Item} đang được đấu giá</li>
 *   <li>Danh sách người tham gia: username của các bidder đã đăng ký</li>
 * </ul>
 *
 * <h3>Những gì KHÔNG được lưu (chỉ tồn tại khi runtime):</h3>
 * <ul>
 *   <li>{@code Timer} — được tạo lại khi load dựa trên {@code terminateAt}</li>
 *   <li>{@code AuctionCountdownTask} — được tạo lại khi load</li>
 *   <li>Đối tượng {@code Client} — client sẽ kết nối lại sau restart</li>
 * </ul>
 *
 * @see AuctionDAO
 * @see CommonClasses.Auction
 */
public class AuctionSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    // ========================== Thuộc tính ==========================

    /** ID duy nhất của phiên đấu giá. */
    private int auctionId;

    /** Username của seller đã tạo phiên đấu giá này. */
    private String clientOwner;

    /** Thời điểm tạo phiên đấu giá. */
    private Date createdAt;

    /** Thời điểm kết thúc theo lịch trình. */
    private Date terminateAt;

    /**
     * Loại phiên đấu giá: {@code "Time_Fixed"} hoặc {@code "Time_With_Reset"}.
     * Dùng để xác định tạo timer task nào khi khôi phục.
     */
    private String type;

    /**
     * Trạng thái vòng đời của phiên đấu giá.
     * Luồng chuyển: OPEN → RUNNING → FINISHED → PAID / CANCELED
     */
    private String status;

    /** Sản phẩm đang được đấu giá. */
    private Item item;

    /**
     * Danh sách đầy đủ các bid trong phiên, sắp xếp giá cao nhất đầu tiên.
     * Lưu trữ toàn bộ lịch sử bid cho tính năng biểu đồ giá và kiểm toán.
     */
    private LinkedList<Bid> bidList;

    /**
     * Username của các client đã đăng ký tham gia.
     * Lưu dưới dạng chuỗi thay vì đối tượng Client vì Client chỉ tồn tại khi runtime.
     */
    private List<String> registeredUsernames;

    /** Phiên đấu giá có đang trong giai đoạn đếm ngược khi được lưu hay không. */
    private boolean wasInCountDown;

    // ========================== Constructor ==========================

    /**
     * Constructor mặc định cho deserialization.
     */
    public AuctionSnapshot() {
        this.bidList = new LinkedList<>();
        this.registeredUsernames = new ArrayList<>();
        this.status = "OPEN";
    }

    /**
     * Tạo snapshot từ các trường riêng lẻ của phiên đấu giá.
     *
     * @param auctionId           ID duy nhất của phiên
     * @param clientOwner         username của người tạo phiên
     * @param createdAt           thời điểm tạo
     * @param terminateAt         thời điểm kết thúc
     * @param type                loại phiên (Time_Fixed / Time_With_Reset)
     * @param status              trạng thái hiện tại (OPEN / RUNNING / FINISHED / PAID / CANCELED)
     * @param item                sản phẩm đang đấu giá
     * @param bidList             danh sách bid (giá cao nhất đầu tiên)
     * @param registeredUsernames username của người tham gia
     * @param wasInCountDown      giai đoạn đếm ngược có đang hoạt động không
     */
    public AuctionSnapshot(int auctionId, String clientOwner, Date createdAt,
                           Date terminateAt, String type, String status,
                           Item item, LinkedList<Bid> bidList,
                           List<String> registeredUsernames, boolean wasInCountDown) {
        this.auctionId = auctionId;
        this.clientOwner = clientOwner;
        this.createdAt = createdAt;
        this.terminateAt = terminateAt;
        this.type = type;
        this.status = status;
        this.item = item;
        this.bidList = (bidList != null) ? new LinkedList<>(bidList) : new LinkedList<>();
        this.registeredUsernames = (registeredUsernames != null)
                ? new ArrayList<>(registeredUsernames) : new ArrayList<>();
        this.wasInCountDown = wasInCountDown;
    }

    // ========================== Getter & Setter ==========================

    public int getAuctionId() {
        return auctionId;
    }

    public void setAuctionId(int auctionId) {
        this.auctionId = auctionId;
    }

    public String getClientOwner() {
        return clientOwner;
    }

    public void setClientOwner(String clientOwner) {
        this.clientOwner = clientOwner;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getTerminateAt() {
        return terminateAt;
    }

    public void setTerminateAt(Date terminateAt) {
        this.terminateAt = terminateAt;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
    }

    public LinkedList<Bid> getBidList() {
        return bidList;
    }

    public void setBidList(LinkedList<Bid> bidList) {
        this.bidList = bidList;
    }

    public List<String> getRegisteredUsernames() {
        return registeredUsernames;
    }

    public void setRegisteredUsernames(List<String> registeredUsernames) {
        this.registeredUsernames = registeredUsernames;
    }

    public boolean wasInCountDown() {
        return wasInCountDown;
    }

    public void setWasInCountDown(boolean wasInCountDown) {
        this.wasInCountDown = wasInCountDown;
    }

    // ========================== Phương thức Tiện ích ==========================

    /**
     * Trả về giá cao nhất hiện tại, hoặc giá khởi điểm nếu chưa có bid.
     *
     * @return giá hiện tại cao nhất
     */
    public float getCurrentPrice() {
        if (bidList != null && !bidList.isEmpty()) {
            return bidList.getFirst().getBid();
        }
        return (item != null) ? item.getStartingPrice() : 0f;
    }

    /**
     * Trả về username của người đang dẫn đầu, hoặc {@code null} nếu chưa có bid.
     *
     * @return username của người đấu giá cao nhất
     */
    public String getHighestBidder() {
        if (bidList != null && !bidList.isEmpty()) {
            return bidList.getFirst().getBidderUsername();
        }
        return null;
    }

    /**
     * Kiểm tra phiên đấu giá đã kết thúc chưa (FINISHED, PAID, hoặc CANCELED).
     *
     * @return {@code true} nếu phiên đã không còn hoạt động
     */
    public boolean isConcluded() {
        return "FINISHED".equals(status) || "PAID".equals(status) || "CANCELED".equals(status);
    }

    /**
     * Trả về tổng số bid đã đặt trong phiên.
     *
     * @return số lượng bid
     */
    public int getBidCount() {
        return (bidList != null) ? bidList.size() : 0;
    }

    @Override
    public String toString() {
        return "AuctionSnapshot{" +
                "id=" + auctionId +
                ", chủ='" + clientOwner + '\'' +
                ", sản phẩm='" + (item != null ? item.getName() : "null") + '\'' +
                ", trạng thái='" + status + '\'' +
                ", số bid=" + getBidCount() +
                ", giá hiện tại=" + getCurrentPrice() +
                ", kết thúc lúc=" + terminateAt +
                '}';
    }
}
