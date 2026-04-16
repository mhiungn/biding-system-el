//Donat Salihu
//Nikolaos Lintas
//Memli Restelica
//Philippos Kalatzis

package CommonClasses;

import Packets.PacketMessage;
import Payload.*;
import Server.AuctionCountdownTask;
import Server.AuctionException.*;
import Server.AuctionTerminateTask;
import Server.Client;
import Server.Server;
import Server.ServerException.ServerNotClientOwnerException;

import java.io.IOException;
import java.io.Serializable;
import java.util.Date;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Timer;
import java.util.concurrent.atomic.AtomicInteger;

import static Packets.MessageType.*;

/**
 * Đại diện cho một phiên đấu giá đang diễn ra trong hệ thống.
 * <p>
 * Lớp {@code Auction} là đối tượng lõi (domain object) trung tâm ở phía server.
 * Nó đóng gói toàn bộ trạng thái và hành vi của một phiên đấu giá đơn lẻ:
 * <ul>
 * <li>Quyền sở hữu — client nào (xác định bằng IP/Username) đã tạo phiên này.</li>
 * <li>Sản phẩm bán — đối tượng {@link Item} có tên, mô tả, và giá khởi điểm.</li>
 * <li>Người tham gia — danh sách các đối tượng {@link Client} đăng ký tham gia đấu giá.</li>
 * <li>Quản lý giá (Bid) — danh sách các đối tượng {@link Bid}, được sắp xếp giảm dần (giá cao nhất đứng đầu).</li>
 * <li>Thời gian — lên lịch tự động kết thúc hoặc đếm ngược thông qua
 * {@link Timer}, {@link AuctionTerminateTask}, và {@link AuctionCountdownTask}.</li>
 * </ul>
 *
 * <h3>Các loại phiên đấu giá:</h3>
 * <dl>
 * <dt>{@code "Time_Fixed"}</dt>
 * <dd>Phiên kết thúc đúng vào một giờ cố định, bất chấp có ai đặt giá hay không.
 * Sử dụng {@link AuctionTerminateTask} để gọi thẳng hàm {@link #conclude()} khi đến giờ kết thúc.</dd>
 * <dt>{@code "Time_With_Reset"}</dt>
 * <dd>Phiên kết thúc bằng giai đoạn đếm ngược. Khi đến thời hạn,
 * {@link AuctionCountdownTask} bắt đầu quy trình "lần thứ nhất... lần thứ hai...".
 * Nếu có một bid mới được đặt trong lúc đếm ngược này, bộ đếm giờ sẽ
 * <strong>reset (đặt lại)</strong>,
 * tạo thêm cơ hội cho những người khác vào trả giá tiếp (Anti-sniping).</dd>
 * </dl>
 *
 * <h3>Lưu ý về An toàn Đa luồng (Thread safety):</h3>
 * Trường {@link #incrementer} dùng {@link AtomicInteger} để sinh ID tự động an toàn đa luồng.
 * Tuy nhiên, các trạng thái có thể biến đổi khác (bảo gồm các list, timer, v.v.)
 * <em>không</em> được cấp phép đồng bộ hóa nội bộ — server phải tự xử lý đồng bộ hóa (synchronize) từ bên ngoài.
 *
 * @see Item
 * @see Bid
 * @see Client
 * @see Server
 * @see AuctionCountdownTask
 * @see AuctionTerminateTask
 */
public class Auction implements Serializable {

    // ========================== Thuộc tính ==========================

    /**
     * Bộ đếm tự động tăng (an toàn đa luồng) dùng để sinh ID duy nhất cho phiên đấu giá.
     * Mỗi đối tượng {@code Auction} mới sẽ lấy ID tiếp theo từ bộ đếm này.
     */
    private final static AtomicInteger incrementer = new AtomicInteger();

    /**
     * Định danh duy nhất cho phiên đấu giá, được gán tự động khi khởi tạo.
     */
    private int id;

    /**
     * Tên đăng nhập (hoặc IP) của client tạo (sở hữu) phiên này.
     * Người sở hữu không được phép tự đặt giá trên phiên của mình, nhưng có thể hủy nó nếu chưa có ai bid.
     */
    private String clientOwner;

    /** Nhãn thời gian đánh dấu lúc phiên được lập. */
    private Date createdAt;

    /**
     * Thời điểm kết thúc theo lịch trình của phiên.
     * Với phiên "Time_Fixed", nó sẽ kết thúc chính xác vào giờ này.
     * Với phiên "Time_With_Reset", giai đoạn đếm ngược sẽ bắt đầu từ giờ này.
     */
    private Date terminateAt;

