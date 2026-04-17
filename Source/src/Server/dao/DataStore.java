package Server.dao;

import java.io.*;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Lớp tiện ích hỗ trợ đọc/ghi dữ liệu ra file bằng Java Serialization (an toàn đa luồng).
 * <p>
 * {@code DataStore} cung cấp cơ chế lưu trữ cấp thấp được sử dụng bởi tất cả
 * các lớp DAO. Nó serialize các đối tượng Java (thường là {@code HashMap}) ra file
 * nhị phân bằng {@link ObjectOutputStream}/{@link ObjectInputStream},
 * và bảo vệ truy cập đồng thời bằng {@link ReentrantReadWriteLock}.
 * </p>
 *
 * <h3>Cấu trúc lưu trữ:</h3>
 * Tất cả file dữ liệu được lưu trong thư mục cấu hình (mặc định: {@code data/}).
 * Mỗi DAO có file riêng:
 * <pre>
 *   data/
 *   ├── users.dat          — Map các đối tượng User
 *   ├── items.dat          — Map các đối tượng Item
 *   ├── item_owners.dat    — Map ánh xạ item → seller
 *   └── auctions.dat       — Map các đối tượng AuctionSnapshot
 * </pre>
 *
 * <h3>An toàn đa luồng:</h3>
 * Mỗi instance {@code DataStore} có {@link ReentrantReadWriteLock} riêng.
 * Nhiều thread có thể đọc đồng thời, nhưng ghi thì độc quyền (exclusive).
 * Điều này đảm bảo tính toàn vẹn dữ liệu khi server xử lý nhiều client cùng lúc.
 *
 * <h3>Ví dụ sử dụng:</h3>
 * <pre>{@code
 *   DataStore store = new DataStore("users.dat");
 *   HashMap<String, User> users = new HashMap<>();
 *   users.put("john", new Bidder("john", "pass123", "john@mail.com"));
 *   store.writeData(users);
 *
 *   HashMap<String, User> loaded = store.readData();
 * }</pre>
 *
 * @see GenericDAO
 * @see UserDAO
 * @see ItemDAO
 * @see AuctionDAO
 */
public class DataStore {

    // ========================== Hằng số ==========================

    /**
     * Thư mục mặc định chứa tất cả file dữ liệu.
     * Tự động tạo nếu chưa tồn tại.
     */
    private static final String DEFAULT_DATA_DIR = "data";

    // ========================== Thuộc tính ==========================

    /** Đường dẫn đầy đủ tới file dữ liệu. */
    private final Path filePath;

    /**
     * Khóa đọc-ghi cho truy cập file an toàn đa luồng.
     * Nhiều reader có thể truy cập đồng thời; writer có quyền truy cập độc quyền.
     */
    private final ReentrantReadWriteLock lock;

    // ========================== Constructor ==========================

    /**
     * Tạo {@code DataStore} mới cho tên file cho trước, sử dụng thư mục
     * mặc định ({@value #DEFAULT_DATA_DIR}).
     *
     * @param fileName tên file dữ liệu (VD: {@code "users.dat"})
     */
    public DataStore(String fileName) {
        this(DEFAULT_DATA_DIR, fileName);
    }

    /**
     * Tạo {@code DataStore} với thư mục và tên file tùy chỉnh.
     * <p>
     * Thư mục dữ liệu sẽ được tự động tạo nếu chưa tồn tại.
     * </p>
     *
     * @param dataDir  thư mục chứa file dữ liệu
     * @param fileName tên file dữ liệu
     */
    public DataStore(String dataDir, String fileName) {
        this.filePath = Paths.get(dataDir, fileName);
        this.lock = new ReentrantReadWriteLock();
        ensureDirectoryExists();
    }

    // ========================== Phương thức Public ==========================

    /**
     * Serialize và ghi một {@code HashMap} ra file dữ liệu.
     * <p>
     * Phương thức này lấy khóa ghi (write lock) trước khi thực hiện I/O.
     * Toàn bộ map được ghi nguyên tử (atomic) — nếu ghi thất bại, nội dung
     * file cũ vẫn được giữ nguyên (chiến lược ghi-vào-file-tạm-rồi-đổi-tên).
     * </p>
     *
     * @param data map cần serialize và lưu
     * @param <K>  kiểu khóa
     * @param <V>  kiểu giá trị
     * @throws DataAccessException nếu xảy ra lỗi I/O khi ghi
     */
    public <K, V> void writeData(HashMap<K, V> data) {
        lock.writeLock().lock();
        try {
            // Ghi vào file tạm trước, sau đó đổi tên để đảm bảo tính nguyên tử
            Path tempFile = filePath.resolveSibling(filePath.getFileName() + ".tmp");

            try (ObjectOutputStream oos = new ObjectOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(tempFile)))) {
                oos.writeObject(data);
                oos.flush();
            }

