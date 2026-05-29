package Server.service;

import CommonClasses.AutoBidConfig;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Quản lý toàn bộ logic Auto-Bidding (Đấu giá tự động) trên Server.
 * <p>
 * {@code AutoBidManager} là trung tâm điều phối auto-bid cho toàn hệ thống.
 * Mỗi phiên đấu giá (auction) có một {@link PriorityQueue} riêng chứa các
 * cấu hình {@link AutoBidConfig}, được sắp xếp theo thứ tự ưu tiên:
 * <ol>
 *   <li>maxBid cao hơn → ưu tiên trước</li>
 *   <li>Đăng ký sớm hơn → ưu tiên trước (tie-breaking)</li>
 * </ol>
 *
 * <h3>Thread Safety:</h3>
 * Sử dụng {@link ReentrantLock} per-auction để đảm bảo:
 * <ul>
 *   <li>Tránh Race Condition khi nhiều client đồng thời đặt giá</li>
 *   <li>Tránh Lost Update khi nhiều auto-bid kích hoạt cùng lúc</li>
 *   <li>Đảm bảo chuỗi auto-bid (bidding chain) xử lý tuần tự cho mỗi auction</li>
 * </ul>
 *
 * <h3>Singleton Pattern:</h3>
 * Thread-safe lazy initialization bằng double-checked locking.
 *
 * <h3>Thiết kế PriorityQueue:</h3>
 * Sử dụng {@code PriorityQueue<AutoBidConfig>} với ordering tự nhiên
 * (dựa trên {@link AutoBidConfig#compareTo}). Java PriorityQueue là min-heap,
 * nên {@code AutoBidConfig.compareTo} đảo thứ tự maxBid để phần tử có
 * maxBid cao nhất nằm ở đầu queue.
 *
 * @see AutoBidConfig
 * @see BiddingApplicationService
 */
public class AutoBidManager {

    // ========================== Singleton ==========================

    private static volatile AutoBidManager instance;

    /**
     * Trả về instance Singleton của {@code AutoBidManager}.
     *
     * @return instance duy nhất
     */
    public static AutoBidManager getInstance() {
        if (instance == null) {
            synchronized (AutoBidManager.class) {
                if (instance == null) {
                    instance = new AutoBidManager();
                }
            }
        }
        return instance;
    }

    // ========================== Fields ==========================

    /**
     * Map lưu trữ PriorityQueue cho từng phiên đấu giá.
     * <p>
     * Key: auctionId (Integer)<br>
     * Value: PriorityQueue&lt;AutoBidConfig&gt; sắp xếp theo maxBid giảm dần
     * </p>
     */
    private final Map<Integer, PriorityQueue<AutoBidConfig>> autoBidQueues;

    /**
     * Map lưu lock riêng cho từng auction để xử lý concurrency fine-grained.
     * Mỗi auction có một ReentrantLock riêng, tránh block toàn bộ hệ thống
     * khi chỉ một auction đang xử lý auto-bid.
     */
    private final Map<Integer, ReentrantLock> auctionLocks;

    // ========================== Constructor ==========================

    private AutoBidManager() {
        this.autoBidQueues = new ConcurrentHashMap<>();
        this.auctionLocks = new ConcurrentHashMap<>();
    }

    // ========================== Lock Management ==========================

    /**
     * Lấy hoặc tạo ReentrantLock cho một auction cụ thể.
     * Sử dụng {@code computeIfAbsent} của ConcurrentHashMap (thread-safe).
     *
     * @param auctionId ID phiên đấu giá
     * @return ReentrantLock dành riêng cho auction đó
     */
    private ReentrantLock getLock(int auctionId) {
        return auctionLocks.computeIfAbsent(auctionId, k -> new ReentrantLock(true));
    }

    // ========================== Public API ==========================

    /**
     * Đăng ký hoặc cập nhật cấu hình Auto-Bid cho một user trên một auction.
     * <p>
     * Mỗi user chỉ được phép có <b>tối đa 1</b> cấu hình auto-bid trên mỗi
     * phiên đấu giá. Nếu đã tồn tại, cấu hình cũ sẽ bị thay thế.
     * </p>
     *
     * @param username  username của Bidder
     * @param auctionId ID phiên đấu giá
     * @param maxBid    giá tối đa sẵn sàng trả
     * @param increment bước giá tự động
     * @return {@link AutoBidConfig} đã được đăng ký
     * @throws IllegalArgumentException nếu maxBid hoặc increment <= 0
     */
    public AutoBidConfig registerAutoBid(String username, int auctionId,
                                         float maxBid, float increment) {
        ReentrantLock lock = getLock(auctionId);
        lock.lock();
        try {
            PriorityQueue<AutoBidConfig> queue = autoBidQueues.computeIfAbsent(
                    auctionId, k -> new PriorityQueue<>());

            // Xóa cấu hình cũ nếu tồn tại (mỗi user chỉ có 1 config/auction)
            queue.removeIf(config -> config.getUsername().equals(username));

            AutoBidConfig newConfig = new AutoBidConfig(username, auctionId, maxBid, increment);
            queue.offer(newConfig);

            System.out.println("[AutoBidManager] Đã đăng ký auto-bid: " + newConfig);
            return newConfig;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Hủy cấu hình Auto-Bid của một user trên một auction.
     *
     * @param username  username của Bidder
     * @param auctionId ID phiên đấu giá
     * @return {@code true} nếu tìm thấy và hủy thành công
     */
    public boolean cancelAutoBid(String username, int auctionId) {
        ReentrantLock lock = getLock(auctionId);
        lock.lock();
        try {
            PriorityQueue<AutoBidConfig> queue = autoBidQueues.get(auctionId);
            if (queue == null) {
                return false;
            }

            boolean removed = queue.removeIf(config ->
                    config.getUsername().equals(username));

            if (removed) {
                System.out.println("[AutoBidManager] Đã hủy auto-bid của '"
                        + username + "' trên auction " + auctionId);
            }
            return removed;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Xử lý chuỗi Auto-Bid sau khi có một lượt đặt giá mới.
     * <p>
     * Đây là <b>phương thức cốt lõi</b> của hệ thống Auto-Bidding.
     * Sử dụng <b>thuật toán iterative</b> (vòng lặp) thay vì đệ quy để
     * tránh stack overflow khi nhiều auto-bidder "đấu" nhau.
     * </p>
     *
     * <h4>Thuật toán:</h4>
     * <pre>
     * WHILE true:
     *   1. Duyệt PriorityQueue tìm auto-bidder ưu tiên cao nhất
     *      mà KHÔNG phải là người vừa đặt giá (triggerUsername)
     *   2. Tính autoBidAmount = currentPrice + increment
     *   3. Nếu autoBidAmount <= maxBid → đặt giá, cập nhật context, CONTINUE
     *   4. Nếu autoBidAmount > maxBid → deactivate, thử người tiếp theo
     *   5. Hết người → BREAK
     * </pre>
     *
     * @param auctionId       ID phiên đấu giá
     * @param triggerUsername  username của người vừa đặt giá (manual hoặc auto)
     * @param currentPrice    giá hiện tại cao nhất sau lượt đặt giá vừa xong
     */
    public void processAutoBids(int auctionId, String triggerUsername, float currentPrice) {
        ReentrantLock lock = getLock(auctionId);
        lock.lock();
        try {
            PriorityQueue<AutoBidConfig> queue = autoBidQueues.get(auctionId);
            if (queue == null || queue.isEmpty()) {
                return;
            }

            // Lấy service instances (ngoài vòng lặp để tránh lookup lặp lại)
            BiddingApplicationService biddingService = BiddingApplicationService.getInstance();
            NetworkPushService pushService = NetworkPushService.getInstance();

            String currentTrigger = triggerUsername;
            float price = currentPrice;

            // === ITERATIVE AUTO-BID CHAIN ===
            // Giới hạn số vòng lặp tối đa để phòng tránh infinite loop
            int maxIterations = 100;
            int iteration = 0;

            while (iteration < maxIterations) {
                iteration++;

                AutoBidConfig candidate = findEligibleCandidate(queue, currentTrigger);
                if (candidate == null) {
                    // Không còn ai đủ điều kiện → kết thúc chuỗi
                    break;
                }

                float autoBidAmount = candidate.calculateAutoBidAmount(price);

                if (candidate.canBidAt(price)) {
                    // === CÓ THỂ ĐẶT GIÁ ===
                    boolean success = biddingService.placeBid(
                            candidate.getUsername(), auctionId, autoBidAmount, true);

                    if (success) {
                        System.out.println("[AutoBidManager] Auto-bid thành công: '"
                                + candidate.getUsername() + "' đặt " + autoBidAmount
                                + " trên auction " + auctionId);

                        // Thông báo realtime cho user rằng hệ thống đã đặt giá thay họ
                        pushAutoBidPlacedNotification(pushService, candidate, autoBidAmount);

                        // Cập nhật context cho vòng lặp tiếp theo
                        currentTrigger = candidate.getUsername();
                        price = autoBidAmount;

                        // Tiếp tục vòng lặp — có thể có auto-bidder khác phản hồi
                        continue;
                    } else {
                        // Bid thất bại (có thể do wallet không đủ, auction đã đóng, v.v.)
                        System.out.println("[AutoBidManager] Auto-bid thất bại cho '"
                                + candidate.getUsername() + "' — bỏ qua");
                        candidate.deactivate();
                        rebuildQueue(auctionId, queue);
                        continue;
                    }
                } else {
                    // === VƯỢT QUÁ GIỚI HẠN maxBid ===
                    System.out.println("[AutoBidManager] Auto-bid đạt giới hạn: '"
                            + candidate.getUsername() + "' maxBid=" + candidate.getMaxBid()
                            + " < cần " + autoBidAmount);

                    candidate.deactivate();
                    rebuildQueue(auctionId, queue);

                    // Thông báo cho user rằng auto-bid đã hết hiệu lực
                    pushAutoBidLimitReachedNotification(pushService, candidate, price);

                    // Thử auto-bidder tiếp theo trong queue
                    continue;
                }
            }

            if (iteration >= maxIterations) {
                System.err.println("[AutoBidManager] CẢNH BÁO: Đạt giới hạn vòng lặp ("
                        + maxIterations + ") cho auction " + auctionId);
            }

        } finally {
            lock.unlock();
        }
    }

    /**
     * Truy vấn cấu hình Auto-Bid hiện tại của một user trên một auction.
     *
     * @param username  username của Bidder
     * @param auctionId ID phiên đấu giá
     * @return {@link AutoBidConfig} nếu tồn tại và còn active, hoặc {@code null}
     */
    public AutoBidConfig getAutoBidConfig(String username, int auctionId) {
        ReentrantLock lock = getLock(auctionId);
        lock.lock();
        try {
            PriorityQueue<AutoBidConfig> queue = autoBidQueues.get(auctionId);
            if (queue == null) {
                return null;
            }

            return queue.stream()
                    .filter(config -> config.getUsername().equals(username) && config.isActive())
                    .findFirst()
                    .orElse(null);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Dọn dẹp toàn bộ cấu hình auto-bid khi một auction kết thúc.
     * Giải phóng bộ nhớ và lock liên quan.
     *
     * @param auctionId ID phiên đấu giá đã kết thúc
     */
    public void cleanupAuction(int auctionId) {
        ReentrantLock lock = getLock(auctionId);
        lock.lock();
        try {
            autoBidQueues.remove(auctionId);
            System.out.println("[AutoBidManager] Đã dọn dẹp auto-bid cho auction " + auctionId);
        } finally {
            lock.unlock();
        }
        // Xóa lock sau khi unlock (an toàn vì auction đã kết thúc)
        auctionLocks.remove(auctionId);
    }

    /**
     * Trả về số lượng cấu hình auto-bid active cho một auction.
     * (Hữu ích cho debug và monitoring)
     *
     * @param auctionId ID phiên đấu giá
     * @return số lượng auto-bid configs active
     */
    public int getActiveAutoBidCount(int auctionId) {
        ReentrantLock lock = getLock(auctionId);
        lock.lock();
        try {
            PriorityQueue<AutoBidConfig> queue = autoBidQueues.get(auctionId);
            if (queue == null) {
                return 0;
            }
            return (int) queue.stream().filter(AutoBidConfig::isActive).count();
        } finally {
            lock.unlock();
        }
    }

    // ========================== Private Helpers ==========================

    /**
     * Tìm ứng viên auto-bid đủ điều kiện ưu tiên cao nhất.
     * <p>
     * Ứng viên hợp lệ phải:
     * <ul>
     *   <li>KHÔNG phải là người vừa đặt giá ({@code triggerUsername})</li>
     *   <li>Còn {@code active}</li>
     * </ul>
     * </p>
     * <p>
     * Vì PriorityQueue không hỗ trợ iterate theo thứ tự ưu tiên (chỉ {@code poll/peek}
     * đảm bảo ordering), ta tạo bản sao đã sắp xếp để duyệt.
     * </p>
     *
     * @param queue           PriorityQueue của auction
     * @param triggerUsername  username cần loại trừ
     * @return AutoBidConfig ưu tiên cao nhất hợp lệ, hoặc {@code null}
     */
    private AutoBidConfig findEligibleCandidate(PriorityQueue<AutoBidConfig> queue,
                                                 String triggerUsername) {
        // Tạo danh sách đã sắp xếp từ PriorityQueue để duyệt theo đúng thứ tự ưu tiên
        List<AutoBidConfig> sorted = new ArrayList<>(queue);
        Collections.sort(sorted);

        for (AutoBidConfig config : sorted) {
            if (config.isActive() && !config.getUsername().equals(triggerUsername)) {
                return config;
            }
        }
        return null;
    }

    /**
     * Xây dựng lại PriorityQueue sau khi deactivate một phần tử.
     * <p>
     * Loại bỏ các config đã bị deactivate để giữ queue sạch.
     * </p>
     *
     * @param auctionId ID phiên đấu giá
     * @param queue     PriorityQueue cần rebuild
     */
    private void rebuildQueue(int auctionId, PriorityQueue<AutoBidConfig> queue) {
        queue.removeIf(config -> !config.isActive());
        if (queue.isEmpty()) {
            autoBidQueues.remove(auctionId);
        }
    }

    /**
     * Gửi thông báo push cho user khi hệ thống tự động đặt giá thành công.
     */
    private void pushAutoBidPlacedNotification(NetworkPushService pushService,
                                                AutoBidConfig config, float bidAmount) {
        try {
            pushService.pushAutoBidNotification(
                    config.getUsername(),
                    config.getAuctionId(),
                    "AUTO_BID_PLACED",
                    bidAmount,
                    config.getMaxBid(),
                    "Hệ thống đã tự động đặt giá " + bidAmount
                            + " cho bạn (giới hạn: " + config.getMaxBid() + ")"
            );
        } catch (RuntimeException e) {
            System.err.println("[AutoBidManager] Push notification thất bại: " + e.getMessage());
        }
    }

    /**
     * Gửi thông báo push cho user khi auto-bid đạt giới hạn maxBid.
     */
    private void pushAutoBidLimitReachedNotification(NetworkPushService pushService,
                                                      AutoBidConfig config, float currentPrice) {
        try {
            pushService.pushAutoBidNotification(
                    config.getUsername(),
                    config.getAuctionId(),
                    "AUTO_BID_LIMIT_REACHED",
                    currentPrice,
                    config.getMaxBid(),
                    "Auto-bid đã dừng! Giá hiện tại (" + currentPrice
                            + ") đã vượt quá giới hạn của bạn (" + config.getMaxBid() + ")"
            );
        } catch (RuntimeException e) {
            System.err.println("[AutoBidManager] Push notification thất bại: " + e.getMessage());
        }
    }
}
