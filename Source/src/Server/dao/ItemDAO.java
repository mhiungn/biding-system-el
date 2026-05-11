package Server.dao;

import CommonClasses.Items.*;

import java.sql.*;
import java.util.*;
import java.util.Date;

/**
 * Lớp DAO quản lý việc lưu trữ dữ liệu {@link Item} (sản phẩm đấu giá) trên MySQL.
 * <p>
 * DAO này xử lý các thao tác CRUD cho sản phẩm (Electronics, Art, Vehicle, v.v.).
 * Mỗi item được lưu trong bảng {@code items} với khóa chính là UUID tự sinh,
 * kèm theo cột {@code seller_username} để theo dõi quyền sở hữu.
 * </p>
 *
 * <h3>Singleton Pattern:</h3>
 * Triển khai Singleton an toàn đa luồng bằng double-checked locking.
 *
 * <h3>Cấu trúc bảng:</h3>
 * <pre>
 *   items (
 *       item_id         VARCHAR(36) PRIMARY KEY,
 *       name            VARCHAR(255),
 *       starting_price  FLOAT,
 *       description     TEXT,
 *       item_type       VARCHAR(50),       -- ELECTRONICS / ART / VEHICLE
 *       seller_username VARCHAR(50)        -- username của seller sở hữu
 *   )
 * </pre>
 *
 * <h3>Mapping kiểu Item:</h3>
 * Vì các lớp con {@link Electronics}, {@link Art}, {@link Vehicle} không có
 * thuộc tính riêng (chỉ khác nhau ở {@code getDisplayInfo()}), cột {@code item_type}
 * được dùng để xác định class nào cần tạo khi đọc từ database.
 *
 * <h3>Ví dụ sử dụng:</h3>
 * <pre>{@code
 *   ItemDAO itemDAO = ItemDAO.getInstance();
 *
 *   Item laptop = ItemFactory.createItem("ELECTRONICS", 500f, "Gaming Laptop", "RTX 4090");
 *   String itemId = itemDAO.saveItem(laptop, "seller_john");
 *
 *   Item found = itemDAO.findById(itemId);
 *   Map<String, Item> johnItems = itemDAO.findBySeller("seller_john");
 * }</pre>
 *
 * @see Item
 * @see GenericDAO
 * @see DatabaseConnection
 */
public class ItemDAO implements GenericDAO<String, Item> {

    // ========================== Singleton ==========================

    /** Instance duy nhất của ItemDAO. */
    private static volatile ItemDAO instance;

    /**
     * Trả về instance Singleton của {@code ItemDAO}.
     * Sử dụng double-checked locking để khởi tạo lazy an toàn đa luồng.
     *
     * @return instance Singleton của {@code ItemDAO}
     */
    public static ItemDAO getInstance() {
        if (instance == null) {
            synchronized (ItemDAO.class) {
                if (instance == null) {
                    instance = new ItemDAO();
                }
            }
        }
        return instance;
    }

    // ========================== Constructor ==========================

    /**
     * Constructor private — sử dụng {@link #getInstance()}.
     * Tự động tạo bảng {@code items} nếu chưa tồn tại.
     */
    private ItemDAO() {
        createTableIfNotExists();
        System.out.println("[ItemDAO] Initialized with MySQL. Currently, there are " + count() + " products.");
    }

    // ========================== Tạo bảng ==========================