    /**
     * Cờ cho biết phiên có đang nằm trong giai đoạn đếm ngược (countdown) hay không.
     * Chỉ có tác dụng với loại "Time_With_Reset". Khi cờ là {@code true}, bid mới sẽ làm reset timer đếm ngược.
     */
    private boolean isInCountDown;

    /**
     * Loại hình đấu giá: {@code "Time_Fixed"} hoặc {@code "Time_With_Reset"}.
     * Quyết định cách thức phiên sẽ kết thúc.
     */
    private String type;

    /** {@link Timer} sử dụng để lên lịch task kết thúc/đếm ngược. */
    private Timer timer;

    /** Danh sách những client hiện đang đăng ký tham gia phiên làm participant. */
    private LinkedList<Client> clientList;

    /**
     * Lịch sử các giá thầu (bid) đã đặt, lưu theo thứ tự giảm dần từ cao xuống thấp.
     * Phần tử đứng đầu (index 0) luôn là mức giá cao nhất hiện tại.
     */
    private LinkedList<Bid> bidList;

    /** Sản phẩm mang ra đấu giá. */
    private Item item;

    /**
     * Task đếm ngược cho loại đấu giá "Time_With_Reset".
     * Xử lý luồng đếm ngược "lần thứ 1... lần thứ 2... chốt!".
     */
    private AuctionCountdownTask countdownTask;

    // ========================== Constructor ==========================

    /**
     * Tạo một Phiên đấu giá đã có sẵn danh sách người tham gia và danh sách bid.
     * <p>
     * Constructor này được dùng khi bạn khôi phục lại trạng thái từ CSDL (nhỏ)
     * hoặc nhằm mục đích test. ID được gán tự động từ {@link #incrementer}.
     * </p>
     * <p>
     * Phụ thuộc vào biến {@code type}:
     * <ul>
     * <li>{@code "Time_Fixed"} — Lập lịch một {@link AuctionTerminateTask} kích hoạt vào lúc {@code terminateAt}.</li>
     * <li>{@code "Time_With_Reset"} — Lập lịch một {@link AuctionCountdownTask} bắt đầu đếm tại {@code terminateAt}.</li>
     * </ul>
     *
     * @param clientOwner chủ sở hữu phiên (IP/Username)
     * @param terminateAt ngày/giờ kết thúc hoặc bắt đầu đếm ngược
     * @param type        loại phiên đấu giá: {@code "Time_Fixed"} hoặc {@code "Time_With_Reset"}
     * @param clientList  danh sách khởi tạo sẵn các client tham gia
     * @param bidList     danh sách khởi tạo sẵn các bid
     * @param item        sản phẩm
     */
    public Auction(String clientOwner, Date terminateAt, String type,
            LinkedList<Client> clientList, LinkedList<Bid> bidList, Item item) {
        id = incrementer.incrementAndGet();
        this.clientOwner = clientOwner;
        this.createdAt = new Date();
        this.terminateAt = terminateAt;
        this.type = type;
        this.timer = new Timer();
        countdownTask = new AuctionCountdownTask(this);

        // Lập lịch loại timer task phù hợp theo quy định loại hình đấu giá
        if (type.equals("Time_Fixed")) {
            // Loại cố định kết thúc ngay lập tức vào giờ hẹn
            timer.schedule(new AuctionTerminateTask(this), terminateAt);
        } else {
            // Loại đếm ngược bắt đầu luồng đếm xuống khi tới giờ hẹn
            timer.schedule(countdownTask, terminateAt);
        }

        this.clientList = clientList;
        this.bidList = bidList;
        this.item = item;
        isInCountDown = false;
    }

    /**
     * Tạo một Phiên đấu giá mới hoàn toàn với danh sách bid và client trống rỗng.
     * <p>
     * Đây là constructor chính được sử dụng khi client yêu cầu mở bán đấu giá mới.
     * Cả 2 list người dùng và lịch sử bid đều được thiết lập là {@link LinkedList} trống.
     * </p>
     *
     * @param clientOwner chủ sở hữu phiên (IP/Username)
     * @param terminateAt ngày/giờ kết thúc hoặc bắt đầu đếm ngược
     * @param type        loại phiên đấu giá: {@code "Time_Fixed"} hoặc {@code "Time_With_Reset"}
     * @param item        sản phẩm
     */
    public Auction(String clientOwner, Date terminateAt, String type, Item item) {
        id = incrementer.incrementAndGet();
        this.clientOwner = clientOwner;
        createdAt = new Date();
        this.terminateAt = terminateAt;
        this.type = type;
        timer = new Timer();
        bidList = new LinkedList<>();
        clientList = new LinkedList<>();
        this.item = item;
        isInCountDown = false;
        countdownTask = new AuctionCountdownTask(this);

        // Lập lịch cho timer task
        if (type.equals("Time_Fixed")) {
            timer.schedule(new AuctionTerminateTask(this), terminateAt);
        } else {
            timer.schedule(countdownTask, terminateAt);
        }

    }

