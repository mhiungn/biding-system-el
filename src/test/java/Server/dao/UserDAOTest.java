package Server.dao;

import CommonClasses.*;

import org.junit.jupiter.api.*;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests cho {@link UserDAO}.
 * <p>
 * Sử dụng H2 in-memory database (MySQL compatibility mode) thay cho MySQL thật.
 * {@link DatabaseConnection} được redirect sang H2 bằng reflection trước khi
 * khởi tạo DAO Singleton, giúp test chạy nhanh và không phụ thuộc network.
 * </p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UserDAOTest {

    private static UserDAO userDAO;

    @BeforeAll
    static void setUpAll() throws Exception {
        // Redirect DatabaseConnection → H2 in-memory
        TestDatabaseHelper.redirectToH2();
        TestDatabaseHelper.createAllTables();

        // Reset singleton để tạo instance mới kết nối H2
        TestDatabaseHelper.resetSingleton(UserDAO.class);
        userDAO = UserDAO.getInstance();
    }

    @AfterAll
    static void tearDownAll() throws Exception {
        TestDatabaseHelper.dropAllTables();
        TestDatabaseHelper.resetSingleton(UserDAO.class);
    }

    @BeforeEach
    void clearData() throws SQLException {
        TestDatabaseHelper.clearAllTables();
    }

    // ========================== Test save() ==========================

    @Test
    @Order(1)
    @DisplayName("save() - Lưu Bidder mới thành công")
    void testSaveBidder() {
        Bidder bidder = new Bidder("john", "pass123", "john@mail.com");
        userDAO.save("john", bidder);

        User found = userDAO.findById("john");
        assertNotNull(found, "User phải được tìm thấy sau khi save");
        assertEquals("john", found.getUsername());
        assertEquals("pass123", found.getPassword());
        assertEquals("john@mail.com", found.getEmail());
        assertEquals("BIDDER", found.getRole());
        assertInstanceOf(Bidder.class, found, "User phải được map đúng kiểu Bidder");
    }

    @Test
    @Order(2)
    @DisplayName("save() - Lưu Seller mới thành công")
    void testSaveSeller() {
        Seller seller = new Seller("anna", "pass456", "anna@mail.com");
        userDAO.save("anna", seller);

        User found = userDAO.findById("anna");
        assertNotNull(found);
        assertEquals("SELLER", found.getRole());
        assertInstanceOf(Seller.class, found, "User phải được map đúng kiểu Seller");
    }

    @Test
    @Order(3)
    @DisplayName("save() - Lưu Admin mới thành công")
    void testSaveAdmin() {
        Admin admin = new Admin("root", "admin123", "root@mail.com");
        userDAO.save("root", admin);

        User found = userDAO.findById("root");
        assertNotNull(found);
        assertEquals("ADMIN", found.getRole());
        assertInstanceOf(Admin.class, found, "User phải được map đúng kiểu Admin");
    }

    @Test
    @Order(4)
    @DisplayName("save() - Từ chối khi username null")
    void testSaveNullUsername() {
        Bidder bidder = new Bidder("test", "pass", "test@mail.com");
        assertThrows(IllegalArgumentException.class,
                () -> userDAO.save(null, bidder),
                "Phải ném IllegalArgumentException khi username null");
    }

    @Test
    @Order(5)
    @DisplayName("save() - Từ chối khi username rỗng")
    void testSaveEmptyUsername() {
        Bidder bidder = new Bidder("test", "pass", "test@mail.com");
        assertThrows(IllegalArgumentException.class,
                () -> userDAO.save("   ", bidder),
                "Phải ném IllegalArgumentException khi username rỗng");
    }

    @Test
    @Order(6)
    @DisplayName("save() - Từ chối khi user null")
    void testSaveNullUser() {
        assertThrows(IllegalArgumentException.class,
                () -> userDAO.save("john", null),
                "Phải ném IllegalArgumentException khi user null");
    }

    @Test
    @Order(7)
    @DisplayName("save() - Bỏ qua khi username đã tồn tại")
    void testSaveDuplicate() {
        Bidder bidder1 = new Bidder("john", "pass1", "john@mail.com");
        Bidder bidder2 = new Bidder("john", "pass2", "john2@mail.com");

        userDAO.save("john", bidder1);
        userDAO.save("john", bidder2); // Phải bị bỏ qua

        User found = userDAO.findById("john");
        assertEquals("pass1", found.getPassword(),
                "Password phải giữ nguyên giá trị ban đầu vì save trùng bị từ chối");
    }

    // ========================== Test findById() ==========================

    @Test
    @Order(10)
    @DisplayName("findById() - Trả về null khi không tìm thấy")
    void testFindByIdNotFound() {
        User found = userDAO.findById("nonexistent");
        assertNull(found, "Phải trả về null khi user không tồn tại");
    }

    @Test
    @Order(11)
    @DisplayName("findById() - Tìm thấy user đã lưu")
    void testFindByIdSuccess() {
        userDAO.save("bob", new Bidder("bob", "pass", "bob@mail.com"));
        User found = userDAO.findById("bob");
        assertNotNull(found);
        assertEquals("bob", found.getUsername());
    }

    // ========================== Test findAll() ==========================

    @Test
    @Order(20)
    @DisplayName("findAll() - Trả về danh sách rỗng khi chưa có user")
    void testFindAllEmpty() {
        List<User> users = userDAO.findAll();
        assertNotNull(users);
        assertTrue(users.isEmpty(), "Danh sách phải rỗng khi chưa có dữ liệu");
    }

    @Test
    @Order(21)
    @DisplayName("findAll() - Trả về đầy đủ tất cả user")
    void testFindAllMultiple() {
        userDAO.save("user1", new Bidder("user1", "p1", "u1@mail.com"));
        userDAO.save("user2", new Seller("user2", "p2", "u2@mail.com"));
        userDAO.save("user3", new Admin("user3", "p3", "u3@mail.com"));

        List<User> users = userDAO.findAll();
        assertEquals(3, users.size(), "Phải trả về đúng 3 user");
    }

    // ========================== Test update() ==========================

    @Test
    @Order(30)
    @DisplayName("update() - Cập nhật password và email thành công")
    void testUpdateSuccess() {
        userDAO.save("john", new Bidder("john", "oldpass", "old@mail.com"));

        Bidder updated = new Bidder("john", "newpass", "new@mail.com");
        boolean result = userDAO.update("john", updated);

        assertTrue(result, "update phải trả về true khi thành công");

        User found = userDAO.findById("john");
        assertEquals("newpass", found.getPassword());
        assertEquals("new@mail.com", found.getEmail());
    }

    @Test
    @Order(31)
    @DisplayName("update() - Trả về false khi user không tồn tại")
    void testUpdateNotFound() {
        Bidder bidder = new Bidder("ghost", "pass", "ghost@mail.com");
        boolean result = userDAO.update("ghost", bidder);
        assertFalse(result, "update phải trả về false khi user không tồn tại");
    }

    // ========================== Test delete() ==========================

    @Test
    @Order(40)
    @DisplayName("delete() - Xóa user thành công")
    void testDeleteSuccess() {
        userDAO.save("john", new Bidder("john", "pass", "john@mail.com"));
        assertTrue(userDAO.exists("john"), "User phải tồn tại trước khi xóa");

        boolean result = userDAO.delete("john");
        assertTrue(result, "delete phải trả về true");
        assertNull(userDAO.findById("john"), "User phải không còn tồn tại sau khi xóa");
    }

    @Test
    @Order(41)
    @DisplayName("delete() - Trả về false khi user không tồn tại")
    void testDeleteNotFound() {
        boolean result = userDAO.delete("nonexistent");
        assertFalse(result, "delete phải trả về false khi user không tồn tại");
    }

    // ========================== Test exists() ==========================

    @Test
    @Order(50)
    @DisplayName("exists() - Trả về true khi user tồn tại")
    void testExistsTrue() {
        userDAO.save("john", new Bidder("john", "pass", "john@mail.com"));
        assertTrue(userDAO.exists("john"));
    }

    @Test
    @Order(51)
    @DisplayName("exists() - Trả về false khi user không tồn tại")
    void testExistsFalse() {
        assertFalse(userDAO.exists("nonexistent"));
    }

    // ========================== Test count() ==========================

    @Test
    @Order(60)
    @DisplayName("count() - Trả về 0 khi chưa có user")
    void testCountEmpty() {
        assertEquals(0, userDAO.count());
    }

    @Test
    @Order(61)
    @DisplayName("count() - Đếm đúng số lượng user")
    void testCountMultiple() {
        userDAO.save("u1", new Bidder("u1", "p1", "e1@mail.com"));
        userDAO.save("u2", new Seller("u2", "p2", "e2@mail.com"));
        assertEquals(2, userDAO.count());
    }

    // ========================== Test authenticate() ==========================

    @Test
    @Order(70)
    @DisplayName("authenticate() - Đăng nhập thành công")
    void testAuthenticateSuccess() {
        userDAO.save("john", new Bidder("john", "pass123", "john@mail.com"));

        User authenticated = userDAO.authenticate("john", "pass123");
        assertNotNull(authenticated, "Xác thực phải thành công với đúng username/password");
        assertEquals("john", authenticated.getUsername());
    }

    @Test
    @Order(71)
    @DisplayName("authenticate() - Thất bại khi sai password")
    void testAuthenticateWrongPassword() {
        userDAO.save("john", new Bidder("john", "pass123", "john@mail.com"));

        User result = userDAO.authenticate("john", "wrongpass");
        assertNull(result, "Xác thực phải thất bại khi sai password");
    }

    @Test
    @Order(72)
    @DisplayName("authenticate() - Thất bại khi user không tồn tại")
    void testAuthenticateUserNotExists() {
        User result = userDAO.authenticate("nobody", "pass");
        assertNull(result, "Xác thực phải thất bại khi user không tồn tại");
    }

    // ========================== Test findByEmail() ==========================

    @Test
    @Order(80)
    @DisplayName("findByEmail() - Tìm thấy user theo email")
    void testFindByEmailFound() {
        userDAO.save("john", new Bidder("john", "pass", "john@mail.com"));

        User found = userDAO.findByEmail("john@mail.com");
        assertNotNull(found);
        assertEquals("john", found.getUsername());
    }

    @Test
    @Order(81)
    @DisplayName("findByEmail() - Trả về null khi email không tồn tại")
    void testFindByEmailNotFound() {
        User found = userDAO.findByEmail("nobody@mail.com");
        assertNull(found);
    }

    // ========================== Test findByRole() ==========================

    @Test
    @Order(90)
    @DisplayName("findByRole() - Lọc đúng user theo vai trò BIDDER")
    void testFindByRole() {
        userDAO.save("b1", new Bidder("b1", "p", "b1@m.com"));
        userDAO.save("b2", new Bidder("b2", "p", "b2@m.com"));
        userDAO.save("s1", new Seller("s1", "p", "s1@m.com"));

        List<User> bidders = userDAO.findByRole("BIDDER");
        assertEquals(2, bidders.size(), "Phải tìm thấy đúng 2 BIDDER");
        for (User u : bidders) {
            assertInstanceOf(Bidder.class, u);
        }
    }

    // ========================== Test isEmailTaken() ==========================

    @Test
    @Order(100)
    @DisplayName("isEmailTaken() - Trả về true khi email đã được dùng")
    void testIsEmailTakenTrue() {
        userDAO.save("john", new Bidder("john", "pass", "taken@mail.com"));
        assertTrue(userDAO.isEmailTaken("taken@mail.com"));
    }

    @Test
    @Order(101)
    @DisplayName("isEmailTaken() - Trả về false khi email chưa được dùng")
    void testIsEmailTakenFalse() {
        assertFalse(userDAO.isEmailTaken("free@mail.com"));
    }
}
