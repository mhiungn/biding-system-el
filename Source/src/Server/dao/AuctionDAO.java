package Server.dao;

import CommonClasses.Bid;
import CommonClasses.Items.*;

import java.sql.*;
import java.util.*;
import java.util.Date;

/**
 * Lớp DAO quản lý việc lưu trữ dữ liệu {@link AuctionSnapshot} (phiên đấu giá)
 * trên MySQL.
 * <p>
 * DAO này xử lý các thao tác CRUD cho phiên đấu giá bao gồm snapshot chính,
 * danh sách bid, và danh sách người tham gia. Dữ liệu được phân bổ qua 3 bảng:
 * </p>
 * <ul>
 * <li>{@code auction_snapshots} — thông tin chính của phiên đấu giá + FK tới item</li>
 * <li>{@code auction_bids} — lịch sử các lượt đặt giá (sắp xếp giá cao nhất đầu
 * tiên)</li>
 * <li>{@code auction_participants} — danh sách username người tham gia</li>
 * </ul>
 *
 * <h3>Singleton Pattern:</h3>
 * Triển khai Singleton an toàn đa luồng bằng double-checked locking.
 *
 * <h3>Cấu trúc bảng:</h3>
 * 
 * <pre>
 *   auction_snapshots (
 *       auction_id          INT           PRIMARY KEY,
 *       client_owner        VARCHAR(50)   NOT NULL,
 *       item_id             VARCHAR(36)   NOT NULL FK → items(item_id),
 *       created_at          DATETIME,
 *       terminate_at        DATETIME,
 *       type                VARCHAR(30),
 *       status              VARCHAR(20)   DEFAULT 'OPEN',
 *       was_in_countdown    BOOLEAN       DEFAULT FALSE
 *   )
 *
 *   auction_bids (
 *       id                  INT AUTO_INCREMENT PRIMARY KEY,
 *       auction_id          INT FK → auction_snapshots,
 *       bid_amount          FLOAT,
 *       bidder_username     VARCHAR(50),
 *       created_at          DATETIME,
 *       bid_order           INT           -- 0 = giá cao nhất (đầu LinkedList)
 *   )
 *
 *   auction_participants (
 *       auction_id          INT FK → auction_snapshots,
 *       username            VARCHAR(50),
 *       PRIMARY KEY (auction_id, username)
 *   )
 * </pre>
 *
 * <h3>Lợi ích của thiết kế mới:</h3>
 * <ul>
 *   <li>❌ Không còn trùng lặp dữ liệu item (item chỉ lưu ở bảng items)</li>
 *   <li>✅ Item được lấy từ ItemDAO qua FK join</li>
 *   <li>✅ Khi seller cập nhật item → tự động thấy trong auction</li>
 *   <li>✅ Referential integrity enforced by database</li>
 * </ul>
 *
 * <h3>Transaction:</h3>
 * Các thao tác {@link #save} và {@link #update} sử dụng transaction để đảm bảo
 * tính toàn vẹn dữ liệu qua 3 bảng. Nếu có bất kỳ lỗi nào, toàn bộ thao tác
 * sẽ được rollback.
 *
 * <h3>Ví dụ sử dụng:</h3>
 * 
 * <pre>{@code
 * AuctionDAO auctionDAO = AuctionDAO.getInstance();
 * ItemDAO itemDAO = ItemDAO.getInstance();
 *
 * // Lưu item trước
 * String itemId = itemDAO.saveItem(item, "seller_ann");
 *
 * // Tạo snapshot chỉ với item_id (không inline item)
 * AuctionSnapshot snapshot = new AuctionSnapshot(1, "seller_ann", new Date(),
 *         endDate, "Time_Fixed", "OPEN", item, new LinkedList<>(), new ArrayList<>(), false);
 * auctionDAO.save("1", snapshot);
 *
 * // Lấy auction - item sẽ được load từ ItemDAO tự động
 * AuctionSnapshot found = auctionDAO.findById("1");
 * }</pre>
 *
 * @see AuctionSnapshot
 * @see GenericDAO
 * @see DatabaseConnection
 * @see ItemDAO
 */
public class AuctionDAO implements GenericDAO<String, AuctionSnapshot> {

    // ========================== Singleton ==========================

    /** Instance duy nhất của AuctionDAO. */
    private static volatile AuctionDAO instance;

    /**
     * Trả về instance Singleton của {@code AuctionDAO}.
     * Sử dụng double-checked locking để khởi tạo lazy an toàn đa luồng.
     *
     * @return instance Singleton của {@code AuctionDAO}
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

    // ========================== Constructor ==========================

    /**
     * Constructor private — sử dụng {@link #getInstance()}.
     * Tự động tạo các bảng {@code auction_snapshots}, {@code auction_bids},
     * và {@code auction_participants} nếu chưa tồn tại.
     */
    private AuctionDAO() {
        createTablesIfNotExist();
        System.out.println("[AuctionDAO] Đã khởi tạo với MySQL. Hiện có " + count() + " phiên đấu giá.");
    }

    // ========================== Tạo bảng ==========================