    // ========================== Getter & Setter ==========================

    /**
     * Trả về bộ sinh ID tự động.
     *
     * @return {@link AtomicInteger} bộ đếm ID
     */
    public static AtomicInteger getIncrementer() {
        return incrementer;
    }

    /**
     * Trả về ID phiên.
     *
     * @return auction ID
     */
    public int getId() {
        return id;
    }

    /**
     * Gán (Set) ID phiên.
     *
     * @param id auction ID mới
     */
    public void setId(int id) {
        this.id = id;
    }

    /**
     * Lấy địa chỉ IP/Username của người chủ tạo phiên.
     *
     * @return chủ phiên
     */
    public String getClientOwner() {
        return clientOwner;
    }

    /**
     * Cập nhật chủ của phiên.
     *
     * @param clientOwner thông tin người tạo mới
     */
    public void setClientOwner(String clientOwner) {
        this.clientOwner = clientOwner;
    }

    /**
     * Lấy ngày/giờ phiên được lập ra.
     *
     * @return thời điểm bắt đầu
     */
    public Date getCreatedAt() {
        return createdAt;
    }

    /**
     * Gán ngày/giờ phiên được lập.
     *
     * @param createdAt ngày lập
     */
    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Lấy thời điểm hẹn theo lịch trình để phiến kết thúc.
     *
     * @return thời hạn kết thúc
     */
    public Date getTerminateAt() {
        return terminateAt;
    }

    /**
     * Sửa hẹn lịch kết thúc.
     *
     * @param terminateAt giờ khóa sổ mới
     */
    public void setTerminateAt(Date terminateAt) {
        this.terminateAt = terminateAt;
    }

    /**
     * Kểm tra xem chế độ đếm ngược đã kích hoạt chưa (Chỉ dùng cho "Time_With_Reset").
     *
     * @return {@code true} nếu đang đếm ngược
     */
    public boolean isInCountDown() {
        return isInCountDown;
    }

    /**
     * Gán cờ bật/tắt cho chế độ đếm ngược.
     *
     * @param inCountDown trạng thái kích hoạt đếm ngược
     */
    public void setInCountDown(boolean inCountDown) {
        isInCountDown = inCountDown;
    }

    /**
     * Trả về thể loại phiên.
     *
     * @return chuỗi {@code "Time_Fixed"} hoặc {@code "Time_With_Reset"}
     */
    public String getType() {
        return type;
    }

    /**
     * Gán thể loại phiên đấu giá.
     *
     * @param type chuỗi loại hình mới
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Lấy đối tượng Timer đang giữ lịch hẹn cho phiên.
     *
     * @return {@link Timer}
     */
    public Timer getTimer() {
        return timer;
    }

    /**
     * Cập nhật Timer.
     *
     * @param timer timer mới
     */
    public void setTimer(Timer timer) {
        this.timer = timer;
    }

    /**
     * Trả về danh sách Client đã đăng ký.
     *
     * @return {@link LinkedList} object danh sách
     */
    public LinkedList<Client> getClientList() {
        return clientList;
    }

    /**
     * Thay the danh sách client.
     *
     * @param clientList danh sách mới
     */
    public void setClientList(LinkedList<Client> clientList) {
        this.clientList = clientList;
    }

    /**
     * Trả về danh sách Bid.
     * Danh sách được xếp giảm dần với giá cao nhất ở index 0.
     *
     * @return {@link LinkedList} lịch sử các lượt đặt giá
     */
    public LinkedList<Bid> getBidList() {
        return bidList;
    }

    /**
     * Ghi đè lại bằng danh sách Bid mới.
     *
     * @param bidList danh sách thầu mới
     */
    public void setBidList(LinkedList<Bid> bidList) {
        this.bidList = bidList;
    }

    /**
     * Trả về sản phẩm thuộc về đấu giá.
     *
     * @return đối tượng {@link Item}
     */
    public Item getItem() {
        return item;
    }

    /**
     * Update sản phẩm.
     *
     * @param item item thay thế mới
     */
    public void setItem(Item item) {
        this.item = item;
    }

    /**
     * Lấy bộ task đếm ngược chuyên đảm nhiệm "lần mót... lần 2... chốt"
     *
     * @return {@link AuctionCountdownTask}
     */
    public AuctionCountdownTask getCountdownTask() {
        return countdownTask;
    }

    /**
     * Sửa lại Task đếm ngược.
     *
     * @param countdownTask Task mới
     */
    public void setCountdownTask(AuctionCountdownTask countdownTask) {
        this.countdownTask = countdownTask;
    }

