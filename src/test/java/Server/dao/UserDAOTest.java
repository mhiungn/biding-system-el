package Server.dao;

import CommonClasses.*;

import org.junit.jupiter.api.*;

import java.sql.SQLException;
import java.util.Date;
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

    private static User standardUser(String username, String password, String email) {
        return new User(username, password, email, "USER");
    }

    private static User adminUser(String username, String password, String email) {
        return new User(username, password, email, "ADMIN");
    }

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
    @DisplayName("save() - Lưu USER mới thành công")
    void testSaveBidder() {
        User bidder = standardUser("john", "pass123", "john@mail.com");
        userDAO.save("john", bidder);

        User found = userDAO.findById("john");
        assertNotNull(found, "User phải được tìm thấy sau khi save");
        assertEquals("john", found.getUsername());
        assertEquals("pass123", found.getPassword());
        assertEquals("john@mail.com", found.getEmail());
        assertEquals("USER", found.getRole());
        assertNull(found.getProfileImageUrl());
    }

    @Test
    @Order(2)
    @DisplayName("save() - Lưu USER thứ hai thành công")
    void testSaveSeller() {
        User seller = standardUser("anna", "pass456", "anna@mail.com");
        userDAO.save("anna", seller);

        User found = userDAO.findById("anna");
        assertNotNull(found);
        assertEquals("USER", found.getRole());
    }

    @Test
    @Order(3)
    @DisplayName("save() - Lưu ADMIN mới thành công")
    void testSaveAdmin() {
        User admin = adminUser("root", "admin123", "root@mail.com");
        userDAO.save("root", admin);

        User found = userDAO.findById("root");
        assertNotNull(found);
        assertEquals("ADMIN", found.getRole());
    }

    @Test
    @Order(4)
    @DisplayName("save() - Từ chối khi username null")
    void testSaveNullUsername() {
        User bidder = standardUser("test", "pass", "test@mail.com");
        assertThrows(IllegalArgumentException.class,
                () -> userDAO.save(null, bidder),
                "Phải ném IllegalArgumentException khi username null");
    }

    @Test
    @Order(5)
    @DisplayName("save() - Từ chối khi username rỗng")
    void testSaveEmptyUsername() {
        User bidder = standardUser("test", "pass", "test@mail.com");
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
        User bidder1 = standardUser("john", "pass1", "john@mail.com");
        User bidder2 = standardUser("john", "pass2", "john2@mail.com");

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
        userDAO.save("bob", standardUser("bob", "pass", "bob@mail.com"));
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
        userDAO.save("user1", standardUser("user1", "p1", "u1@mail.com"));
        userDAO.save("user2", standardUser("user2", "p2", "u2@mail.com"));
        userDAO.save("user3", adminUser("user3", "p3", "u3@mail.com"));

        List<User> users = userDAO.findAll();
        assertEquals(3, users.size(), "Phải trả về đúng 3 user");
    }

    // ========================== Test update() ==========================

    @Test
    @Order(30)
    @DisplayName("update() - Cập nhật password và email thành công")
    void testUpdateSuccess() {
        userDAO.save("john", standardUser("john", "oldpass", "old@mail.com"));
        String profileImageUrl = "https://res.cloudinary.com/demo/image/upload/user-profiles/john/avatar.png";
        assertTrue(userDAO.updateProfileImageUrl("john", profileImageUrl));

        User updated = standardUser("john", "newpass", "new@mail.com");
        boolean result = userDAO.update("john", updated);

        assertTrue(result, "update phải trả về true khi thành công");

        User found = userDAO.findById("john");
        assertEquals("newpass", found.getPassword());
        assertEquals("new@mail.com", found.getEmail());
        assertEquals(profileImageUrl, found.getProfileImageUrl());
    }

    @Test
    @Order(31)
    @DisplayName("update() - Trả về false khi user không tồn tại")
    void testUpdateNotFound() {
        User bidder = standardUser("ghost", "pass", "ghost@mail.com");
        boolean result = userDAO.update("ghost", bidder);
        assertFalse(result, "update phải trả về false khi user không tồn tại");
    }

    @Test
    @Order(32)
    @DisplayName("updateContactInfo() - updates phone and location")
    void testUpdateContactInfoSuccess() {
        userDAO.save("john", standardUser("john", "pass", "john@mail.com"));
        String profileImageUrl = "https://res.cloudinary.com/demo/image/upload/user-profiles/john/avatar.png";
        assertTrue(userDAO.updateProfileImageUrl("john", profileImageUrl));

        boolean result = userDAO.updateContactInfo("john", "+84 123 456 789", "Hanoi");

        assertTrue(result);
        User found = userDAO.findById("john");
        assertEquals("+84 123 456 789", found.getPhone());
        assertEquals("Hanoi", found.getLocation());
        assertEquals(profileImageUrl, found.getProfileImageUrl());
    }

    @Test
    @Order(33)
    @DisplayName("updateProfileImageUrl() - persists profile image URL")
    void testUpdateProfileImageUrlSuccess() {
        userDAO.save("john", standardUser("john", "pass", "john@mail.com"));
        String profileImageUrl = "https://res.cloudinary.com/demo/image/upload/user-profiles/john/avatar.png";

        boolean result = userDAO.updateProfileImageUrl("john", profileImageUrl);

        assertTrue(result);
        User found = userDAO.findById("john");
        assertEquals(profileImageUrl, found.getProfileImageUrl());
    }

    @Test
    @Order(34)
    @DisplayName("updateProfileImageUrl() - returns false when user does not exist")
    void testUpdateProfileImageUrlNotFound() {
        boolean result = userDAO.updateProfileImageUrl(
                "ghost",
                "https://res.cloudinary.com/demo/image/upload/user-profiles/ghost/avatar.png");

        assertFalse(result);
    }

    // ========================== Test delete() ==========================

    @Test
    @Order(40)
    @DisplayName("delete() - Xóa user thành công")
    void testDeleteSuccess() {
        userDAO.save("john", standardUser("john", "pass", "john@mail.com"));
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
        userDAO.save("john", standardUser("john", "pass", "john@mail.com"));
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
        userDAO.save("u1", standardUser("u1", "p1", "e1@mail.com"));
        userDAO.save("u2", standardUser("u2", "p2", "e2@mail.com"));
        assertEquals(2, userDAO.count());
    }

    // ========================== Test authenticate() ==========================

    @Test
    @Order(70)
    @DisplayName("authenticate() - Đăng nhập thành công")
    void testAuthenticateSuccess() {
        userDAO.save("john", standardUser("john", "pass123", "john@mail.com"));

        User authenticated = userDAO.authenticate("john", "pass123");
        assertNotNull(authenticated, "Xác thực phải thành công với đúng username/password");
        assertEquals("john", authenticated.getUsername());
    }

    @Test
    @Order(71)
    @DisplayName("authenticate() - Thất bại khi sai password")
    void testAuthenticateWrongPassword() {
        userDAO.save("john", standardUser("john", "pass123", "john@mail.com"));

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
        userDAO.save("john", standardUser("john", "pass", "john@mail.com"));

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
    @DisplayName("findByRole() - Lọc đúng user theo vai trò USER")
    void testFindByRole() {
        userDAO.save("b1", standardUser("b1", "p", "b1@m.com"));
        userDAO.save("b2", standardUser("b2", "p", "b2@m.com"));
        userDAO.save("s1", standardUser("s1", "p", "s1@m.com"));

        List<User> bidders = userDAO.findByRole("USER");
        assertEquals(3, bidders.size(), "Phải tìm thấy đúng 3 USER");
        for (User u : bidders) {
            assertEquals("USER", u.getRole());
        }
    }

    // ========================== Test isEmailTaken() ==========================

    @Test
    @Order(100)
    @DisplayName("isEmailTaken() - Trả về true khi email đã được dùng")
    void testIsEmailTakenTrue() {
        userDAO.save("john", standardUser("john", "pass", "taken@mail.com"));
        assertTrue(userDAO.isEmailTaken("taken@mail.com"));
    }

    @Test
    @Order(101)
    @DisplayName("isEmailTaken() - Trả về false khi email chưa được dùng")
    void testIsEmailTakenFalse() {
        assertFalse(userDAO.isEmailTaken("free@mail.com"));
    }

    @Test
    @Order(102)
    @DisplayName("getCreatedAt() - returns account creation time")
    void testGetCreatedAt() {
        userDAO.save("john", standardUser("john", "pass", "john@mail.com"));

        Date createdAt = userDAO.getCreatedAt("john");

        assertNotNull(createdAt);
    }
}