    /**
     * Tạo các bảng cần thiết trong MySQL nếu chưa tồn tại.
     * <p>
     * Thứ tự tạo bảng quan trọng: {@code auction_snapshots} phải được tạo trước
     * vì hai bảng còn lại tham chiếu foreign key đến nó với
     * {@code ON DELETE CASCADE}.
     * </p>
     */
    private void createTablesIfNotExist() {
        // Bảng snapshot chính (với FK tới items)
        String snapshotTable = "CREATE TABLE IF NOT EXISTS auction_snapshots ("
                + "auction_id          INT           PRIMARY KEY, "
                + "client_owner        VARCHAR(50)   NOT NULL, "
                + "item_id             VARCHAR(36)   NOT NULL, "
                + "created_at          DATETIME, "
                + "terminate_at        DATETIME, "
                + "type                VARCHAR(30), "
                + "minimum_bid_increment FLOAT     NOT NULL DEFAULT 1, "
                + "status              VARCHAR(20)   NOT NULL DEFAULT 'OPEN', "
                + "was_in_countdown    BOOLEAN       NOT NULL DEFAULT FALSE, "
                + "FOREIGN KEY (item_id) REFERENCES items(item_id) ON DELETE CASCADE"
                + ")";

        String bidsTable = "CREATE TABLE IF NOT EXISTS auction_bids ("
                + "id                  INT           AUTO_INCREMENT PRIMARY KEY, "
                + "auction_id          INT           NOT NULL, "
                + "bid_amount          FLOAT         NOT NULL, "
                + "bidder_username     VARCHAR(50), "
                + "created_at          DATETIME, "
                + "bid_order           INT           NOT NULL, "
                + "FOREIGN KEY (auction_id) REFERENCES auction_snapshots(auction_id) ON DELETE CASCADE, "
                + "INDEX idx_auction_id (auction_id), "
                + "INDEX idx_bid_order (bid_order)"
                + ")";

        String participantsTable = "CREATE TABLE IF NOT EXISTS auction_participants ("
                + "auction_id          INT           NOT NULL, "
                + "username            VARCHAR(50)   NOT NULL, "
                + "PRIMARY KEY (auction_id, username), "
                + "FOREIGN KEY (auction_id) REFERENCES auction_snapshots(auction_id) ON DELETE CASCADE, "
                + "FOREIGN KEY (username) REFERENCES users(username) ON DELETE CASCADE"
                + ")";

        try (Connection conn = DatabaseConnection.getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute(snapshotTable);
            stmt.execute(bidsTable);
            stmt.execute(participantsTable);
            addColumnIfMissing(conn, "auction_snapshots", "minimum_bid_increment", "FLOAT NOT NULL DEFAULT 1");
        } catch (SQLException e) {
            throw new RuntimeException("[AuctionDAO] Không thể tạo bảng", e);
        }
    }

    // ========================== Triển khai GenericDAO ==========================

