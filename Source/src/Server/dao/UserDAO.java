package Server.dao;

import CommonClasses.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Lớp DAO quản lý việc lưu trữ dữ liệu {@link User}.
 * <p>
 * DAO này xử lý tất cả các thao tác dữ liệu liên quan đến người dùng: đăng ký,
 * xác thực, và CRUD cho tài khoản {@link Bidder}, {@link Seller}, {@link Admin}.
 * Sử dụng Java Serialization thông qua {@link DataStore} để lưu dữ liệu ra ổ đĩa.
 * </p>
 *
 * <h3>Singleton Pattern:</h3>
 * {@code UserDAO} được triển khai dưới dạng Singleton an toàn đa luồng
 * (double-checked locking) để đảm bảo chỉ có một điểm truy cập dữ liệu
 * người dùng duy nhất trong toàn bộ ứng dụng server.
 *
 * <h3>Ánh xạ khóa:</h3>
 * Người dùng được lưu trong {@code HashMap<String, User>} với khóa là
 * {@link User#getUsername() username}. Cho phép tra cứu O(1) khi xác thực
 * và đảm bảo username không bị trùng.
 *
 * <h3>An toàn đa luồng:</h3>
 * Tất cả phương thức public được bảo vệ bởi {@link ReentrantReadWriteLock},
 * cho phép đọc đồng thời nhưng ghi thì độc quyền.
 *
 * <h3>Ví dụ sử dụng:</h3>
 * <pre>{@code
 *   UserDAO userDAO = UserDAO.getInstance();
 *   userDAO.save("john", new Bidder("john", "pass123", "john@mail.com"));
 *
 *   User user = userDAO.authenticate("john", "pass123");
 *   if (user != null) {
 *       System.out.println("Đăng nhập thành công: " + user.getDisplayInfo());
 *   }
 * }</pre>
 *
 * @see User
 * @see Bidder
 * @see Seller
 * @see Admin
 * @see GenericDAO
 * @see DataStore
 */
public class UserDAO implements GenericDAO<String, User> {

    // ========================== Hằng số ==========================

    /** Tên file lưu trữ dữ liệu người dùng. */
    private static final String DATA_FILE = "users.dat";

    // ========================== Singleton ==========================

    /** Instance duy nhất của UserDAO. */
    private static volatile UserDAO instance;

    /**
     * Trả về instance Singleton của {@code UserDAO}.
     * Sử dụng double-checked locking để khởi tạo lazy an toàn đa luồng.
     *
     * @return instance Singleton của {@code UserDAO}
     */
    public static UserDAO getInstance() {
        if (instance == null) {
            synchronized (UserDAO.class) {
                if (instance == null) {
                    instance = new UserDAO();
                }
            }
        }
        return instance;
    }

    // ========================== Thuộc tính ==========================

    /** Kho lưu trữ file cho serialization. */
    private final DataStore dataStore;

    /** Bộ nhớ đệm (cache) trong RAM chứa tất cả user, khóa theo username. */
    private HashMap<String, User> users;

    /** Khóa đọc-ghi cho truy cập an toàn đa luồng vào cache. */
    private final ReentrantReadWriteLock lock;

    // ========================== Constructor ==========================

    /**
     * Constructor private — sử dụng {@link #getInstance()} để lấy Singleton.
     * Tải dữ liệu người dùng từ ổ đĩa khi khởi tạo.
     */
    private UserDAO() {
        this.dataStore = new DataStore(DATA_FILE);
        this.lock = new ReentrantReadWriteLock();
        this.users = dataStore.readData();
        System.out.println("[UserDAO] Đã khởi tạo. Tải " + users.size() + " người dùng từ ổ đĩa.");
    }

    // ========================== Triển khai GenericDAO ==========================

    /**
     * Đăng ký một người dùng mới vào hệ thống.
     * <p>
     * User được lưu vào bộ nhớ và ngay lập tức ghi xuống ổ đĩa.
     * Nếu user với username này đã tồn tại, thao tác bị từ chối
     * và in ra cảnh báo.
     * </p>
     *
     * @param username tên đăng nhập duy nhất (khóa)
     * @param user     đối tượng User (Bidder, Seller, hoặc Admin)
     * @throws IllegalArgumentException nếu username rỗng/null hoặc user là null
     */
    @Override
    public void save(String username, User user) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username không được để trống hoặc null");
        }
        if (user == null) {
            throw new IllegalArgumentException("User không được null");
        }

        lock.writeLock().lock();
        try {
            if (users.containsKey(username)) {
                System.err.println("[UserDAO] Cảnh báo: User '" + username + "' đã tồn tại. Dùng update() thay thế.");
                return;
            }
            users.put(username, user);
            persistData();
            System.out.println("[UserDAO] Đã lưu user: " + username + " (vai trò: " + user.getRole() + ")");
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Tìm người dùng theo username.
     *
     * @param username tên đăng nhập cần tìm
     * @return {@link User} nếu tìm thấy, hoặc {@code null} nếu không tồn tại
     */
    @Override
    public User findById(String username) {
        lock.readLock().lock();
        try {
            return users.get(username);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Trả về danh sách tất cả người dùng đã đăng ký.
     *
     * @return danh sách mới chứa tất cả user (an toàn để chỉnh sửa)
     */
    @Override
    public List<User> findAll() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(users.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Cập nhật thông tin của một người dùng đã tồn tại.
     * <p>
     * User được xác định bằng username. Nếu không tìm thấy user
     * với username này, thao tác cập nhật bị từ chối.
     * </p>
     *
     * @param username tên đăng nhập của user cần cập nhật
     * @param user     dữ liệu User mới
     * @return {@code true} nếu tìm thấy và cập nhật thành công, {@code false} nếu không
     */
    @Override
    public boolean update(String username, User user) {
        lock.writeLock().lock();
        try {
            if (!users.containsKey(username)) {
                return false;
            }
            users.put(username, user);
            persistData();
            System.out.println("[UserDAO] Đã cập nhật user: " + username);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Xóa một người dùng theo username.
     *
     * @param username tên đăng nhập của user cần xóa
     * @return {@code true} nếu tìm thấy và xóa thành công, {@code false} nếu không
     */
    @Override
    public boolean delete(String username) {
        lock.writeLock().lock();
        try {
            User removed = users.remove(username);
            if (removed != null) {
                persistData();
                System.out.println("[UserDAO] Đã xóa user: " + username);
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Kiểm tra xem user với username cho trước có tồn tại hay không.
     *
     * @param username tên đăng nhập cần kiểm tra
     * @return {@code true} nếu tồn tại
     */
    @Override
    public boolean exists(String username) {
        lock.readLock().lock();
        try {
            return users.containsKey(username);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Trả về tổng số người dùng đã đăng ký.
     *
     * @return số lượng user
     */
    @Override
    public int count() {
        lock.readLock().lock();
        try {
            return users.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Ghi toàn bộ dữ liệu user trong bộ nhớ xuống ổ đĩa.
     */
    @Override
    public void flush() {
        lock.writeLock().lock();
        try {
            persistData();
            System.out.println("[UserDAO] Đã ghi " + users.size() + " user xuống ổ đĩa.");
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Tải lại toàn bộ dữ liệu user từ ổ đĩa, thay thế cache trong bộ nhớ.
     */
    @Override
    public void reload() {
        lock.writeLock().lock();
        try {
            this.users = dataStore.readData();
            System.out.println("[UserDAO] Đã tải lại " + users.size() + " user từ ổ đĩa.");
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ========================== Phương thức Xác thực ==========================

    /**
     * Xác thực người dùng bằng cách kiểm tra username và password.
     * <p>
     * Đây là phương thức chính được sử dụng trong luồng đăng nhập.
     * Thực hiện so sánh phân biệt chữ hoa/thường cho cả username và password.
     * </p>
     *
     * @param username tên đăng nhập cần xác thực
     * @param password mật khẩu cần kiểm tra
     * @return đối tượng {@link User} nếu thông tin hợp lệ,
     *         hoặc {@code null} nếu xác thực thất bại
     */
    public User authenticate(String username, String password) {
        lock.readLock().lock();
        try {
            User user = users.get(username);
            if (user != null && user.getPassword().equals(password)) {
                System.out.println("[UserDAO] Xác thực thành công cho: " + username);
                return user;
            }
            System.out.println("[UserDAO] Xác thực thất bại cho: " + username);
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    // ========================== Phương thức Truy vấn ==========================

    /**
     * Tìm người dùng theo địa chỉ email.
     *
     * @param email địa chỉ email cần tìm
     * @return {@link User} nếu tìm thấy, hoặc {@code null} nếu không có user nào có email này
     */
    public User findByEmail(String email) {
        lock.readLock().lock();
        try {
            for (User user : users.values()) {
                if (user.getEmail().equals(email)) {
                    return user;
                }
            }
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Tìm tất cả người dùng theo vai trò (role) cụ thể.
     *
     * @param role vai trò cần lọc (VD: "BIDDER", "SELLER", "ADMIN")
     * @return danh sách user có vai trò tương ứng
     */
    public List<User> findByRole(String role) {
        lock.readLock().lock();
        try {
            List<User> result = new ArrayList<>();
            for (User user : users.values()) {
                if (user.getRole().equalsIgnoreCase(role)) {
                    result.add(user);
                }
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Kiểm tra xem địa chỉ email đã được đăng ký hay chưa.
     *
     * @param email email cần kiểm tra
     * @return {@code true} nếu email đã được sử dụng
     */
    public boolean isEmailTaken(String email) {
        return findByEmail(email) != null;
    }

    // ========================== Phương thức Private ==========================

    /**
     * Ghi map user hiện tại xuống ổ đĩa.
     * Phải được gọi khi đang giữ write lock.
     */
    private void persistData() {
        dataStore.writeData(users);
    }
}