    // ========================== Logic Nghiệp vụ (Business Logic) ==========================

    /**
     * Đăng kí một Client mới vào tham gia phiên.
     * <p>
     * Client này sẽ được nạp vào list {@link #clientList} và ID phiên sẽ được nạp
     * ngược vào list sở hữu cá nhân của client đó. Yêu cầu 2 ràng buộc:
     * <ol>
     * <li>Tuyệt đối Không để cho người chủ/người bán được tham gia đấu phiên của chính mình. (Cấm tạo nhu cầu ảo)</li>
     * <li>Một người tham gia không thể nạp tên hai lần vào cùng 1 phiên.</li>
     * </ol>
     *
     * @param client người muốn tham gia
     * @throws AuctionAlreadyRegisteredException nếu trùng lặp đăng ký
     * @throws AuctionClientIsOwnerException     nếu chính người lập phiên muốn chen vô
     */
    public void addClient(Client client) throws AuctionAlreadyRegisteredException, AuctionClientIsOwnerException {

        // Cấm cửa người chủ lập phiên đăng ký vào phòng của chính họ.
        if (!clientOwner.equals(client.getUsername())) {
            // Kiểm chứng xem client có vô duyên đăng ký lại hay chưa
            if (!clientList.contains(client)) {

                // Ghi danh ID phiên này vào danh mục quản lý trong hồ sơ ông khách
                client.getRegisteredAuctions().addFirst(id);

                // Triển khai kết nạp
                clientList.add(client);

            } else {
                throw new AuctionAlreadyRegisteredException("Client này đăng kí rồi");
            }
        } else {
            throw new AuctionClientIsOwnerException("Ai lại đi đầu cơ chính món hàng của mình. (Owner)");
        }
    }

    /**
     * Huỷ tư cách thành viên, tự nguyện xin rút.
     * <p>
     * Rút tên client khỏi tập lưu khách và gỡ phiên này ra khỏi túi sở hữu cá nhân của vị khách.\br>
     * <br>
     * <strong>Tuyệt Vấn Đề:</strong> Ai đang nắm giữ ngôi vương giá thầu đỉnh cao thì KHÔNG 
     * ĐƯỢC PHÉP từ nhiệm cho tới khi có người gánh hộ mức giá hớ hênh đó. Giúp loại trừ tình trạng "phá giá bỏ của chạy lấy người".
     * </p>
     *
     * @param client khách muốn exit
     * @throws AuctionHighBidException       nếu người đó đang ôm cú Bid khủng nhất phòng.
     * @throws AuctionNotRegisteredException nếu người này tào lao, chưa có tên mà đã xin rút.
     */
    public void removeClient(Client client)
            throws AuctionHighBidException, AuctionNotRegisteredException {

        // Phải chắc chắn ông có tên ở đây đã
        if (clientList.contains(client)) {

            // Chặn đứt việc đào chạy nếu nắm trong tay chức Vua Bid
            if (!bidList.isEmpty() && bidList.getFirst().getBidderUsername()
                    .equals(client.getUsername())) {
                throw new AuctionHighBidException("Đang ôm Bid cao nhất thì khỏi có mà chạy");
            }

            // Gỡ tag phòng cho danh sách của riêng khách
            int auctionIndex = client.getRegisteredAuctions().indexOf(id);
            if (auctionIndex != -1) {
                client.getRegisteredAuctions().remove(auctionIndex);
            }

            // Đuổi khỏi hội nghị
            clientList.remove(client);
        } else {
            throw new AuctionNotRegisteredException("Ngươi chưa từng đăng kí tham gia");
        }
    }