    /**
     * Tạo bảng {@code items} trong MySQL nếu chưa tồn tại.
     */
    private void createTableIfNotExists() {
        String sql = "CREATE TABLE IF NOT EXISTS items ("
                + "item_id         VARCHAR(36)    PRIMARY KEY, "
                + "name            VARCHAR(255)   NOT NULL, "
                + "starting_price  FLOAT          NOT NULL, "
                + "current_highest_price FLOAT    NOT NULL, "
                + "item_type       VARCHAR(50)    NOT NULL, "
                + "description     TEXT, "
                + "auction_start_time DATETIME    NULL, "
                + "auction_end_time DATETIME      NULL, "
                + "seller_username VARCHAR(50)"
                + ")";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("[ItemDAO] Không thể tạo bảng items", e);
        }
    }

    // ========================== Triển khai GenericDAO ==========================

    /**
     * Lưu một item với ID đã cho sẵn (không ghi nhận seller).
     * <p>
     * Để lưu item <b>kèm theo dõi quyền sở hữu seller</b>, sử dụng
     * {@link #saveItem(Item, String)} — tự động sinh ID.
     * </p>
     * @param itemId ID duy nhất của item
     * @param item   đối tượng Item cần lưu
     * @throws IllegalArgumentException nếu itemId rỗng/null hoặc item là null
     */
    @Override
    public void save(String itemId, Item item) {
        if (itemId == null || itemId.trim().isEmpty()) {
            throw new IllegalArgumentException("ID san pham khong duoc de trong va null");
        }
        if (item == null) {
            throw new IllegalArgumentException("San pham khong duoc NULL");
        }

        String sql = "INSERT INTO items (item_id, name, starting_price, current_highest_price, item_type, description, auction_start_time, auction_end_time) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, itemId);
            ps.setString(2, item.getName());
            ps.setFloat(3, item.getStartingPrice());
            ps.setFloat(4, item.getCurrentHighestPrice());
            ps.setString(5, getItemType(item));
            ps.setString(6, item.getDescription());
            ps.setTimestamp(7, toTimestamp(item.getAuctionStartTime()));
            ps.setTimestamp(8, toTimestamp(item.getAuctionEndTime()));
            ps.executeUpdate();
            System.out.println("[ItemDAO] Da luu san pham: " + itemId + " (" + item.getName() + ")");
        } catch (SQLException e) {
            throw new RuntimeException("[ItemDAO] Loi khi luu san pham: " + itemId, e);
        }
    }

    /**
     * Tìm sản phẩm theo ID.
     *
     * @param itemId ID sản phẩm cần tìm
     * @return {@link Item} nếu tìm thấy, hoặc {@code null} nếu không tồn tại
     */
    @Override
    public Item findById(String itemId) {
        String sql = "SELECT * FROM items WHERE item_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToItem(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("[ItemDAO] Loi khi tim san pham: " + itemId, e);
        }
    }

    /**
     * Trả về tất cả sản phẩm đã lưu.
     * @return danh sách tất cả Item; trả về danh sách rỗng nếu không có
     */
    @Override
    public List<Item> findAll() {
        String sql = "SELECT * FROM items";
        List<Item> result = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapResultSetToItem(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("[ItemDAO] Loi khi lay tat ca san pham", e);
        }
        return result;
    }

    /**
     * Cập nhật thông tin một sản phẩm đã tồn tại.
     *
     * @param itemId ID của sản phẩm cần cập nhật
     * @param item   dữ liệu Item mới
     * @return {@code true} nếu tìm thấy và cập nhật thành công
     */
    @Override
    public boolean update(String itemId, Item item) {
        String sql = "UPDATE items SET name = ?, starting_price = ?, current_highest_price = ?, description = ?, item_type = ?, auction_start_time = ?, auction_end_time = ? "
                + "WHERE item_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, item.getName());
            ps.setFloat(2, item.getStartingPrice());
            ps.setFloat(3, item.getCurrentHighestPrice());
            ps.setString(4, item.getDescription());
            ps.setString(5, getItemType(item));
            ps.setTimestamp(6, toTimestamp(item.getAuctionStartTime()));
            ps.setTimestamp(7, toTimestamp(item.getAuctionEndTime()));
            ps.setString(8, itemId);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                System.out.println("[ItemDAO] Update sp: " + itemId + " (" + item.getName() + ")");
                return true;
            }
            return false;
        } catch (SQLException e) {
            throw new RuntimeException("[ItemDAO] Error update sp: " + itemId, e);
        }
    }

    /**
     * Xóa một sản phẩm theo ID.
     *
     * @param itemId ID của sản phẩm cần xóa
     * @return {@code true} nếu tìm thấy và xóa thành công
     */
    @Override
    public boolean delete(String itemId) {
        String sql = "DELETE FROM items WHERE item_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, itemId);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                System.out.println("[ItemDAO] Đã xóa sản phẩm: " + itemId);
                return true;
            }
            return false;
        } catch (SQLException e) {
            throw new RuntimeException("[ItemDAO] Lỗi khi xóa sản phẩm: " + itemId, e);
        }
    }

    /**
     * Kiểm tra sản phẩm với ID cho trước có tồn tại hay không.
     *
     * @param itemId ID sản phẩm cần kiểm tra
     * @return {@code true} nếu tồn tại
     */
    @Override
    public boolean exists(String itemId) {
        String sql = "SELECT COUNT(*) FROM items WHERE item_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
                return false;
            }
        } catch (SQLException e) {
            throw new RuntimeException("[ItemDAO] Lỗi khi kiểm tra tồn tại: " + itemId, e);
        }
    }

    /**
     * Trả về tổng số sản phẩm đã lưu.
     *
     * @return số lượng sản phẩm
     */
    @Override
    public int count() {
        String sql = "SELECT COUNT(*) FROM items";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        } catch (SQLException e) {
            throw new RuntimeException("[ItemDAO] Lỗi khi đếm sản phẩm", e);
        }
    }

    // ========================== Phương thức riêng cho Item ==========================

    /**
     * Lưu sản phẩm mới với UUID tự sinh và ghi nhận quyền sở hữu của seller.
     * <p>
     * Đây là phương thức ưu tiên khi tạo sản phẩm mới. Nó tự sinh ID duy nhất,
     * lưu item, và ghi nhận seller nào sở hữu item đó.
     * </p>
     *
     * @param item           sản phẩm cần lưu
     * @param sellerUsername  username của seller sở hữu sản phẩm này
     * @return ID sản phẩm được tự sinh (chuỗi UUID)
     * @throws IllegalArgumentException nếu item hoặc sellerUsername là null
     */
    public String saveItem(Item item, String sellerUsername) {
        if (item == null) {
            throw new IllegalArgumentException("Sản phẩm không được null");
        }
        if (sellerUsername == null || sellerUsername.trim().isEmpty()) {
            throw new IllegalArgumentException("Username của seller không được để trống hoặc null");
        }

        String itemId = UUID.randomUUID().toString();
        String sql = "INSERT INTO items (item_id, name, starting_price, current_highest_price, description, item_type, auction_start_time, auction_end_time, seller_username) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, itemId);
            ps.setString(2, item.getName());
            ps.setFloat(3, item.getStartingPrice());
            ps.setFloat(4, item.getCurrentHighestPrice());
            ps.setString(5, item.getDescription());
            ps.setString(6, getItemType(item));
            ps.setTimestamp(7, toTimestamp(item.getAuctionStartTime()));
            ps.setTimestamp(8, toTimestamp(item.getAuctionEndTime()));
            ps.setString(9, sellerUsername);
            ps.executeUpdate();
            System.out.println("[ItemDAO] Đã lưu sản phẩm: " + itemId
                    + " (" + item.getName() + ") thuộc sở hữu của " + sellerUsername);
            return itemId;
        } catch (SQLException e) {
            throw new RuntimeException("[ItemDAO] Lỗi khi lưu sản phẩm với seller", e);
        }
    }

    /**
     * Tìm tất cả sản phẩm thuộc sở hữu của một seller cụ thể.
     *
     * @param sellerUsername username của seller
     * @return map itemId → Item cho tất cả sản phẩm của seller này
     */
    public Map<String, Item> findBySeller(String sellerUsername) {
        String sql = "SELECT * FROM items WHERE seller_username = ?";
        Map<String, Item> result = new HashMap<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sellerUsername);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String itemId = rs.getString("item_id");
                    Item item = mapResultSetToItem(rs);
                    result.put(itemId, item);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("[ItemDAO] Lỗi khi tìm sản phẩm theo seller: " + sellerUsername, e);
        }
        return result;
    }

    /**
     * Trả về username của seller sở hữu sản phẩm.
     *
     * @param itemId ID sản phẩm cần tra cứu
     * @return username của seller, hoặc {@code null} nếu không tìm thấy
     */
    public String getItemOwner(String itemId) {
        String sql = "SELECT seller_username FROM items WHERE item_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("seller_username");
                }
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("[ItemDAO] Lỗi khi tìm chủ sở hữu: " + itemId, e);
        }
    }

    /**
     * Kiểm tra một seller cụ thể có sở hữu sản phẩm hay không.
     *
     * @param itemId        ID sản phẩm
     * @param sellerUsername username của seller
     * @return {@code true} nếu seller sở hữu sản phẩm này
     */
    public boolean isOwner(String itemId, String sellerUsername) {
        String sql = "SELECT COUNT(*) FROM items WHERE item_id = ? AND seller_username = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, itemId);
            ps.setString(2, sellerUsername);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
                return false;
            }
        } catch (SQLException e) {
            throw new RuntimeException("[ItemDAO] Lỗi khi kiểm tra quyền sở hữu", e);
        }
    }

    /**
     * Tìm sản phẩm theo tên (không phân biệt chữ hoa/thường, tìm kiếm gần đúng).
     *
     * @param namePart tên hoặc một phần tên cần tìm
     * @return map itemId → Item cho tất cả sản phẩm khớp
     */
    public Map<String, Item> findByName(String namePart) {
        String sql = "SELECT * FROM items WHERE LOWER(name) LIKE ?";
        Map<String, Item> result = new HashMap<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "%" + namePart.toLowerCase() + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String itemId = rs.getString("item_id");
                    Item item = mapResultSetToItem(rs);
                    result.put(itemId, item);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("[ItemDAO] Lỗi khi tìm sản phẩm theo tên: " + namePart, e);
        }
        return result;
    }

    /**
     * Trả về tất cả sản phẩm dưới dạng Map (itemId → Item).
     *
     * @return map mới chứa tất cả sản phẩm
     */
    public Map<String, Item> findAllAsMap() {
        String sql = "SELECT * FROM items";
        Map<String, Item> result = new HashMap<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String itemId = rs.getString("item_id");
                Item item = mapResultSetToItem(rs);
                result.put(itemId, item);
            }
        } catch (SQLException e) {
            throw new RuntimeException("[ItemDAO] Lỗi khi lấy tất cả sản phẩm dạng Map", e);
        }
        return result;
    }

    // ========================== Phương thức Private ==========================

    /**
     * Chuyển đổi một dòng {@link ResultSet} thành đối tượng {@link Item} đúng kiểu.
     * <p>
     * Dựa vào cột {@code item_type} để xác định tạo {@link Electronics},
     * {@link Art}, hay {@link Vehicle}.
     * </p>
     *
     * @param rs ResultSet đang trỏ tới dòng cần đọc
     * @return đối tượng Item đúng kiểu
     * @throws SQLException nếu lỗi đọc dữ liệu
     */
    private Item mapResultSetToItem(ResultSet rs) throws SQLException {
        String type = rs.getString("item_type");
        float price = rs.getFloat("starting_price");
        float currentHighestPrice = rs.getFloat("current_highest_price");
        if (rs.wasNull()) {
            currentHighestPrice = price;
        }
        String name = rs.getString("name");
        String desc = rs.getString("description");
        Date auctionStartTime = toDate(rs.getTimestamp("auction_start_time"));
        Date auctionEndTime = toDate(rs.getTimestamp("auction_end_time"));

        Item item;
        switch (type.toUpperCase()) {
            case "ELECTRONICS":
                item = new Electronics(price, name, desc);
                break;
            case "ART":
                item = new Art(price, name, desc);
                break;
            case "VEHICLE":
                item = new Vehicle(price, name, desc);
                break;
            default:
                throw new RuntimeException("Loại sản phẩm không xác định trong database: " + type);
        }
        item.setCurrentHighestPrice(currentHighestPrice);
        item.setAuctionStartTime(auctionStartTime);
        item.setAuctionEndTime(auctionEndTime);
        return item;
    }

    /**
     * Xác định chuỗi kiểu item từ đối tượng {@link Item} để lưu vào cột {@code item_type}.
     *
     * @param item đối tượng Item cần xác định kiểu
     * @return chuỗi kiểu: "ELECTRONICS", "ART", hoặc "VEHICLE"
     */
    private String getItemType(Item item) {
        if (item instanceof Electronics) return "ELECTRONICS";
        if (item instanceof Art) return "ART";
        if (item instanceof Vehicle) return "VEHICLE";
        return item.getClass().getSimpleName().toUpperCase();
    }

    private Timestamp toTimestamp(Date date) {
        return date == null ? null : new Timestamp(date.getTime());
    }

    private Date toDate(Timestamp timestamp) {
        return timestamp == null ? null : new Date(timestamp.getTime());
    }
}
