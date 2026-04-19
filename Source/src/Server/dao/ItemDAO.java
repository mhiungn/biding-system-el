package Server.dao;

import CommonClasses.Items.*;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Lớp DAO quản lý việc lưu trữ dữ liệu {@link Item} (sản phẩm đấu giá).
 * <p>
 * DAO này xử lý các thao tác CRUD cho sản phẩm (Electronics, Art, Vehicle, v.v.).
 * Vì lớp {@link Item} không có trường ID sẵn, DAO này tự tạo khóa UUID cho mỗi
 * item và duy trì một bảng ánh xạ riêng để theo dõi seller nào sở hữu item nào.
 * </p>
 *
 * <h3>Singleton Pattern:</h3>
 * Triển khai Singleton an toàn đa luồng bằng double-checked locking.
 *
 * <h3>File dữ liệu:</h3>
 * <ul>
 *   <li>{@code items.dat} — dữ liệu item chính (Map&lt;String, Item&gt;)</li>
 *   <li>{@code item_owners.dat} — ánh xạ quyền sở hữu (Map&lt;String, String&gt;: itemId → sellerUsername)</li>
 * </ul>
 *
 * <h3>An toàn đa luồng:</h3>
 * Tất cả phương thức public được bảo vệ bởi {@link ReentrantReadWriteLock}.
 *
 * <h3>Ví dụ sử dụng:</h3>
 * <pre>{@code
 *   ItemDAO itemDAO = ItemDAO.getInstance();
 *
 *   Item laptop = TypeItem.createItem("ELECTRONICS", 500f, "Gaming Laptop", "RTX 4090");
 *   String itemId = itemDAO.saveItem(laptop, "seller_john");
 *
 *   Item found = itemDAO.findById(itemId);
 *   Map<String, Item> johnItems = itemDAO.findBySeller("seller_john");
 * }</pre>
 *
 * @see Item
 * @see GenericDAO
 * @see DataStore
 */
public class ItemDAO implements GenericDAO<String, Item> {

    // ========================== Hằng số ==========================

    /** Tên file lưu trữ các đối tượng Item. */
    private static final String ITEMS_FILE = "items.dat";

    /** Tên file lưu trữ ánh xạ item → seller. */
    private static final String OWNERS_FILE = "item_owners.dat";

    // ========================== Singleton ==========================

    private static volatile ItemDAO instance;

    /**
     * Trả về instance Singleton của {@code ItemDAO}.
     *
     * @return instance Singleton
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

    // ========================== Thuộc tính ==========================

    /** Kho lưu trữ file cho dữ liệu Item. */
    private final DataStore itemStore;

    /** Kho lưu trữ file cho ánh xạ quyền sở hữu. */
    private final DataStore ownerStore;

    /** Cache trong bộ nhớ: itemId → Item. */
    private HashMap<String, Item> items;

    /** Ánh xạ quyền sở hữu trong bộ nhớ: itemId → sellerUsername. */
    private HashMap<String, String> itemOwners;

    /** Khóa đọc-ghi cho truy cập an toàn đa luồng. */
    private final ReentrantReadWriteLock lock;

    // ========================== Constructor ==========================

    /**
     * Constructor private — sử dụng {@link #getInstance()}.
     * Tải dữ liệu từ ổ đĩa khi khởi tạo.
     */
    private ItemDAO() {
        this.itemStore = new DataStore(ITEMS_FILE);
        this.ownerStore = new DataStore(OWNERS_FILE);
        this.lock = new ReentrantReadWriteLock();
        this.items = itemStore.readData();
        this.itemOwners = ownerStore.readData();
        System.out.println("[ItemDAO] Đã khởi tạo. Tải " + items.size() + " sản phẩm từ ổ đĩa.");
    }

    // ========================== Triển khai GenericDAO ==========================