    /**
     * Lưu một phiên đấu giá mới vào cơ sở dữ liệu.
     * <p>
     * Thao tác lưu được thực hiện trong một transaction duy nhất bao gồm:
     * snapshot chính (chỉ chứa item_id, không inline item), danh sách bid, 
     * và danh sách người tham gia.
     * Nếu phiên đấu giá với ID đã tồn tại, tự động chuyển sang {@link #update}.
     * </p>
     *
     * @param auctionId ID duy nhất của phiên đấu giá (dạng chuỗi số)
     * @param snapshot  đối tượng AuctionSnapshot cần lưu
     * @throws IllegalArgumentException nếu auctionId rỗng/null hoặc snapshot là
     *                                  null
     */
    @Override
    public void save(String auctionId, AuctionSnapshot snapshot) {
        if (auctionId == null || auctionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Auction ID không thể null hoặc để trống.");
        }
        if (snapshot == null) {
            throw new IllegalArgumentException("Auction Snapshot không thể null.");
        }

        // Nếu đã tồn tại, chuyển sang update
        if (exists(auctionId)) {
            System.out.println("[AuctionDAO] Phiên " + auctionId + " đã tồn tại — chuyển sang update.");
            update(auctionId, snapshot);
            return;
        }

        int id = parseAuctionId(auctionId);
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            // 1. Lưu thông tin chính của phiên đấu giá (chỉ item_id, không inline)
            insertSnapshot(conn, id, snapshot);

            // 2. Lưu danh sách bid
            insertBids(conn, id, snapshot.getBidList());

            // 3. Lưu danh sách người tham gia
            insertParticipants(conn, id, snapshot.getRegisteredUsernames());

            conn.commit();
            System.out.println("[AuctionDAO] Đã lưu phiên đấu giá: " + auctionId);
        } catch (SQLException e) {
            rollbackQuietly(conn);
            throw new RuntimeException("[AuctionDAO] Lỗi khi lưu phiên đấu giá: " + auctionId, e);
        } finally {
            closeQuietly(conn);
        }
    }

    /**
     * Tìm phiên đấu giá theo ID.
     * <p>
     * Dữ liệu được tập hợp từ 3 bảng: snapshot chính, danh sách bid
     * (sắp xếp theo {@code bid_order}), danh sách người tham gia,
     * và item được load từ ItemDAO qua item_id.
     * </p>
     *
     * @param auctionId ID phiên đấu giá cần tìm (dạng chuỗi số)
     * @return {@link AuctionSnapshot} nếu tìm thấy, hoặc {@code null} nếu không tồn
     *         tại
     */
    @Override
    public AuctionSnapshot findById(String auctionId) {
        String sql = "SELECT * FROM auction_snapshots WHERE auction_id = ?";
        int id = parseAuctionId(auctionId);

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    AuctionSnapshot snapshot = mapResultSetToSnapshot(rs);
                    // Load item từ ItemDAO
                    if (snapshot != null) {
                        String itemId = rs.getString("item_id");
                        if (itemId != null && !itemId.trim().isEmpty()) {
                            Item item = ItemDAO.getInstance().findById(itemId);
                            if (item != null) {
                                snapshot.setItem(item);
                            }
                        }
                    }
                    snapshot.setBidList(loadBids(id));
                    snapshot.setRegisteredUsernames(loadParticipants(id));
                    return snapshot;
                }
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("[AuctionDAO] Lỗi khi tìm phiên đấu giá: " + auctionId, e);
        }
    }

    /**
     * Trả về tất cả phiên đấu giá đã lưu.
     *
     * @return danh sách tất cả AuctionSnapshot; trả về danh sách rỗng nếu không có
     */
    @Override
    public List<AuctionSnapshot> findAll() {
        String sql = "SELECT * FROM auction_snapshots";
        List<AuctionSnapshot> result = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                AuctionSnapshot snapshot = mapResultSetToSnapshot(rs);
                int id = snapshot.getAuctionId();
                
                // Load item từ ItemDAO
                String itemId = rs.getString("item_id");
                if (itemId != null) {
                    Item item = ItemDAO.getInstance().findById(itemId);
                    if (item != null) {
                        snapshot.setItem(item);
                    }
                }
                
                snapshot.setBidList(loadBids(id));
                snapshot.setRegisteredUsernames(loadParticipants(id));
                result.add(snapshot);
            }
        } catch (SQLException e) {
            throw new RuntimeException("[AuctionDAO] Lỗi khi lấy tất cả phiên đấu giá", e);
        }
        return result;
    }

    /**
     * Cập nhật thông tin một phiên đấu giá đã tồn tại.
     * <p>
     * Thao tác cập nhật được thực hiện trong transaction: cập nhật snapshot chính,
     * xóa rồi chèn lại toàn bộ danh sách bid và danh sách người tham gia.
     * </p>
     *
     * @param auctionId ID của phiên cần cập nhật (dạng chuỗi số)
     * @param snapshot  dữ liệu AuctionSnapshot mới
     * @return {@code true} nếu tìm thấy và cập nhật thành công
     */
    @Override
    public boolean update(String auctionId, AuctionSnapshot snapshot) {
        int id = parseAuctionId(auctionId);
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            // 1. Cập nhật thông tin chính (chỉ cập nhật item_id, không inline)
            String sql = "UPDATE auction_snapshots SET client_owner = ?, item_id = ?, "
                    + "created_at = ?, terminate_at = ?, type = ?, status = ?, was_in_countdown = ?, minimum_bid_increment = ? "
                    + "WHERE auction_id = ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, snapshot.getClientOwner());
                
                // Lấy item_id từ item hoặc để null
                String itemId = null;
                if (snapshot.getItem() != null) {
                    // Nếu có item object, cần tìm item_id của nó
                    // Thường là seller sẽ biết item_id của item mình
                    // Hoặc có thể lấy từ context khác
                    // Ở đây giả sử item_id đã được set sẵn
                    itemId = getItemIdFromItem(snapshot.getItem());
                }
                ps.setString(2, itemId);
                
                ps.setTimestamp(3, toTimestamp(snapshot.getCreatedAt()));
                ps.setTimestamp(4, toTimestamp(snapshot.getTerminateAt()));
                ps.setString(5, snapshot.getType());
                ps.setString(6, snapshot.getStatus());
                ps.setBoolean(7, snapshot.wasInCountDown());
                ps.setFloat(8, snapshot.getMinimumBidIncrement());
                ps.setInt(9, id);
                
                int rows = ps.executeUpdate();

                if (rows == 0) {
                    conn.rollback();
                    return false;
                }
            }

            // 2. Xóa bid cũ rồi chèn lại
            deleteBids(conn, id);
            insertBids(conn, id, snapshot.getBidList());

            // 3. Xóa participant cũ rồi chèn lại
            deleteParticipants(conn, id);
            insertParticipants(conn, id, snapshot.getRegisteredUsernames());

            conn.commit();
            System.out.println("[AuctionDAO] Đã cập nhật phiên đấu giá: " + auctionId);
            return true;
        } catch (SQLException e) {
            rollbackQuietly(conn);
            throw new RuntimeException("[AuctionDAO] Lỗi khi cập nhật phiên đấu giá: " + auctionId, e);
        } finally {
            closeQuietly(conn);
        }
    }

    /**
     * Xóa một phiên đấu giá theo ID.
     * <p>
     * {@code ON DELETE CASCADE} trên foreign key sẽ tự động xóa
     * các dòng liên quan trong {@code auction_bids} và
     * {@code auction_participants}.
     * </p>
     *
     * @param auctionId ID của phiên cần xóa (dạng chuỗi số)
     * @return {@code true} nếu tìm thấy và xóa thành công
     */
    @Override
    public boolean delete(String auctionId) {
        String sql = "DELETE FROM auction_snapshots WHERE auction_id = ?";
        int id = parseAuctionId(auctionId);

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                System.out.println("[AuctionDAO] Đã xóa phiên đấu giá: " + auctionId);
                return true;
            }
            return false;
        } catch (SQLException e) {
            throw new RuntimeException("[AuctionDAO] Lỗi khi xóa phiên đấu giá: " + auctionId, e);
        }
    }

    /**
     * Kiểm tra phiên đấu giá với ID cho trước có tồn tại hay không.
     *
     * @param auctionId ID phiên cần kiểm tra (dạng chuỗi số)
     * @return {@code true} nếu tồn tại
     */
    @Override
    public boolean exists(String auctionId) {
        String sql = "SELECT COUNT(*) FROM auction_snapshots WHERE auction_id = ?";
        int id = parseAuctionId(auctionId);

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
                return false;
            }
        } catch (SQLException e) {
            throw new RuntimeException("[AuctionDAO] Lỗi khi kiểm tra tồn tại: " + auctionId, e);
        }
    }

    /**
     * Trả về tổng số phiên đấu giá đã lưu.
     *
     * @return số lượng phiên đấu giá
     */
    @Override
    public int count() {
        String sql = "SELECT COUNT(*) FROM auction_snapshots";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        } catch (SQLException e) {
            throw new RuntimeException("[AuctionDAO] Lỗi khi đếm phiên đấu giá", e);
        }
    }

    // ========================== Phương thức riêng cho Auction
    // ==========================

    /**
     * Tìm tất cả phiên đấu giá theo trạng thái.
     *
     * @param status trạng thái cần lọc (OPEN, RUNNING, FINISHED, PAID, CANCELED)
     * @return danh sách phiên đấu giá có trạng thái tương ứng
     */
    public List<AuctionSnapshot> findByStatus(String status) {
        String sql = "SELECT * FROM auction_snapshots WHERE UPPER(status) = ?";
        List<AuctionSnapshot> result = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AuctionSnapshot snapshot = mapResultSetToSnapshot(rs);
                    int id = snapshot.getAuctionId();
                    
                    // Load item từ ItemDAO
                    String itemId = rs.getString("item_id");
                    if (itemId != null) {
                        Item item = ItemDAO.getInstance().findById(itemId);
                        if (item != null) {
                            snapshot.setItem(item);
                        }
                    }
                    
                    snapshot.setBidList(loadBids(id));
                    snapshot.setRegisteredUsernames(loadParticipants(id));
                    result.add(snapshot);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("[AuctionDAO] Lỗi khi tìm phiên theo trạng thái: " + status, e);
        }
        return result;
    }

    /**
     * Tìm tất cả phiên đấu giá do một seller cụ thể tạo.
     *
     * @param clientOwner username của seller
     * @return danh sách phiên đấu giá của seller đó
     */
    public List<AuctionSnapshot> findByClientOwner(String clientOwner) {
        String sql = "SELECT * FROM auction_snapshots WHERE client_owner = ?";
        List<AuctionSnapshot> result = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, clientOwner);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AuctionSnapshot snapshot = mapResultSetToSnapshot(rs);
                    int id = snapshot.getAuctionId();
                    
                    // Load item từ ItemDAO
                    String itemId = rs.getString("item_id");
                    if (itemId != null) {
                        Item item = ItemDAO.getInstance().findById(itemId);
                        if (item != null) {
                            snapshot.setItem(item);
                        }
                    }
                    
                    snapshot.setBidList(loadBids(id));
                    snapshot.setRegisteredUsernames(loadParticipants(id));
                    result.add(snapshot);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("[AuctionDAO] Lỗi khi tìm phiên theo chủ: " + clientOwner, e);
        }
        return result;
    }

    /**
     * Cập nhật nhanh trạng thái của một phiên đấu giá mà không cần truyền toàn bộ
     * snapshot.
     *
     * @param auctionId ID phiên cần cập nhật
     * @param newStatus trạng thái mới (OPEN / RUNNING / FINISHED / PAID / CANCELED)
     * @return {@code true} nếu cập nhật thành công
     */
    public boolean updateStatus(String auctionId, String newStatus) {
        String sql = "UPDATE auction_snapshots SET status = ? WHERE auction_id = ?";
        int id = parseAuctionId(auctionId);

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newStatus);
            ps.setInt(2, id);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                System.out.println("[AuctionDAO] Đã cập nhật trạng thái phiên " + auctionId + " → " + newStatus);
                return true;
            }
            return false;
        } catch (SQLException e) {
            throw new RuntimeException("[AuctionDAO] Lỗi khi cập nhật trạng thái: " + auctionId, e);
        }
    }

    public boolean updateTerminateAt(String auctionId, Date terminateAt) {
        String sql = "UPDATE auction_snapshots SET terminate_at = ? WHERE auction_id = ?";
        int id = parseAuctionId(auctionId);

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, toTimestamp(terminateAt));
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("[AuctionDAO] Lỗi khi cập nhật thời gian kết thúc: " + auctionId, e);
        }
    }

    /**
     * Trả về tất cả phiên đấu giá dưới dạng Map (auctionId → AuctionSnapshot).
     *
     * @return map mới chứa tất cả phiên đấu giá
     */
    public Map<String, AuctionSnapshot> findAllAsMap() {
        List<AuctionSnapshot> all = findAll();
        Map<String, AuctionSnapshot> result = new HashMap<>();
        for (AuctionSnapshot snapshot : all) {
            result.put(String.valueOf(snapshot.getAuctionId()), snapshot);
        }
        return result;
    }

    public int countDashboardAuctions(String category, boolean endingSoon, Float minPriceInclusive, Float maxPriceInclusive) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM (" +
                " SELECT s.auction_id " +
                " FROM auction_snapshots s " +
                " JOIN items i ON i.item_id = s.item_id " +
                " LEFT JOIN (SELECT auction_id, MAX(bid_amount) AS max_bid FROM auction_bids GROUP BY auction_id) b ON b.auction_id = s.auction_id " +
                " WHERE s.status IN ('OPEN','RUNNING')"
                + " AND s.terminate_at IS NOT NULL AND s.terminate_at > NOW()"
        );
        List<Object> params = new ArrayList<>();
        appendDashboardFilters(sql, params, category, endingSoon, minPriceInclusive, maxPriceInclusive);
        sql.append(" ) x");

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
            return 0;
        } catch (SQLException e) {
            throw new RuntimeException("[AuctionDAO] Lỗi đếm auction dashboard", e);
        }
    }

    public List<DashboardAuctionRow> findDashboardAuctions(String category, boolean endingSoon,
                                                           Float minPriceInclusive, Float maxPriceInclusive,
                                                           int limit, int offset) {
        StringBuilder sql = new StringBuilder(
                "SELECT s.auction_id, s.status, s.created_at, s.terminate_at, " +
                " s.minimum_bid_increment, i.item_id, i.item_type, i.name, i.description, i.starting_price, " +
                " i.auction_start_time, i.auction_end_time, i.item_condition, i.location, " +
                " COALESCE(b.max_bid, i.current_highest_price, i.starting_price) AS current_price, " +
                " COALESCE(b.bid_count, 0) AS bid_count " +
                " FROM auction_snapshots s " +
                " JOIN items i ON i.item_id = s.item_id " +
                " LEFT JOIN (SELECT auction_id, MAX(bid_amount) AS max_bid, COUNT(*) AS bid_count FROM auction_bids GROUP BY auction_id) b " +
                " ON b.auction_id = s.auction_id " +
                " WHERE s.status IN ('OPEN','RUNNING')"
                + " AND s.terminate_at IS NOT NULL AND s.terminate_at > NOW()"
        );
        List<Object> params = new ArrayList<>();
        appendDashboardFilters(sql, params, category, endingSoon, minPriceInclusive, maxPriceInclusive);
        sql.append(" ORDER BY s.terminate_at ASC, s.auction_id DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        List<DashboardAuctionRow> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Item item = createItemFromDashboardRow(rs);
                    int auctionId = rs.getInt("auction_id");
                    String status = rs.getString("status");
                    Date startTime = toDate(rs.getTimestamp("created_at"));
                    Date endTime = toDate(rs.getTimestamp("terminate_at"));
                    int bidCount = rs.getInt("bid_count");
                    float minimumBidIncrement = rs.getFloat("minimum_bid_increment");
                    result.add(new DashboardAuctionRow(auctionId, status, startTime, endTime, item, bidCount, minimumBidIncrement));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("[AuctionDAO] Lỗi tải auction dashboard", e);
        }
    }

    public int countActiveAuctions() {
        String sql = "SELECT COUNT(*) FROM auction_snapshots "
                + "WHERE status IN ('OPEN', 'RUNNING') "
                + "AND terminate_at IS NOT NULL AND terminate_at > NOW()";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        } catch (SQLException e) {
            throw new RuntimeException("[AuctionDAO] Lỗi đếm active auctions", e);
        }
    }

    public int countEndingTodayAuctions() {
        String sql = "SELECT COUNT(*) FROM auction_snapshots "
                + "WHERE status IN ('OPEN', 'RUNNING') "
                + "AND terminate_at IS NOT NULL AND terminate_at > NOW() "
                + "AND DATE(terminate_at) = CURDATE()";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        } catch (SQLException e) {
            throw new RuntimeException("[AuctionDAO] Lỗi đếm auction ending today", e);
        }
    }

    public int countTotalBids() {
        String sql = "SELECT COUNT(*) FROM auction_bids";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        } catch (SQLException e) {
            throw new RuntimeException("[AuctionDAO] Lỗi đếm tổng bids", e);
        }
    }

    /**
     * Returns a full DashboardAuctionRow for a single auction by its ID.
     * Used by BiddingDetailController to load auction detail.
     *
     * @param auctionId the auction ID
     * @return DashboardAuctionRow with full item detail, or null if not found
     */
    public DashboardAuctionRow findFullAuctionDetail(int auctionId) {
        String sql = "SELECT s.auction_id, s.status, s.created_at, s.terminate_at, s.client_owner, "
                + " s.minimum_bid_increment, i.item_id, i.item_type, i.name, i.description, i.starting_price, "
                + " i.auction_start_time, i.auction_end_time, i.item_condition, i.location, "
                + " COALESCE(b.max_bid, i.current_highest_price, i.starting_price) AS current_price, "
                + " COALESCE(b.bid_count, 0) AS bid_count "
                + " FROM auction_snapshots s "
                + " JOIN items i ON i.item_id = s.item_id "
                + " LEFT JOIN (SELECT auction_id, MAX(bid_amount) AS max_bid, COUNT(*) AS bid_count FROM auction_bids GROUP BY auction_id) b "
                + " ON b.auction_id = s.auction_id "
                + " WHERE s.auction_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Item item = createItemFromDashboardRow(rs);
                    String status = rs.getString("status");
                    Date startTime = toDate(rs.getTimestamp("created_at"));
                    Date endTime = toDate(rs.getTimestamp("terminate_at"));
                    int bidCount = rs.getInt("bid_count");
                    float minimumBidIncrement = rs.getFloat("minimum_bid_increment");
                    return new DashboardAuctionRow(auctionId, status, startTime, endTime, item, bidCount, minimumBidIncrement);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("[AuctionDAO] Lỗi khi tải chi tiết auction: " + auctionId, e);
        }
    }

    /**
     * Returns the owner username for a given auction.
     *
     * @param auctionId the auction ID
     * @return the owner username, or null if auction not found
     */
    public String findAuctionOwner(int auctionId) {
        String sql = "SELECT client_owner FROM auction_snapshots WHERE auction_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("client_owner");
                }
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("[AuctionDAO] Lỗi khi tìm owner auction: " + auctionId, e);
        }
    }

    /**
     * Finds all active auctions where the given user is a participant.
     * Used by MyBidsController to show active bids.
     *
     * @param username the participant username
     * @return list of DashboardAuctionRow for active auctions the user participates in
     */
    public List<DashboardAuctionRow> findActiveAuctionsByParticipant(String username) {
        String sql = "SELECT s.auction_id, s.status, s.created_at, s.terminate_at, "
                + " s.minimum_bid_increment, i.item_id, i.item_type, i.name, i.description, i.starting_price, "
                + " i.auction_start_time, i.auction_end_time, i.item_condition, i.location, "
                + " COALESCE(b.max_bid, i.current_highest_price, i.starting_price) AS current_price, "
                + " COALESCE(b.bid_count, 0) AS bid_count "
                + " FROM auction_participants p "
                + " JOIN auction_snapshots s ON s.auction_id = p.auction_id "
                + " JOIN items i ON i.item_id = s.item_id "
                + " LEFT JOIN (SELECT auction_id, MAX(bid_amount) AS max_bid, COUNT(*) AS bid_count FROM auction_bids GROUP BY auction_id) b "
                + "   ON b.auction_id = s.auction_id "
                + " WHERE p.username = ? AND s.status IN ('OPEN','RUNNING') "
                + " AND s.terminate_at IS NOT NULL AND s.terminate_at > NOW() "
                + " ORDER BY s.terminate_at ASC";

        List<DashboardAuctionRow> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Item item = createItemFromDashboardRow(rs);
                    result.add(new DashboardAuctionRow(
                            rs.getInt("auction_id"), rs.getString("status"),
                            toDate(rs.getTimestamp("created_at")),
                            toDate(rs.getTimestamp("terminate_at")),
                            item, rs.getInt("bid_count"), rs.getFloat("minimum_bid_increment")));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("[AuctionDAO] Lỗi tìm active auctions cho user: " + username, e);
        }
    }

    /**
     * Finds all completed/cancelled auctions where the given user placed bids.
     * Used by MyBidsController to show completed bids history.
     *
     * @param username the bidder username
     * @return list of DashboardAuctionRow for completed auctions
     */
    public List<DashboardAuctionRow> findCompletedAuctionsByBidder(String username) {
        String sql = "SELECT DISTINCT s.auction_id, s.status, s.created_at, s.terminate_at, "
                + " s.minimum_bid_increment, i.item_id, i.item_type, i.name, i.description, i.starting_price, "
                + " i.auction_start_time, i.auction_end_time, i.item_condition, i.location, "
                + " COALESCE(b_max.max_bid, i.current_highest_price, i.starting_price) AS current_price, "
                + " COALESCE(b_max.bid_count, 0) AS bid_count "
                + " FROM auction_bids ab "
                + " JOIN auction_snapshots s ON s.auction_id = ab.auction_id "
                + " JOIN items i ON i.item_id = s.item_id "
                + " LEFT JOIN (SELECT auction_id, MAX(bid_amount) AS max_bid, COUNT(*) AS bid_count FROM auction_bids GROUP BY auction_id) b_max "
                + "   ON b_max.auction_id = s.auction_id "
                + " WHERE ab.bidder_username = ? AND s.status IN ('FINISHED','PAID','CANCELED') "
                + " ORDER BY s.terminate_at DESC";

        List<DashboardAuctionRow> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Item item = createItemFromDashboardRow(rs);
                    result.add(new DashboardAuctionRow(
                            rs.getInt("auction_id"), rs.getString("status"),
                            toDate(rs.getTimestamp("created_at")),
                            toDate(rs.getTimestamp("terminate_at")),
                            item, rs.getInt("bid_count"), rs.getFloat("minimum_bid_increment")));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("[AuctionDAO] Lỗi tìm completed auctions cho user: " + username, e);
        }
    }

    /**
     * Returns the highest bid placed by a specific user on a specific auction.
     *
     * @param auctionId the auction ID
     * @param username  the bidder username
     * @return the user's highest bid amount, or 0 if no bid placed
     */
    public float getUserHighestBid(int auctionId, String username) {
        String sql = "SELECT MAX(bid_amount) AS max_bid FROM auction_bids WHERE auction_id = ? AND bidder_username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, auctionId);
            ps.setString(2, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getFloat("max_bid");
                }
                return 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("[AuctionDAO] Lỗi khi lấy highest bid của user", e);
        }
    }

    /**
     * Returns the username of the current highest bidder for an auction.
     *
     * @param auctionId the auction ID
     * @return username of highest bidder, or null if no bids
     */
    public String getHighestBidderUsername(int auctionId) {
        String sql = "SELECT bidder_username FROM auction_bids WHERE auction_id = ? ORDER BY bid_order ASC LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("bidder_username");
                }
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("[AuctionDAO] Lỗi khi tìm highest bidder", e);
        }
    }

    /**
     * Returns the bid history for a given auction (ordered highest first).
     *
     * @param auctionId the auction ID
     * @return list of Bids ordered by bid_order ASC (highest first)
     */
    public List<Bid> getBidHistoryForAuction(int auctionId) {
        return new ArrayList<>(loadBids(auctionId));
    }

    /**
     * Counts auctions created by a specific user.
     *
     * @param username the owner username
     * @return count of auctions created
     */
    public int countCreatedByUser(String username) {
        String sql = "SELECT COUNT(*) FROM auction_snapshots WHERE client_owner = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("[AuctionDAO] Lỗi đếm auctions created by user", e);
        }
    }

    /**
     * Counts auctions won by a specific user (highest bidder in FINISHED auctions).
     *
     * @param username the bidder username
     * @return count of auctions won
     */
    public int countWonByUser(String username) {
        String sql = "SELECT COUNT(*) FROM auction_snapshots s "
                + " JOIN auction_bids b ON b.auction_id = s.auction_id AND b.bid_order = 0 "
                + " WHERE s.status = 'FINISHED' AND b.bidder_username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("[AuctionDAO] Lỗi đếm auctions won by user", e);
        }
    }

    /**
     * Counts total bids placed by a specific user.
     *
     * @param username the bidder username
     * @return count of bids placed
     */
    public int countBidsByUser(String username) {
        String sql = "SELECT COUNT(*) FROM auction_bids WHERE bidder_username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("[AuctionDAO] Lỗi đếm bids by user", e);
        }
    }

    /**
     * Counts active auctions where the user is currently participating.
     *
     * @param username the participant username
     * @return count of active participations
     */
    public int countActiveParticipations(String username) {
        String sql = "SELECT COUNT(*) FROM auction_participants p "
                + " JOIN auction_snapshots s ON s.auction_id = p.auction_id "
                + " WHERE p.username = ? AND s.status IN ('OPEN','RUNNING') "
                + " AND s.terminate_at IS NOT NULL AND s.terminate_at > NOW()";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("[AuctionDAO] Lỗi đếm active participations", e);
        }
    }

    /**
     * Thêm một bid vào phiên đấu giá đang tồn tại.
     * <p>
     * Bid mới luôn là giá cao nhất nên được chèn vào đầu danh sách
     * (bid_order = 0). Các bid cũ sẽ được dịch chuyển lên 1 bậc.
     * </p>
     *
     * @param auctionId ID phiên đấu giá
     * @param bid       bid cần thêm
     */
    public void addBid(String auctionId, Bid bid) {
        int id = parseAuctionId(auctionId);
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            // Dịch chuyển thứ tự của các bid hiện tại lên 1
            String shiftSql = "UPDATE auction_bids SET bid_order = bid_order + 1 WHERE auction_id = ?";
            try (PreparedStatement shiftPs = conn.prepareStatement(shiftSql)) {
                shiftPs.setInt(1, id);
                shiftPs.executeUpdate();
            }

            // Chèn bid mới vào vị trí đầu tiên (bid_order = 0)
            String insertSql = "INSERT INTO auction_bids "
                    + "(auction_id, bid_amount, bidder_username, created_at, bid_order) "
                    + "VALUES (?, ?, ?, ?, 0)";
            try (PreparedStatement insertPs = conn.prepareStatement(insertSql)) {
                insertPs.setInt(1, id);
                insertPs.setFloat(2, bid.getBid());
                insertPs.setString(3, bid.getBidderUsername());
                insertPs.setTimestamp(4, toTimestamp(bid.getCreatedAt()));
                insertPs.executeUpdate();
            }

            conn.commit();
            System.out.println("[AuctionDAO] Đã thêm bid " + bid.getBid()
                    + " của " + bid.getBidderUsername() + " vào phiên " + auctionId);
        } catch (SQLException e) {
            rollbackQuietly(conn);
            throw new RuntimeException("[AuctionDAO] Lỗi khi thêm bid vào phiên: " + auctionId, e);
        } finally {
            closeQuietly(conn);
        }
    }

    /**
     * Thêm một người tham gia vào phiên đấu giá.
     * <p>
     * Nếu người tham gia đã tồn tại (trùng primary key), thao tác bị bỏ qua
     * mà không ném ngoại lệ (dùng {@code INSERT IGNORE}).
     * </p>
     *
     * @param auctionId ID phiên đấu giá
     * @param username  username của người tham gia
     */
    public void addParticipant(String auctionId, String username) {
        String sql = "INSERT IGNORE INTO auction_participants (auction_id, username) VALUES (?, ?)";
        int id = parseAuctionId(auctionId);

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setString(2, username);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                System.out.println("[AuctionDAO] Đã thêm người tham gia '"
                        + username + "' vào phiên " + auctionId);
            }
        } catch (SQLException e) {
            throw new RuntimeException("[AuctionDAO] Lỗi khi thêm người tham gia", e);
        }
    }

    /**
     * Xóa một người tham gia khỏi phiên đấu giá.
     *
     * @param auctionId ID phiên đấu giá
     * @param username  username của người cần xóa
     * @return {@code true} nếu tìm thấy và xóa thành công
     */
    public boolean removeParticipant(String auctionId, String username) {
        String sql = "DELETE FROM auction_participants WHERE auction_id = ? AND username = ?";
        int id = parseAuctionId(auctionId);

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setString(2, username);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("[AuctionDAO] Lỗi khi xóa người tham gia", e);
        }
    }

    // ========================== Phương thức Private — Ghi dữ liệu
    // ==========================

    /**
     * Chèn dòng mới vào bảng {@code auction_snapshots}.
     * Lưu item_id (FK), không lưu inline item.
     *
     * @param conn     kết nối đang mở (trong transaction)
     * @param id       auction_id dạng int
     * @param snapshot dữ liệu cần chèn
     * @throws SQLException nếu lỗi SQL
     */
    private void insertSnapshot(Connection conn, int id, AuctionSnapshot snapshot) throws SQLException {
        String sql = "INSERT INTO auction_snapshots "
                + "(auction_id, client_owner, item_id, created_at, terminate_at, type, status, was_in_countdown, minimum_bid_increment) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setString(2, snapshot.getClientOwner());
            
            // Lấy item_id từ item hoặc để null
            String itemId = null;
            if (snapshot.getItem() != null) {
                itemId = getItemIdFromItem(snapshot.getItem());
            }
            ps.setString(3, itemId);
            
            ps.setTimestamp(4, toTimestamp(snapshot.getCreatedAt()));
            ps.setTimestamp(5, toTimestamp(snapshot.getTerminateAt()));
            ps.setString(6, snapshot.getType());
            ps.setString(7, snapshot.getStatus());
            ps.setBoolean(8, snapshot.wasInCountDown());
            ps.setFloat(9, snapshot.getMinimumBidIncrement());
            ps.executeUpdate();
        }
    }

    /**
     * Chèn danh sách bid vào bảng {@code auction_bids}.
     * Thứ tự trong LinkedList được bảo toàn qua cột {@code bid_order}.
     *
     * @param conn    kết nối đang mở (trong transaction)
     * @param id      auction_id dạng int
     * @param bidList danh sách bid cần lưu
     * @throws SQLException nếu lỗi SQL
     */
    private void insertBids(Connection conn, int id, LinkedList<Bid> bidList) throws SQLException {
        if (bidList == null || bidList.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO auction_bids "
                + "(auction_id, bid_amount, bidder_username, created_at, bid_order) "
                + "VALUES (?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int order = 0;
            for (Bid bid : bidList) {
                ps.setInt(1, id);
                ps.setFloat(2, bid.getBid());
                ps.setString(3, bid.getBidderUsername());
                ps.setTimestamp(4, toTimestamp(bid.getCreatedAt()));
                ps.setInt(5, order++);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /**
     * Chèn danh sách người tham gia vào bảng {@code auction_participants}.
     *
     * @param conn      kết nối đang mở (trong transaction)
     * @param id        auction_id dạng int
     * @param usernames danh sách username
     * @throws SQLException nếu lỗi SQL
     */
    private void insertParticipants(Connection conn, int id, List<String> usernames) throws SQLException {
        if (usernames == null || usernames.isEmpty()) {
            return;
        }

        String sql = "INSERT IGNORE INTO auction_participants (auction_id, username) VALUES (?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (String username : usernames) {
                ps.setInt(1, id);
                ps.setString(2, username);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /**
     * Xóa tất cả bid của một phiên (dùng trước khi chèn lại trong update).
     */
    private void deleteBids(Connection conn, int auctionId) throws SQLException {
        String sql = "DELETE FROM auction_bids WHERE auction_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, auctionId);
            ps.executeUpdate();
        }
    }

    /**
     * Xóa tất cả participant của một phiên (dùng trước khi chèn lại trong update).
     */
    private void deleteParticipants(Connection conn, int auctionId) throws SQLException {
        String sql = "DELETE FROM auction_participants WHERE auction_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, auctionId);
            ps.executeUpdate();
        }
    }

    // ========================== Phương thức Private — Đọc dữ liệu
    // ==========================

    /**
     * Chuyển đổi một dòng {@link ResultSet} từ bảng {@code auction_snapshots}
     * thành đối tượng {@link AuctionSnapshot}.
     * <p>
     * Chỉ đọc các trường trong bảng chính. Item, bid list, và participant
     * cần được load riêng.
     * </p>
     *
     * @param rs ResultSet đang trỏ tới dòng cần đọc
     * @return đối tượng AuctionSnapshot (chưa load đủ item, bidList, participants)
     * @throws SQLException nếu lỗi đọc dữ liệu
     */
    private AuctionSnapshot mapResultSetToSnapshot(ResultSet rs) throws SQLException {
        AuctionSnapshot snapshot = new AuctionSnapshot();
        snapshot.setAuctionId(rs.getInt("auction_id"));
        snapshot.setClientOwner(rs.getString("client_owner"));
        snapshot.setCreatedAt(toDate(rs.getTimestamp("created_at")));
        snapshot.setTerminateAt(toDate(rs.getTimestamp("terminate_at")));
        snapshot.setType(rs.getString("type"));
        snapshot.setStatus(rs.getString("status"));
        snapshot.setWasInCountDown(rs.getBoolean("was_in_countdown"));
        snapshot.setMinimumBidIncrement(rs.getFloat("minimum_bid_increment"));
        
        // Item sẽ được load từ ItemDAO sau (không inline)
        snapshot.setItem(null);

        return snapshot;
    }

    /**
     * Tải danh sách bid từ bảng {@code auction_bids} cho một phiên đấu giá.
     * Dữ liệu được sắp xếp theo {@code bid_order ASC} để khôi phục đúng
     * thứ tự LinkedList (giá cao nhất ở đầu).
     *
     * @param auctionId ID phiên đấu giá (int)
     * @return LinkedList các Bid đã sắp xếp
     */
    private LinkedList<Bid> loadBids(int auctionId) {
        String sql = "SELECT * FROM auction_bids WHERE auction_id = ? ORDER BY bid_order ASC";
        LinkedList<Bid> bidList = new LinkedList<>();

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    float amount = rs.getFloat("bid_amount");
                    String username = rs.getString("bidder_username");
                    Date createdAt = toDate(rs.getTimestamp("created_at"));
                    bidList.add(new Bid(createdAt, amount, username));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("[AuctionDAO] Lỗi khi tải danh sách bid cho phiên: " + auctionId, e);
        }
        return bidList;
    }

    /**
     * Tải danh sách username người tham gia từ bảng {@code auction_participants}.
     *
     * @param auctionId ID phiên đấu giá (int)
     * @return danh sách username
     */
    private List<String> loadParticipants(int auctionId) {
        String sql = "SELECT username FROM auction_participants WHERE auction_id = ?";
        List<String> usernames = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    usernames.add(rs.getString("username"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("[AuctionDAO] Lỗi khi tải danh sách người tham gia: " + auctionId, e);
        }
        return usernames;
    }

    // ========================== Phương thức Private — Tiện ích
    // ==========================

    private void addColumnIfMissing(Connection conn, String tableName, String columnName, String definition)
            throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getColumns(null, null, tableName, columnName)) {
            if (rs.next()) {
                return;
            }
        }
        try (Statement alterStmt = conn.createStatement()) {
            alterStmt.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
        }
    }

    /**
     * Chuyển đổi chuỗi auctionId sang int.
     *
     * @param auctionId ID dạng chuỗi
     * @return giá trị int
     * @throws IllegalArgumentException nếu chuỗi không phải số hợp lệ
     */
    private int parseAuctionId(String auctionId) {
        try {
            return Integer.parseInt(auctionId.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Auction ID phải là số nguyên hợp lệ: " + auctionId, e);
        }
    }

    /**
     * Chuyển đổi {@link java.util.Date} sang {@link java.sql.Timestamp}.
     *
     * @param date đối tượng Date (có thể null)
     * @return Timestamp tương ứng, hoặc {@code null} nếu đầu vào null
     */
    private Timestamp toTimestamp(Date date) {
        return (date != null) ? new Timestamp(date.getTime()) : null;
    }

    /**
     * Chuyển đổi {@link java.sql.Timestamp} sang {@link java.util.Date}.
     *
     * @param timestamp đối tượng Timestamp (có thể null)
     * @return Date tương ứng, hoặc {@code null} nếu đầu vào null
     */
    private Date toDate(Timestamp timestamp) {
        return (timestamp != null) ? new Date(timestamp.getTime()) : null;
    }

    /**
     * Lấy item_id từ một đối tượng Item bằng cách tìm kiếm trong ItemDAO.
     * 
     * LƯUÝ: Phương thức này tìm kiếm item có cùng name, price, description
     * từ ItemDAO. Nếu không tìm thấy hoặc có nhều item giống nhau, 
     * nên truyền trực tiếp item_id vào AuctionSnapshot.
     *
     * @param item đối tượng Item
     * @return item_id nếu tìm thấy, hoặc null
     */
    private String getItemIdFromItem(Item item) {
        if (item == null) return null;
        if (item.getId() != null && !item.getId().trim().isEmpty()) {
            return item.getId();
        }
        
        // Tìm item có cùng thuộc tính
        Map<String, Item> allItems = ItemDAO.getInstance().findAllAsMap();
        for (Map.Entry<String, Item> entry : allItems.entrySet()) {
            Item dbItem = entry.getValue();
            if (dbItem.getName().equals(item.getName())
                    && dbItem.getStartingPrice() == item.getStartingPrice()
                    && dbItem.getDescription().equals(item.getDescription())
                    && dbItem.getClass().equals(item.getClass())) {
                return entry.getKey();
            }
        }
        
        return null;
    }

    /**
     * Xác định chuỗi kiểu item từ đối tượng {@link Item}.
     *
     * @param item đối tượng Item cần xác định kiểu
     * @return chuỗi kiểu: "ELECTRONICS", "ART", hoặc "VEHICLE"
     */
    private String getItemType(Item item) {
        if (item instanceof Electronics)
            return "ELECTRONICS";
        if (item instanceof Art)
            return "ART";
        if (item instanceof Vehicle)
            return "VEHICLE";
        if (item instanceof RealEstate)
            return "REAL_ESTATE";
        if (item instanceof Fashion)
            return "FASHION";
        if (item instanceof Collectibles)
            return "COLLECTIBLES";
        return item.getClass().getSimpleName().toUpperCase();
    }

    private void appendDashboardFilters(StringBuilder sql, List<Object> params,
                                        String category, boolean endingSoon,
                                        Float minPriceInclusive, Float maxPriceInclusive) {
        if (category != null && !"ALL".equalsIgnoreCase(category)) {
            sql.append(" AND UPPER(i.item_type) = ?");
            params.add(category.toUpperCase());
        }
        if (endingSoon) {
            sql.append(" AND s.terminate_at IS NOT NULL AND s.terminate_at BETWEEN NOW() AND DATE_ADD(NOW(), INTERVAL 3 DAY)");
        }
        if (minPriceInclusive != null) {
            sql.append(" AND COALESCE(b.max_bid, i.current_highest_price, i.starting_price) >= ?");
            params.add(minPriceInclusive);
        }
        if (maxPriceInclusive != null) {
            sql.append(" AND COALESCE(b.max_bid, i.current_highest_price, i.starting_price) <= ?");
            params.add(maxPriceInclusive);
        }
    }

    private void bindParams(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    private Item createItemFromDashboardRow(ResultSet rs) throws SQLException {
        String type = rs.getString("item_type");
        float startingPrice = rs.getFloat("starting_price");
        String name = rs.getString("name");
        String description = rs.getString("description");
        Item item;
        switch (type.toUpperCase()) {
            case "ELECTRONICS":
                item = new Electronics(startingPrice, name, description);
                break;
            case "ART":
                item = new Art(startingPrice, name, description);
                break;
            case "VEHICLE":
                item = new Vehicle(startingPrice, name, description);
                break;
            case "REAL_ESTATE":
                item = new RealEstate(startingPrice, name, description);
                break;
            case "FASHION":
                item = new Fashion(startingPrice, name, description);
                break;
            case "COLLECTIBLES":
                item = new Collectibles(startingPrice, name, description);
                break;
            default:
                throw new RuntimeException("Loại sản phẩm không xác định trong database: " + type);
        }
        item.setId(rs.getString("item_id"));
        item.setCurrentHighestPrice(rs.getFloat("current_price"));
        item.setAuctionStartTime(toDate(rs.getTimestamp("auction_start_time")));
        item.setAuctionEndTime(toDate(rs.getTimestamp("auction_end_time")));
        item.setItemCondition(rs.getString("item_condition"));
        item.setLocation(rs.getString("location"));
        return item;
    }

    /**
     * Rollback connection một cách an toàn, bỏ qua ngoại lệ.
     */
    private void rollbackQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.rollback();
            } catch (SQLException e) {
                System.err.println("[AuctionDAO] Lỗi khi rollback: " + e.getMessage());
            }
        }
    }

    /**
     * Đóng connection một cách an toàn: khôi phục autoCommit và đóng.
     */
    private void closeQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.setAutoCommit(true);
                conn.close();
            } catch (SQLException e) {
                System.err.println("[AuctionDAO] Lỗi khi đóng connection: " + e.getMessage());
            }
        }
    }

    /**
     * Lấy ID tiếp theo khả dụng cho phiên đấu giá mới.
     *
     * @return ID đấu giá tiếp theo
     */
    public int getNextAuctionId() {
        String sql = "SELECT COALESCE(MAX(auction_id), 0) + 1 FROM auction_snapshots";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("[AuctionDAO] Lỗi khi lấy ID đấu giá tiếp theo: " + e.getMessage());
        }
        return (int) (System.currentTimeMillis() / 1000); // fallback
    }
}
