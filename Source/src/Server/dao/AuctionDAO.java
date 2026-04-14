package Server.dao;

import CommonClasses.Bid;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Lớp DAO quản lý việc lưu trữ {@link AuctionSnapshot} (bản chụp phiên đấu giá).
 * <p>
 * DAO này lưu trữ trạng thái phiên đấu giá bằng đối tượng {@link AuctionSnapshot}
 * — bản chụp có thể serialize, chứa phần dữ liệu bền vững của
 * {@link CommonClasses.Auction} mà không có các trường runtime không thể serialize
 * (Timer, Client, v.v.).
 * </p>
 *
 * <h3>Singleton Pattern:</h3>
 * Triển khai Singleton an toàn đa luồng bằng double-checked locking.
 *
 * <h3>Ánh xạ khóa:</h3>
 * Phiên đấu giá được lưu trong {@code HashMap<Integer, AuctionSnapshot>}
 * với khóa là ID phiên (số nguyên, sinh bởi {@code AtomicInteger} trong lớp Auction).
 *
 * <h3>Vòng đời:</h3>
 * <ol>
 *   <li>Khi tạo phiên mới, {@link #save(Integer, AuctionSnapshot)} lưu trạng thái ban đầu.</li>
 *   <li>Khi có bid mới hoặc thay đổi trạng thái, {@link #update(Integer, AuctionSnapshot)}
 *       lưu trạng thái mới.</li>
 *   <li>Khi server khởi động lại, {@link #findActiveAuctions()} trả về tất cả phiên
 *       chưa kết thúc, cho phép tạo lại Timer/Client mới.</li>
 * </ol>
 *
 * <h3>An toàn đa luồng:</h3>
 * Tất cả phương thức public được bảo vệ bởi {@link ReentrantReadWriteLock}.
 *
 * <h3>Ví dụ sử dụng:</h3>
 * <pre>{@code
 *   AuctionDAO dao = AuctionDAO.getInstance();
 *
 *   // Lưu snapshot phiên mới
 *   AuctionSnapshot snapshot = new AuctionSnapshot(
 *       1, "seller_john", new Date(), terminateDate,
 *       "Time_Fixed", "OPEN", item, new LinkedList<>(),
 *       new ArrayList<>(), false
 *   );
 *   dao.save(1, snapshot);
 *
 *   // Cập nhật sau khi có bid
 *   snapshot.getBidList().addFirst(new Bid(new Date(), 150f, "bidder_jane"));
 *   snapshot.setStatus("RUNNING");
 *   dao.update(1, snapshot);
 *
 *   // Tìm phiên đang hoạt động khi khởi động lại
 *   List<AuctionSnapshot> active = dao.findActiveAuctions();
 * }</pre>
 *
 * @see AuctionSnapshot
 * @see CommonClasses.Auction
 * @see GenericDAO
 * @see DataStore
 */
public class AuctionDAO implements GenericDAO<Integer, AuctionSnapshot> {

    // ========================== Hằng số ==========================

    /** Tên file lưu trữ dữ liệu phiên đấu giá. */
    private static final String DATA_FILE = "auctions.dat";

    // ========================== Singleton ==========================

    private static volatile AuctionDAO instance;

    /**
     * Trả về instance Singleton của {@code AuctionDAO}.
     *
     * @return instance Singleton
     */
    public static AuctionDAO getInstance() {
        if (instance == null) {
            synchronized (AuctionDAO.class) {
                if (instance == null) {
                    instance = new AuctionDAO();
                }
            }
        }
        return instance;
    }

    // ========================== Thuộc tính ==========================

    /** Kho lưu trữ file cho dữ liệu phiên đấu giá. */
    private final DataStore dataStore;

    /** Cache trong bộ nhớ: auctionId → AuctionSnapshot. */
    private HashMap<Integer, AuctionSnapshot> auctions;

    /** Khóa đọc-ghi cho truy cập an toàn đa luồng. */
    private final ReentrantReadWriteLock lock;

    // ========================== Constructor ==========================

    /**
     * Constructor private — sử dụng {@link #getInstance()}.
     * Tải dữ liệu phiên đấu giá từ ổ đĩa khi khởi tạo.
     */
    private AuctionDAO() {
        this.dataStore = new DataStore(DATA_FILE);
        this.lock = new ReentrantReadWriteLock();
        this.auctions = dataStore.readData();
        System.out.println("[AuctionDAO] Đã khởi tạo. Tải " + auctions.size() + " phiên đấu giá từ ổ đĩa.");
    }

    // ========================== Triển khai GenericDAO ==========================

    /**
     * Lưu snapshot phiên đấu giá mới.
     *
     * @param auctionId ID duy nhất của phiên
     * @param snapshot  bản chụp AuctionSnapshot cần lưu
     * @throws IllegalArgumentException nếu auctionId null hoặc snapshot null
     */
    @Override
    public void save(Integer auctionId, AuctionSnapshot snapshot) {
        if (auctionId == null) {
            throw new IllegalArgumentException("ID phiên đấu giá không được null");
        }
        if (snapshot == null) {
            throw new IllegalArgumentException("AuctionSnapshot không được null");
        }

        lock.writeLock().lock();
        try {
            if (auctions.containsKey(auctionId)) {
                System.err.println("[AuctionDAO] Cảnh báo: Phiên " + auctionId
                        + " đã tồn tại. Dùng update() thay thế.");
                return;
            }
            auctions.put(auctionId, snapshot);
            persistData();
            System.out.println("[AuctionDAO] Đã lưu phiên: " + auctionId
                    + " (" + snapshot.getItem().getName() + ")");
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Tìm snapshot phiên đấu giá theo ID.
     *
     * @param auctionId ID phiên đấu giá
     * @return {@link AuctionSnapshot} nếu tìm thấy, hoặc {@code null}
     */
    @Override
    public AuctionSnapshot findById(Integer auctionId) {
        lock.readLock().lock();
        try {
            return auctions.get(auctionId);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Trả về tất cả snapshot phiên đấu giá đã lưu.
     *
     * @return danh sách mới chứa tất cả snapshot
     */
    @Override
    public List<AuctionSnapshot> findAll() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(auctions.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Cập nhật snapshot phiên đấu giá (VD: sau bid mới hoặc đổi trạng thái).
     *
     * @param auctionId ID phiên cần cập nhật
     * @param snapshot  snapshot đã cập nhật
     * @return {@code true} nếu tìm thấy và cập nhật thành công
     */
    @Override
    public boolean update(Integer auctionId, AuctionSnapshot snapshot) {
        lock.writeLock().lock();
        try {
            if (!auctions.containsKey(auctionId)) {
                return false;
            }
            auctions.put(auctionId, snapshot);
            persistData();
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Xóa snapshot phiên đấu giá theo ID.
     *
     * @param auctionId ID phiên cần xóa
     * @return {@code true} nếu tìm thấy và xóa thành công
     */
    @Override
    public boolean delete(Integer auctionId) {
        lock.writeLock().lock();
        try {
            AuctionSnapshot removed = auctions.remove(auctionId);
            if (removed != null) {
                persistData();
                System.out.println("[AuctionDAO] Đã xóa phiên: " + auctionId);
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Kiểm tra phiên đấu giá với ID cho trước có tồn tại hay không.
     *
     * @param auctionId ID phiên cần kiểm tra
     * @return {@code true} nếu tồn tại
     */
    @Override
    public boolean exists(Integer auctionId) {
        lock.readLock().lock();
        try {
            return auctions.containsKey(auctionId);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Trả về tổng số phiên đấu giá đã lưu.
     *
     * @return số lượng phiên
     */
    @Override
    public int count() {
        lock.readLock().lock();
        try {
            return auctions.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void flush() {
        lock.writeLock().lock();
        try {
            persistData();
            System.out.println("[AuctionDAO] Đã ghi " + auctions.size() + " phiên xuống ổ đĩa.");
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void reload() {
        lock.writeLock().lock();
        try {
            this.auctions = dataStore.readData();
            System.out.println("[AuctionDAO] Đã tải lại " + auctions.size() + " phiên từ ổ đĩa.");
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ========================== Phương thức Truy vấn riêng cho Auction ==========================

    /**
     * Tìm tất cả phiên đấu giá đang hoạt động (chưa kết thúc).
     * <p>
     * Phiên được coi là đang hoạt động nếu trạng thái là "OPEN" hoặc "RUNNING".
     * Phương thức này được gọi khi server khởi động lại để tái tạo các phiên
     * đấu giá đang diễn ra với Timer mới.
     * </p>
     *
     * @return danh sách snapshot của các phiên đang hoạt động
     */
    public List<AuctionSnapshot> findActiveAuctions() {
        lock.readLock().lock();
        try {
            return auctions.values().stream()
                    .filter(s -> !s.isConcluded())
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Tìm tất cả phiên đấu giá do một seller cụ thể tạo.
     *
     * @param sellerUsername username của seller
     * @return danh sách snapshot các phiên thuộc seller này
     */
    public List<AuctionSnapshot> findBySeller(String sellerUsername) {
        lock.readLock().lock();
        try {
            return auctions.values().stream()
                    .filter(s -> s.getClientOwner().equals(sellerUsername))
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Tìm tất cả phiên đấu giá mà một bidder cụ thể đã đặt giá ít nhất 1 lần.
     *
     * @param bidderUsername username của bidder
     * @return danh sách snapshot các phiên có bid của user này
     */
    public List<AuctionSnapshot> findByBidder(String bidderUsername) {
        lock.readLock().lock();
        try {
            return auctions.values().stream()
                    .filter(s -> s.getBidList().stream()
                            .anyMatch(bid -> bid.getBidderUsername().equals(bidderUsername)))
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Tìm tất cả phiên đấu giá theo trạng thái cụ thể.
     *
     * @param status trạng thái cần lọc (OPEN, RUNNING, FINISHED, PAID, CANCELED)
     * @return danh sách snapshot khớp trạng thái
     */
    public List<AuctionSnapshot> findByStatus(String status) {
        lock.readLock().lock();
        try {
            return auctions.values().stream()
                    .filter(s -> s.getStatus().equalsIgnoreCase(status))
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Tìm tất cả phiên đấu giá mà một user cụ thể đã đăng ký tham gia.
     *
     * @param username username của người tham gia
     * @return danh sách snapshot các phiên user đã đăng ký
     */
    public List<AuctionSnapshot> findByParticipant(String username) {
        lock.readLock().lock();
        try {
            return auctions.values().stream()
                    .filter(s -> s.getRegisteredUsernames().contains(username))
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Chỉ cập nhật trường trạng thái của một phiên đấu giá.
     * <p>
     * Phương thức tiện ích cho các chuyển trạng thái:
     * OPEN → RUNNING → FINISHED → PAID / CANCELED
     * </p>
     *
     * @param auctionId ID phiên đấu giá
     * @param newStatus trạng thái mới
     * @return {@code true} nếu tìm thấy phiên và cập nhật thành công
     */
    public boolean updateStatus(int auctionId, String newStatus) {
        lock.writeLock().lock();
        try {
            AuctionSnapshot snapshot = auctions.get(auctionId);
            if (snapshot == null) {
                return false;
            }
            String oldStatus = snapshot.getStatus();
            snapshot.setStatus(newStatus);
            persistData();
            System.out.println("[AuctionDAO] Phiên " + auctionId
                    + " trạng thái: " + oldStatus + " → " + newStatus);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Thêm bid mới vào snapshot phiên đấu giá và lưu thay đổi.
     * <p>
     * Bid được chèn vào đầu danh sách (giá cao nhất đầu tiên).
     * Trạng thái phiên tự động chuyển sang "RUNNING" nếu đang là "OPEN".
     * </p>
     *
     * @param auctionId ID phiên đấu giá
     * @param bid       bid mới cần thêm
     * @return {@code true} nếu tìm thấy phiên và thêm bid thành công
     */
    public boolean addBid(int auctionId, Bid bid) {
        lock.writeLock().lock();
        try {
            AuctionSnapshot snapshot = auctions.get(auctionId);
            if (snapshot == null) {
                return false;
            }
            snapshot.getBidList().addFirst(bid);
            // Tự động chuyển từ OPEN sang RUNNING khi có bid đầu tiên
            if ("OPEN".equals(snapshot.getStatus())) {
                snapshot.setStatus("RUNNING");
            }
            persistData();
            System.out.println("[AuctionDAO] Phiên " + auctionId
                    + ": bid mới " + bid.getBid() + " bởi " + bid.getBidderUsername());
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Thêm username vào danh sách người tham gia của một phiên đấu giá.
     *
     * @param auctionId ID phiên đấu giá
     * @param username  username cần đăng ký
     * @return {@code true} nếu tìm thấy phiên và đăng ký thành công
     */
    public boolean addParticipant(int auctionId, String username) {
        lock.writeLock().lock();
        try {
            AuctionSnapshot snapshot = auctions.get(auctionId);
            if (snapshot == null) {
                return false;
            }
            if (!snapshot.getRegisteredUsernames().contains(username)) {
                snapshot.getRegisteredUsernames().add(username);
                persistData();
                System.out.println("[AuctionDAO] Phiên " + auctionId
                        + ": đã đăng ký user " + username);
            }
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Xóa username khỏi danh sách người tham gia của một phiên đấu giá.
     *
     * @param auctionId ID phiên đấu giá
     * @param username  username cần hủy đăng ký
     * @return {@code true} nếu tìm thấy phiên và xóa thành công
     */
    public boolean removeParticipant(int auctionId, String username) {
        lock.writeLock().lock();
        try {
            AuctionSnapshot snapshot = auctions.get(auctionId);
            if (snapshot == null) {
                return false;
            }
            boolean removed = snapshot.getRegisteredUsernames().remove(username);
            if (removed) {
                persistData();
                System.out.println("[AuctionDAO] Phiên " + auctionId
                        + ": đã hủy đăng ký user " + username);
            }
            return removed;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Trả về toàn bộ lịch sử bid của một phiên đấu giá cụ thể.
     * <p>
     * Hữu ích cho tính năng Biểu đồ Lịch sử Giá (realtime price curve).
     * Danh sách sắp xếp từ giá cao nhất (đầu) đến thấp nhất (cuối).
     * </p>
     *
     * @param auctionId ID phiên đấu giá
     * @return danh sách bid, hoặc danh sách rỗng nếu không tìm thấy phiên
     */
    public List<Bid> getBidHistory(int auctionId) {
        lock.readLock().lock();
        try {
            AuctionSnapshot snapshot = auctions.get(auctionId);
            if (snapshot != null && snapshot.getBidList() != null) {
                return new ArrayList<>(snapshot.getBidList());
            }
            return new ArrayList<>();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Trả về ID phiên đấu giá lớn nhất hiện đang được lưu trữ.
     * <p>
     * Được sử dụng khi server khởi động lại để khởi tạo bộ đếm
     * {@code AtomicInteger} trong lớp {@link CommonClasses.Auction},
     * đảm bảo phiên mới không bị trùng ID với phiên đã lưu.
     * </p>
     *
     * @return ID phiên lớn nhất, hoặc 0 nếu không có phiên nào
     */
    public int getMaxAuctionId() {
        lock.readLock().lock();
        try {
            return auctions.keySet().stream()
                    .mapToInt(Integer::intValue)
                    .max()
                    .orElse(0);
        } finally {
            lock.readLock().unlock();
        }
    }

    // ========================== Phương thức Private ==========================

    /**
     * Ghi map phiên đấu giá xuống ổ đĩa.
     * Phải được gọi khi đang giữ write lock.
     */
    private void persistData() {
        dataStore.writeData(auctions);
    }
}
