package Server.dao;

import CommonClasses.Items.*;

import org.junit.jupiter.api.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests cho {@link ItemDAO}.
 * <p>
 * Sử dụng H2 in-memory database (MySQL compatibility mode) thay cho MySQL thật.
 * </p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ItemDAOTest {

    private static ItemDAO itemDAO;

    @BeforeAll
    static void setUpAll() throws Exception {
        TestDatabaseHelper.redirectToH2();
        TestDatabaseHelper.createAllTables();

        TestDatabaseHelper.resetSingleton(ItemDAO.class);
        itemDAO = ItemDAO.getInstance();
    }

    @AfterAll
    static void tearDownAll() throws Exception {
        TestDatabaseHelper.dropAllTables();
        TestDatabaseHelper.resetSingleton(ItemDAO.class);
    }

    @BeforeEach
    void clearData() throws SQLException {
        TestDatabaseHelper.clearAllTables();
    }

    // ========================== Test save() ==========================

    @Test
    @Order(1)
    @DisplayName("save() - Lưu Electronics thành công")
    void testSaveElectronics() {
        Electronics laptop = new Electronics(1500f, "Gaming Laptop", "RTX 4090");
        itemDAO.save("item-001", laptop);

        Item found = itemDAO.findById("item-001");
        assertNotNull(found, "Item phải được tìm thấy sau khi save");
        assertEquals("Gaming Laptop", found.getName());
        assertEquals(1500f, found.getStartingPrice(), 0.01f);
        assertEquals("RTX 4090", found.getDescription());
        assertInstanceOf(Electronics.class, found, "Item phải được map đúng kiểu Electronics");
    }

    @Test
    @Order(2)
    @DisplayName("save() - Lưu Art thành công")
    void testSaveArt() {
        Art painting = new Art(500f, "Tranh cổ", "Sơn dầu thế kỷ 19");
        itemDAO.save("item-002", painting);

        Item found = itemDAO.findById("item-002");
        assertNotNull(found);
        assertInstanceOf(Art.class, found, "Item phải được map đúng kiểu Art");
    }

    @Test
    @Order(3)
    @DisplayName("save() - Lưu Vehicle thành công")
    void testSaveVehicle() {
        Vehicle car = new Vehicle(30000f, "Toyota Camry", "Xe ô tô sedan");
        itemDAO.save("item-003", car);

        Item found = itemDAO.findById("item-003");
        assertNotNull(found);
        assertInstanceOf(Vehicle.class, found, "Item phải được map đúng kiểu Vehicle");
    }

    @Test
    @Order(4)
    @DisplayName("save() - Lưu Real Estate thành công")
    void testSaveRealEstate() {
        RealEstate house = new RealEstate(500000f, "Penthouse Quận 1", "Căn hộ cao cấp 3 phòng ngủ");
        itemDAO.save("item-004", house);

        Item found = itemDAO.findById("item-004");
        assertNotNull(found);
        assertInstanceOf(RealEstate.class, found, "Item phải được map đúng kiểu RealEstate");
    }

    @Test
    @Order(5)
    @DisplayName("save() - Lưu Fashion thành công")
    void testSaveFashion() {
        Fashion dress = new Fashion(1200f, "Gucci Dress", "Váy dạ hội bộ sưu tập mới");
        itemDAO.save("item-005", dress);

        Item found = itemDAO.findById("item-005");
        assertNotNull(found);
        assertInstanceOf(Fashion.class, found, "Item phải được map đúng kiểu Fashion");
    }

    @Test
    @Order(6)
    @DisplayName("save() - Lưu Collectibles thành công")
    void testSaveCollectibles() {
        Collectibles coin = new Collectibles(2500f, "Đồng xu cổ", "Đồng xu vàng thế kỷ 18");
        itemDAO.save("item-006", coin);

        Item found = itemDAO.findById("item-006");
        assertNotNull(found);
        assertInstanceOf(Collectibles.class, found, "Item phải được map đúng kiểu Collectibles");
    }

    @Test
    @Order(7)
    @DisplayName("save() - Từ chối khi itemId null")
    void testSaveNullId() {
        Electronics item = new Electronics(100f, "Test", "Desc");
        assertThrows(IllegalArgumentException.class,
                () -> itemDAO.save(null, item));
    }

    @Test
    @Order(8)
    @DisplayName("save() - Từ chối khi itemId rỗng")
    void testSaveEmptyId() {
        Electronics item = new Electronics(100f, "Test", "Desc");
        assertThrows(IllegalArgumentException.class,
                () -> itemDAO.save("  ", item));
    }

    @Test
    @Order(9)
    @DisplayName("save() - Từ chối khi item null")
    void testSaveNullItem() {
        assertThrows(IllegalArgumentException.class,
                () -> itemDAO.save("item-999", null));
    }

    // ========================== Test findById() ==========================

    @Test
    @Order(10)
    @DisplayName("findById() - Trả về null khi không tìm thấy")
    void testFindByIdNotFound() {
        assertNull(itemDAO.findById("nonexistent"));
    }

    @Test
    @Order(11)
    @DisplayName("findById() - Tìm thấy item đã lưu với dữ liệu đúng")
    void testFindByIdCorrectData() {
        itemDAO.save("item-001", new Electronics(1500f, "Laptop", "Gaming laptop"));

        Item found = itemDAO.findById("item-001");
        assertNotNull(found);
        assertEquals("Laptop", found.getName());
        assertEquals(1500f, found.getStartingPrice(), 0.01f);
        assertEquals("Gaming laptop", found.getDescription());
    }

    // ========================== Test findAll() ==========================

    @Test
    @Order(20)
    @DisplayName("findAll() - Trả về danh sách rỗng khi chưa có item")
    void testFindAllEmpty() {
        List<Item> items = itemDAO.findAll();
        assertNotNull(items);
        assertTrue(items.isEmpty());
    }

    @Test
    @Order(21)
    @DisplayName("findAll() - Trả về tất cả item đã lưu")
    void testFindAllMultiple() {
        itemDAO.save("i1", new Electronics(100f, "E1", "d1"));
        itemDAO.save("i2", new Art(200f, "A1", "d2"));
        itemDAO.save("i3", new Vehicle(300f, "V1", "d3"));

        List<Item> items = itemDAO.findAll();
        assertEquals(3, items.size());
    }

    // ========================== Test update() ==========================

    @Test
    @Order(30)
    @DisplayName("update() - Cập nhật tên và giá thành công")
    void testUpdateSuccess() {
        itemDAO.save("item-001", new Electronics(1500f, "Old Name", "Old Desc"));

        Electronics updated = new Electronics(2000f, "New Name", "New Desc");
        boolean result = itemDAO.update("item-001", updated);

        assertTrue(result);

        Item found = itemDAO.findById("item-001");
        assertEquals("New Name", found.getName());
        assertEquals(2000f, found.getStartingPrice(), 0.01f);
        assertEquals("New Desc", found.getDescription());
    }

    @Test
    @Order(31)
    @DisplayName("update() - Trả về false khi item không tồn tại")
    void testUpdateNotFound() {
        Electronics item = new Electronics(100f, "Test", "Desc");
        boolean result = itemDAO.update("nonexistent", item);
        assertFalse(result);
    }

    // ========================== Test delete() ==========================

    @Test
    @Order(40)
    @DisplayName("delete() - Xóa item thành công")
    void testDeleteSuccess() {
        itemDAO.save("item-001", new Electronics(100f, "Laptop", "Desc"));
        assertTrue(itemDAO.exists("item-001"));

        boolean result = itemDAO.delete("item-001");
        assertTrue(result);
        assertNull(itemDAO.findById("item-001"));
    }

    @Test
    @Order(41)
    @DisplayName("delete() - Trả về false khi item không tồn tại")
    void testDeleteNotFound() {
        assertFalse(itemDAO.delete("nonexistent"));
    }

    // ========================== Test exists() & count() ==========================

    @Test
    @Order(50)
    @DisplayName("exists() - Trả về đúng trạng thái tồn tại")
    void testExists() {
        assertFalse(itemDAO.exists("item-001"));

        itemDAO.save("item-001", new Electronics(100f, "Test", "Desc"));
        assertTrue(itemDAO.exists("item-001"));
    }

    @Test
    @Order(51)
    @DisplayName("count() - Đếm đúng số lượng item")
    void testCount() {
        assertEquals(0, itemDAO.count());

        itemDAO.save("i1", new Electronics(100f, "E1", "d1"));
        itemDAO.save("i2", new Art(200f, "A1", "d2"));
        assertEquals(2, itemDAO.count());
    }

    // ========================== Test saveItem() với seller ==========================

    @Test
    @Order(60)
    @DisplayName("saveItem() - Lưu item với seller và tự sinh UUID")
    void testSaveItemWithSeller() {
        Electronics laptop = new Electronics(1500f, "Gaming Laptop", "RTX 4090");
        String itemId = itemDAO.saveItem(laptop, "seller_john");

        assertNotNull(itemId, "Phải trả về ID tự sinh");
        assertFalse(itemId.isEmpty(), "ID không được rỗng");

        Item found = itemDAO.findById(itemId);
        assertNotNull(found);
        assertEquals("Gaming Laptop", found.getName());
    }

    @Test
    @Order(61)
    @DisplayName("saveItem() - Từ chối khi item null")
    void testSaveItemNullItem() {
        assertThrows(IllegalArgumentException.class,
                () -> itemDAO.saveItem(null, "seller"));
    }

    @Test
    @Order(62)
    @DisplayName("saveItem() - Từ chối khi seller null")
    void testSaveItemNullSeller() {
        Electronics item = new Electronics(100f, "Test", "Desc");
        assertThrows(IllegalArgumentException.class,
                () -> itemDAO.saveItem(item, null));
    }

    // ========================== Test findBySeller() ==========================

    @Test
    @Order(70)
    @DisplayName("findBySeller() - Tìm tất cả item của một seller")
    void testFindBySeller() {
        itemDAO.saveItem(new Electronics(100f, "E1", "d1"), "john");
        itemDAO.saveItem(new Art(200f, "A1", "d2"), "john");
        itemDAO.saveItem(new Vehicle(300f, "V1", "d3"), "anna");

        Map<String, Item> johnItems = itemDAO.findBySeller("john");
        assertEquals(2, johnItems.size(), "John phải có đúng 2 item");

        Map<String, Item> annaItems = itemDAO.findBySeller("anna");
        assertEquals(1, annaItems.size(), "Anna phải có đúng 1 item");
    }

    @Test
    @Order(71)
    @DisplayName("findBySeller() - Trả về map rỗng khi seller không có item")
    void testFindBySellerEmpty() {
        Map<String, Item> items = itemDAO.findBySeller("nobody");
        assertNotNull(items);
        assertTrue(items.isEmpty());
    }

    // ========================== Test getItemOwner() ==========================

    @Test
    @Order(80)
    @DisplayName("getItemOwner() - Trả về username của seller sở hữu item")
    void testGetItemOwner() {
        String itemId = itemDAO.saveItem(new Electronics(100f, "E1", "d1"), "john");

        String owner = itemDAO.getItemOwner(itemId);
        assertEquals("john", owner);
    }

    @Test
    @Order(81)
    @DisplayName("getItemOwner() - Trả về null khi item không tồn tại")
    void testGetItemOwnerNotFound() {
        assertNull(itemDAO.getItemOwner("nonexistent"));
    }

    // ========================== Test isOwner() ==========================

    @Test
    @Order(90)
    @DisplayName("isOwner() - Trả về true khi seller sở hữu item")
    void testIsOwnerTrue() {
        String itemId = itemDAO.saveItem(new Electronics(100f, "E1", "d1"), "john");
        assertTrue(itemDAO.isOwner(itemId, "john"));
    }

    @Test
    @Order(91)
    @DisplayName("isOwner() - Trả về false khi seller không sở hữu item")
    void testIsOwnerFalse() {
        String itemId = itemDAO.saveItem(new Electronics(100f, "E1", "d1"), "john");
        assertFalse(itemDAO.isOwner(itemId, "anna"));
    }

    // ========================== Test findByName() ==========================

    @Test
    @Order(100)
    @DisplayName("findByName() - Tìm kiếm gần đúng không phân biệt hoa/thường")
    void testFindByName() {
        itemDAO.save("i1", new Electronics(100f, "Gaming Laptop RTX", "d1"));
        itemDAO.save("i2", new Electronics(200f, "Office Laptop", "d2"));
        itemDAO.save("i3", new Art(300f, "Tranh sơn dầu", "d3"));

        Map<String, Item> results = itemDAO.findByName("laptop");
        assertEquals(2, results.size(), "Phải tìm thấy 2 item chứa 'laptop'");
    }

    @Test
    @Order(101)
    @DisplayName("findByName() - Trả về rỗng khi không tìm thấy")
    void testFindByNameNotFound() {
        itemDAO.save("i1", new Electronics(100f, "Laptop", "d1"));

        Map<String, Item> results = itemDAO.findByName("iphone");
        assertTrue(results.isEmpty());
    }

    // ========================== Test findAllAsMap() ==========================

    @Test
    @Order(110)
    @DisplayName("findAllAsMap() - Trả về map với tất cả item")
    void testFindAllAsMap() {
        itemDAO.save("i1", new Electronics(100f, "E1", "d1"));
        itemDAO.save("i2", new Art(200f, "A1", "d2"));

        Map<String, Item> map = itemDAO.findAllAsMap();
        assertEquals(2, map.size());
        assertTrue(map.containsKey("i1"));
        assertTrue(map.containsKey("i2"));
    }
}