            // Đổi tên nguyên tử: thay thế file gốc bằng file tạm
            Files.move(tempFile, filePath, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);

        } catch (IOException e) {
            throw new DataAccessException("Không thể ghi dữ liệu vào " + filePath, e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Deserialize và đọc {@code HashMap} từ file dữ liệu.
     * <p>
     * Phương thức này lấy khóa đọc (read lock), cho phép nhiều reader đồng thời.
     * Nếu file dữ liệu không tồn tại, trả về {@code HashMap} rỗng thay vì
     * ném ngoại lệ — hỗ trợ trường hợp khởi chạy lần đầu.
     * </p>
     *
     * @param <K> kiểu khóa
     * @param <V> kiểu giá trị
     * @return map đã deserialize, hoặc map rỗng nếu file không tồn tại
     * @throws DataAccessException nếu file tồn tại nhưng không thể đọc hoặc deserialize
     */
    @SuppressWarnings("unchecked")
    public <K, V> HashMap<K, V> readData() {
        lock.readLock().lock();
        try {
            if (!Files.exists(filePath)) {
                return new HashMap<>();
            }

            // Kiểm tra file rỗng (0 byte) — trả về map rỗng
            if (Files.size(filePath) == 0) {
                return new HashMap<>();
            }

            try (ObjectInputStream ois = new ObjectInputStream(
                    new BufferedInputStream(Files.newInputStream(filePath)))) {
                Object obj = ois.readObject();
                if (obj instanceof HashMap) {
                    return (HashMap<K, V>) obj;
                } else {
                    throw new DataAccessException(
                            "File " + filePath + " chứa kiểu dữ liệu không mong đợi: " + obj.getClass().getName());
                }
            }

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("[DataStore] Cảnh báo: Không thể đọc " + filePath
                    + ". Trả về dữ liệu rỗng. Nguyên nhân: " + e.getMessage());
            return new HashMap<>();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Xóa file dữ liệu khỏi ổ đĩa.
     * Hữu ích cho việc test hoặc reset dữ liệu.
     *
     * @return {@code true} nếu file đã được xóa, {@code false} nếu file không tồn tại
     * @throws DataAccessException nếu xảy ra lỗi I/O khi xóa
     */
    public boolean deleteFile() {
        lock.writeLock().lock();
        try {
            return Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new DataAccessException("Không thể xóa file dữ liệu " + filePath, e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Kiểm tra file dữ liệu có tồn tại trên ổ đĩa hay không.
     *
     * @return {@code true} nếu file tồn tại và có dữ liệu
     */
    public boolean fileExists() {
        lock.readLock().lock();
        try {
            return Files.exists(filePath) && Files.size(filePath) > 0;
        } catch (IOException e) {
            return false;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Trả về đường dẫn đầy đủ của file dữ liệu.
     *
     * @return đường dẫn dưới dạng đối tượng {@link Path}
     */
    public Path getFilePath() {
        return filePath;
    }

    // ========================== Phương thức Private ==========================

    /**
     * Đảm bảo thư mục cha của file dữ liệu tồn tại, tạo mới nếu cần.
     */
    private void ensureDirectoryExists() {
        try {
            Path parentDir = filePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
                System.out.println("[DataStore] Đã tạo thư mục dữ liệu: " + parentDir);
            }
        } catch (IOException e) {
            throw new DataAccessException("Không thể tạo thư mục dữ liệu cho " + filePath, e);
        }
    }

    // ========================== Lớp ngoại lệ nội bộ ==========================

    /**
     * Ngoại lệ unchecked cho các lỗi truy cập dữ liệu.
     * Bọc (wrap) các ngoại lệ I/O và serialization bên dưới với thông báo rõ ràng.
     */
    public static class DataAccessException extends RuntimeException {

        public DataAccessException(String message) {
            super(message);
        }

        public DataAccessException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