    /**
     * Lưu một item với ID đã cho sẵn.
     * <p>
     * Để lưu item <b>kèm theo dõi quyền sở hữu seller</b>, sử dụng
     * {@link #saveItem(Item, String)} — tự động sinh ID.
     * </p>
     *
     * @param itemId ID duy nhất của item
     * @param item   đối tượng Item cần lưu
     */
    @Override
    public void save(String itemId, Item item) {
        if (itemId == null || itemId.trim().isEmpty()) {
            throw new IllegalArgumentException("ID sản phẩm không được để trống hoặc null");
        }
        if (item == null) {
            throw new IllegalArgumentException("Sản phẩm không được null");
        }

        lock.writeLock().lock();
        try {
            items.put(itemId, item);
            persistData();
            System.out.println("[ItemDAO] Đã lưu sản phẩm: " + itemId + " (" + item.getName() + ")");
        } finally {
            lock.writeLock().unlock();
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
        lock.readLock().lock();
        try {
            return items.get(itemId);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Trả về tất cả sản phẩm đã lưu.
     *
     * @return danh sách mới chứa tất cả Item
     */
    @Override
    public List<Item> findAll() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(items.values());
        } finally {
            lock.readLock().unlock();
        }
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
        lock.writeLock().lock();
        try {
            if (!items.containsKey(itemId)) {
                return false;
            }
            items.put(itemId, item);
            persistData();
            System.out.println("[ItemDAO] Đã cập nhật sản phẩm: " + itemId + " (" + item.getName() + ")");
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Xóa một sản phẩm và ánh xạ quyền sở hữu của nó.
     *
     * @param itemId ID của sản phẩm cần xóa
     * @return {@code true} nếu tìm thấy và xóa thành công
     */
    @Override
    public boolean delete(String itemId) {
        lock.writeLock().lock();
        try {
            Item removed = items.remove(itemId);
            itemOwners.remove(itemId);
            if (removed != null) {
                persistData();
                System.out.println("[ItemDAO] Đã xóa sản phẩm: " + itemId + " (" + removed.getName() + ")");
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
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
        lock.readLock().lock();
        try {
            return items.containsKey(itemId);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Trả về tổng số sản phẩm đã lưu.
     *
     * @return số lượng sản phẩm
     */
    @Override
    public int count() {
        lock.readLock().lock();
        try {
            return items.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void flush() {
        lock.writeLock().lock();
        try {
            persistData();
            System.out.println("[ItemDAO] Đã ghi " + items.size() + " sản phẩm xuống ổ đĩa.");
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void reload() {
        lock.writeLock().lock();
        try {
            this.items = itemStore.readData();
            this.itemOwners = ownerStore.readData();
            System.out.println("[ItemDAO] Đã tải lại " + items.size() + " sản phẩm từ ổ đĩa.");
        } finally {
            lock.writeLock().unlock();
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

        lock.writeLock().lock();
        try {
            String itemId = UUID.randomUUID().toString();
            items.put(itemId, item);
            itemOwners.put(itemId, sellerUsername);
            persistData();
            System.out.println("[ItemDAO] Đã lưu sản phẩm: " + itemId
                    + " (" + item.getName() + ") thuộc sở hữu của " + sellerUsername);
            return itemId;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Tìm tất cả sản phẩm thuộc sở hữu của một seller cụ thể.
     *
     * @param sellerUsername username của seller
     * @return map itemId → Item cho tất cả sản phẩm của seller này
     */
    public Map<String, Item> findBySeller(String sellerUsername) {
        lock.readLock().lock();
        try {
            Map<String, Item> result = new HashMap<>();
            for (Map.Entry<String, String> entry : itemOwners.entrySet()) {
                if (entry.getValue().equals(sellerUsername)) {
                    Item item = items.get(entry.getKey());
                    if (item != null) {
                        result.put(entry.getKey(), item);
                    }
                }
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Trả về username của seller sở hữu sản phẩm.
     *
     * @param itemId ID sản phẩm cần tra cứu
     * @return username của seller, hoặc {@code null} nếu không tìm thấy
     */
    public String getItemOwner(String itemId) {
        lock.readLock().lock();
        try {
            return itemOwners.get(itemId);
        } finally {
            lock.readLock().unlock();
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
        lock.readLock().lock();
        try {
            String owner = itemOwners.get(itemId);
            return owner != null && owner.equals(sellerUsername);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Tìm sản phẩm theo tên (không phân biệt chữ hoa/thường, tìm kiếm gần đúng).
     *
     * @param namePart tên hoặc một phần tên cần tìm
     * @return map itemId → Item cho tất cả sản phẩm khớp
     */
    public Map<String, Item> findByName(String namePart) {
        lock.readLock().lock();
        try {
            Map<String, Item> result = new HashMap<>();
            String searchLower = namePart.toLowerCase();
            for (Map.Entry<String, Item> entry : items.entrySet()) {
                if (entry.getValue().getName().toLowerCase().contains(searchLower)) {
                    result.put(entry.getKey(), entry.getValue());
                }
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Trả về tất cả sản phẩm dưới dạng Map (itemId → Item).
     *
     * @return map mới chứa tất cả sản phẩm
     */
    public Map<String, Item> findAllAsMap() {
        lock.readLock().lock();
        try {
            return new HashMap<>(items);
        } finally {
            lock.readLock().unlock();
        }
    }

    // ========================== Phương thức Private ==========================

    /**
     * Ghi cả map item và map quyền sở hữu xuống ổ đĩa.
     * Phải được gọi khi đang giữ write lock.
     */
    private void persistData() {
        itemStore.writeData(items);
        ownerStore.writeData(itemOwners);
    }
}