    /**
     * Cưỡng chế trục xuất một khách, kể cả có đang ôm cúp Vua Bid đi chăng nữa.
     * <p>
     * Cú hook pháp lý sinh ra khi người chơi "rớt mạng/chủ ý ngắt kết nối". 
     * Dù đang Top 1 hay không, mạng mà đứt là bị loại. Ngai vàng Bid (nếu có) bị tước 
     * để phế truất, rơi rớt lại mức Bid đứng nhì trước đó. Server rùm beng gọi lệnh 
     * {@code HIGHEST_BID_OWNER_LOST} để báo tin dữ cho toàn dân.
     * </p>
     *
     * @param client client bị rớt mạng / bị bay acc
     * @throws AuctionNotRegisteredException nếu không có trong sổ danh sách
     */
    public void forcefullyRemoveClient(Client client) throws AuctionNotRegisteredException {

        Server server = Server.getInstance();

        // Kiểm tra đúng là có người đó không
        if (clientList.contains(client)) {
            // Check nếu mà vừa lúc hắn ta nắm bid đỉnh
            if (!bidList.isEmpty() && bidList.getFirst().getBidderUsername().equals(client.getUsername())) {
                // Tước đoạt ngai vị Bid cao nhất (remove index 0)
                bidList.remove(0);

                // Ai là chân nhân mới lên vị đây? 
                float highestBid = item.getStartingPrice();

                if (!bidList.isEmpty()) {
                    highestBid = bidList.getFirst().getBid();
                }

                // Lu loa cho thiên hạ biết Tân Vương (hoặc quay tay về giá khởi điểm)
                AuctionUpdatePayload auctionUpdate = new AuctionUpdatePayload(id, createdAt, highestBid,
                        item.getName(), client.getUsername(),
                        item.getDescription());
                server.sendPackets(clientList, new PacketMessage(HIGHEST_BID_OWNER_LOST, auctionUpdate));
            }

            // Xoá trọn danh số của kẻ bất hạnh
            clientList.remove(client);

        } else {
            throw new AuctionNotRegisteredException("Làm gì có mà remove?");
        }
    }

    /**
     * Tung ra cú ngã giá (đặt Bid mới) chính thức dành cho 1 client đang ngồi trong phiên.
     * <p>
     * Hàm này xác định tính hợp lệ phải thoả mãn (Số tiền phải vượt cúp Bid hiện tại
     * hoặc ít ra phải vượt giá gốc ban đầu của chủ sạp), sau đó sẽ làm một chuỗi các thao tác:
     * <ol>
     * <li>Gạch tên Vua Bid cũ.</li>
     * <li>Phong tước Vua Bid cho người mới (addFirst vào LinkedList).</li>
     * <li>Tặng danh hiệu high-bid cho người chơi mới.</li>
     * <li>Nếu phiên là loại "Time_With_Reset" và quan trọng là ĐANG Ở CHẾ ĐỘ ĐẾM NGƯỢC, 
     * thế thì phải ra ơn <strong>reset</strong> bộ đếm đồng hồ — Để bàn dân thiện hạ có thêm thì giờ phục thù.</li>
     * <li>Giao thông báo {@code AUCTION_UPDATE} cho toàn Server cùng nghe đặng mà ganh đua.</li>
     * </ol>
     *
     * @param bid    đối tượng Bid chứa giá trị và nhãn username khách
     * @param client Khách tung tiền
     * @throws AuctionNotRegisteredException Khách lậu (Chưa đăng kí vào room)
     * @throws AuctionLowBidException        Tiền ít đâm chọc lôi thôi (Trả quá bèo bọt)
     * @throws AuctionClientIsOwnerException Mình bán lại đi trả giá ảo
     */
    public void addBid(Bid bid, Client client)
            throws AuctionNotRegisteredException, AuctionLowBidException, AuctionClientIsOwnerException {

        // Người bán cấm tự bid
        if (!clientOwner.equals(client.getUsername())) {
            // Có giấy phép phòng chưa?
            if (clientList.contains(client)) {

                // Giá đưa ra có đè bẹp được giá cao nhất lúc này hay đủ chuẩn giá khởi điểm không.
                if ((!bidList.isEmpty() && bidList.getFirst().getBid() < bid.getBid())
                        || (bidList.isEmpty() && bid.getBid() > item.getStartingPrice())) {

                    Server server = Server.getInstance();

                    // Sao lưu tiền đỉnh cũ để tính toán ném biên bản Update
                    float highestBid = findHighestBid().getBid();

                    // Đánh tụt điểm hạng huy chương của cựu đế vương cũ
                    if (!bidList.isEmpty()
                            && server.getClientHandlers().containsKey(bidList.getFirst().getBidderUsername())) {
                        server.getClientHandlers().get(bidList.getFirst().getBidderUsername()).getClient()
                                .lostHighBid();
                    }

                    // Tước vương miện đặt cho Bid mới chễm chệ leo Index 0!
                    bidList.addFirst(bid);
                    // Dựng tượng cho gã Tân Vương 
                    client.madeHighBid();

                    // QUY TRÌNH HỒI SINH ĐẾM NGƯỢC
                    // Lúc ván cờ đến hồi kết (countdown), mà có gã đại gia ném tiền vào
                    // thì phải bới tung đồng hồ lên kéo dài drama thêm chút nữa.
                    if (type.equals("Time_With_Reset") && isInCountDown) {

                        countdownTask.setCanConclude(false); // Ra lệnh chốt đơn hụt!!
                        countdownTask.cancel(); // Xé Task cu cũ
                        timer.cancel(); // Bẻ vụn Timer 
                        timer.purge(); // Đổ sọt rác luồng huỷ
                        timer = null;
                        countdownTask = null;
                        timer = new Timer(); // Thuê đồng hồ mới
                        countdownTask = new AuctionCountdownTask(this); // Kêu con bò mới đếm ngược
                        timer.schedule(countdownTask, 0); // Vã đếm liền ngay tắp lự
                    }

                    // Ghép thư bố cáo số tiền vừa trót tay
                    AuctionUpdatePayload auctionUpdate = new AuctionUpdatePayload(id, createdAt, highestBid,
                            item.getName(), client.getUsername(),
                            item.getDescription());
                    PacketMessage auctionUpdatePacket = new PacketMessage(AUCTION_UPDATE, auctionUpdate);

                    // Phóng bồ câu cho quần anh hùng võ lâm trong Room
                    server.sendPackets(clientList, auctionUpdatePacket);

                    // Báo qua điện thoại cho thằng Chủ sạp để nhảy cẫng lên ăn mừng 
                    if (server.getClientHandlers().containsKey(clientOwner)) {
                        try {
                            server.getClientHandlers().get(clientOwner).sendPacket(auctionUpdatePacket);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                } else {
                    throw new AuctionLowBidException("Giá quá hẻo, không đủ phá top");
                }
            } else {
                throw new AuctionNotRegisteredException("Vô danh tiểu tốt, chưa đăng ký mà thầu?");
            }
        } else {
            throw new AuctionClientIsOwnerException("Gian thương, không được tự đẩy giá");
        }
    }

    /**
     * Ra lệnh Giải tán quốc hội (Huỷ phiên đấu giá) làm bốc hơi Server list.
     * <p>
     * Duy nhất <strong>chủ xị</strong> (Owner) có khiếu nại lệnh này, với đk kiên quyết 
     * là <strong>phòng chưa hề dính giọt máu (bất kì ai Bid tiền)</strong>. Ván đã vào phom
     * (có Bid) là ép phải nuốt chạy tẹt ga không cho hoãn nha!
     * </p>
     * <p>
     * Khi Giải tán trót lọt:
     * <ul>
     * <li>Băm nát đồng hồ hẹn.</li>
     * <li>Dập mật thư {@code AUCTION_CANCELLED} tát vào mặt toàn dân tham dự phòng.</li>
     * <li>Chủ sạp cũng được nhận giấy chứng nhận "Làm ăn thất bại".</li>
     * <li>Tất ráo các dân tham gia được cởi gông, và Phiên bị xoá sổ từ bộ nhớ đệm Server.</li>
     * </ul>
     *
     * @param client Người chốt hạ đòi huỷ (Bắt buộc phải là ông nội làm chủ)
     * @throws AuctionActiveException        Đã có tiền trao cháo múc rôi, ko chạy được
     * @throws ServerNotClientOwnerException Đâu ra thằng chí phèo tự dưng vô đập quán dẹp tiệm người ta
     */
    public void cancel(Client client) throws AuctionActiveException, ServerNotClientOwnerException {

        // Check thẻ căn cước có phải chủ không
        if (clientOwner.equals(client.getUsername())) {
            // Mạch trống rỗng là xoá luôn ok.
            if (bidList.isEmpty()) {
                Server server = Server.getInstance();

                // Huỷ kíp nổ hệ giờ
                timer.cancel();
                timer = null;

                // Nặn gói báo tang cho các dân đen tham gia 
                PacketMessage auctionCanceledPacket = new PacketMessage(AUCTION_CANCELLED,
                        new ConfirmAuctionCancellationPayload(id));
                server.sendPackets(clientList, auctionCanceledPacket);

                // Dúi vào tay chủ cũ bản copy
                if (server.getClientHandlers().containsKey(clientOwner)) {
                    try {
                        server.getClientHandlers().get(clientOwner).sendPacket(auctionCanceledPacket);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                // Gỡ tem giấy tờ trong phòng quản lý của từng khách 
                for (int i = 0; i < clientList.size(); i++) {
                    if (clientList.get(i).getRegisteredAuctions().contains(id)) {
                        clientList.remove(i);
                    }
                }

                // Thổi bay sạp từ bản đồ chợ đen Server 
                server.getAuctions().remove(id);
            } else {
                throw new AuctionActiveException("Hàng hot đã ngã giá, cấm có thuồi lui.");
            }
        } else {
            throw new ServerNotClientOwnerException("Ra ngoài cho người ta buôn bán(Không phải chủ)");
        }
    }

    /**
     * Hạ màn, kết án, rớt búa, chốt sổ! (Khép lại phiên đấu giá).
     * <p>
     * Hàm này được triệu hồi bởi ngự lâm quân Time ({@link AuctionTerminateTask} 
     * hoặc {@link AuctionCountdownTask}) khi hết giờ, hoặc khi Server báo Cúp Điện sập sập tắt nguồn. 
     * Cách thức tính án:
     * <ol>
     * <li>Nếu sạp đã dính Bid, bắt đầu tra khảo list Bid theo thứ tự lớn => bé. Tìm ra gã đầu tiên 
     * KẾT NỐI MẠNG ĐANG SỐNG, gã này sẽ là QUÁN QUÂN trúng thầu.</li>
     * <li>Ship bằng khen {@code NOTIFY_AUCTION_WINNER} tận giường Winers.</li>
     * <li>Báo hiếu tin vui chung {@code AUCTION_CONCLUDED} cho cả bộ tộc và lão chủ sạp.</li>
     * <li>Bi kịch: Ế sưng (không Bid) HOẶC Trúng thầu lại BÙNG mẹ mất mạng(không dò ra). Khi đó ném phiếu 
     * {@code NOTIFY_NO_AUCTION_WINNER} - Kết quả Huề Vốn.</li>
     * <li>Dọn dẹp rác: Gỡ ID này khỏi sổ lý lịch all khách, và tự thiêu mình trên CSDL Server(bộ nhớ).</li>
     * </ol>
     */
    public void conclude() {

        Server server = Server.getInstance();
        PacketMessage noAuctionWinnerPacket;
        boolean foundWinner = false;

        // VỤ 1: Hàng Hot Bán Được - Lật đật tra lý lịch tìm cho ra Winer 
        if (!bidList.isEmpty()) {
            // Móc từ thằng tay to (To tiền) dần đuối sức
            for (int i = 0; i < bidList.size(); i++) {
                // Thám thính xem gã đó còn trực tuyến hay offline báo nhà mạng 
                if (server.getClientHandlers().containsKey(bidList.get(i).getBidderUsername())) {

                    foundWinner = true; // Bắt được quả tang người chiến thắng

                    // Khắc bia đá gói tin về Kết quả + Danh tánh Đắc Kỉ  
                    ConcludeAuctionPayload concludePayload = new ConcludeAuctionPayload(id, bidList.get(i).getBid(),
                            item.getName(), bidList.get(i).getBidderUsername());
                    PacketMessage concludeAuctionPacket = new PacketMessage(AUCTION_CONCLUDED, concludePayload);
                    NotifyAuctionWinnerPayload notifyWinnerPayload = new NotifyAuctionWinnerPayload(id,
                            bidList.get(i).getBid(), item.getName());
                    PacketMessage notifyWinnerPacket = new PacketMessage(NOTIFY_AUCTION_WINNER, notifyWinnerPayload);

                    // Phím êm cho ông chủ hàng bán hời (nếu hắn còn online thu tiền)
                    if (server.getClientHandlers().containsKey(clientOwner)) {
                        try {
                            server.getClientHandlers().get(clientOwner).sendPacket(concludeAuctionPacket);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    // Trút bỏ gánh nặng vương cường(giải toả high-bid) vì Game chấm hết  
                    server.getClientHandlers().get(bidList.get(i).getBidderUsername()).getClient().lostHighBid();

                    // Vã mẹt tờ Trúng Giải Thưởng lớn vào đầu thằng Winer 
                    try {
                        server.getClientHandlers().get(bidList.get(i).getBidderUsername())
                                .sendPacket(notifyWinnerPacket);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    // Phát thanh tiếng leng keng kết cục tới tai bãi khách ngoài đồng 
                    server.sendPackets(clientList, concludeAuctionPacket);

                    break; // Bắt được Át Chủ Bài rồi thì phá vỡ vòng lặp thui
                }
            }
            if (!foundWinner) {
                // Cuốc tình nghiệt ngã - Cả họ thầu giá đã xách balo biến offline sạch - Kết luận: Bỏ ko 
                noAuctionWinnerPacket = new PacketMessage(NOTIFY_NO_AUCTION_WINNER,
                        new NotifyNoAuctionWinnerPayload(id, item.getName(), item.getStartingPrice()));

                // Nhắn gã chủ xị đừng đợi vô duyên, đem hàng cất vô 
                if (server.getClientHandlers().containsKey(clientOwner)) {
                    try {
                        server.getClientHandlers().get(clientOwner).sendPacket(noAuctionWinnerPacket);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                // Hô la bá cáo thiên hạ 
                server.sendPackets(clientList, noAuctionWinnerPacket);
            }
        } else {
            // VỤ 2: Sản phẩm bị ghẻ lạnh — Hết giờ không ma nào dòm tới -> Chợ Tàn chả có nhà buôn
            noAuctionWinnerPacket = new PacketMessage(NOTIFY_NO_AUCTION_WINNER,
                    new NotifyNoAuctionWinnerPayload(id, item.getName(), item.getStartingPrice()));

            // Gọi lão bán ra ngoài ôm cụt than 
            if (server.getClientHandlers().containsKey(clientOwner)) {
                try {
                    server.getClientHandlers().get(clientOwner).sendPacket(noAuctionWinnerPacket);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            // Khua chiêng trống đánh gãy 
            server.sendPackets(clientList, noAuctionWinnerPacket);
        }

        // TỔNG VỆ SINH: Lao công dọn dẹp quét rác - Xoá triêt vết tích phòng này tại hồ sơ mỗi người
        for (int i = 0; i < clientList.size(); i++) {
            int auctionIndex = clientList.get(i).getRegisteredAuctions().indexOf(id);
            if (auctionIndex != -1) {
                clientList.get(i).getRegisteredAuctions().remove(auctionIndex);
            }
        }

        // Tống cổ xoá định danh cái Chợ Tình này khỏi Bản Đồ Server Active nha! 
        server.getAuctions().remove(id);
    }

    /**
     * Hàm bốc quẻ coi chóp Bid cao kều nhất, nếu xui rủi không có 1 đống phân Bid 
     * nào sất thì quăng về cái Mâm Cơm Áo (Mock/Sentinel bid) để thay lời an ủi.
     * <p>
     * Cục Bid "Ảo diệu" đính kèm thông tin: Này giờ lập = {@code null}, Kẻ ném tiền = {@code null}, 
     * số bạc = {@code 0}. Hữu dụng cho việc tránh null-pointer khét lẹt khi rờ tới .getBid() 
     * cho bọn dev hùa đằng sau.
     * </p>
     *
     * @return {@link Bid} mập nhất, hoặc quả báo {@code Bid(null, 0, null)}
     */
    public Bid findHighestBid() {
        if (!bidList.isEmpty()) {
            return this.getBidList().getFirst();
        } else {
            // Móc ra rổ rác gải lập 
            return new Bid(null, 0, null);
        }
    }

    /**
     * Moi nguyên cái Xác giá tiền lên thôi (không kèm tên). 
     * <p>
     * Rất khác cái {@link #findHighestBid()} kia nha, cái này nôn ra tiền Float, ko có Object bid! 
     * Nếu ko ai ngắm tới, thì ói ra {@code 0} chứ ko phải bóc lại Giá khởi điểm đâu à mhen!
     * </p>
     *
     * @return số tiền núi, hoặc {@code 0.0f} nếu ko móc dc cọc cắc nào 
     */
    public float findHighestItemPrice() {

        float highestBid = 0;

        if (!bidList.isEmpty()) {
            highestBid = bidList.getFirst().getBid();
        }

        return highestBid;
    }

    // ========================== Cấu hình Đè (Override Object) ==========================

    /**
     * Dựng phim tài liệu dạng chữ (Nhằm bug lỗi Dev). 
     *
     * @return Phun ra chuỗi rồng rắn chứa đủ loại rác của phòng 
     */
    @Override
    public String toString() {
        return "ServerAuction{" +
                "id=" + id +
                ", clientOwner=" + clientOwner +
                ", createdAt=" + createdAt +
                ", terminateAt=" + terminateAt +
                ", isInCountDown=" + isInCountDown +
                ", type='" + type + '\'' +
                ", timer=" + timer +
                ", clientList=" + clientList +
                ", bidList=" + bidList +
                ", item=" + item +
                '}';
    }

    /**
     * Xét xử Hai Anh Sinh Đôi! Thước đo sự bằng mặt bằng lòng
     * Tức là tra toàn tính danh 10 ngón coi có ăn nhập ko.
     *
     * @param o Đối tác định danh 
     * @return {@code true} Nếu giống 100%, {@code false} Nếu bị chọc thẹo khác.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Auction auction = (Auction) o;
        return isInCountDown == auction.isInCountDown &&
                Objects.equals(id, auction.id) &&
                Objects.equals(clientOwner, auction.clientOwner) &&
                Objects.equals(createdAt, auction.createdAt) &&
                Objects.equals(terminateAt, auction.terminateAt) &&
                Objects.equals(type, auction.type) &&
                Objects.equals(timer, auction.timer) &&
                Objects.equals(clientList, auction.clientList) &&
                Objects.equals(bidList, auction.bidList) &&
                Objects.equals(item, auction.item);
    }

    /**
     * In ra tờ Bằng khen Bơm Mã (Hash Code). 
     *
     * @return Số Hash ngẫu hứng 
     */
    @Override
    public int hashCode() {
        return Objects
                .hash(id, clientOwner, createdAt, terminateAt, isInCountDown, type, timer, clientList,
                        bidList, item);
    }
}