package Server.dao;

import java.util.List;

/**
 * Interface DAO (Data Access Object) tổng quát định nghĩa các thao tác CRUD
 * chuẩn.
 * <p>
 * Tất cả các lớp DAO trong hệ thống đấu giá đều implement interface này
 * để cung cấp một API thống nhất cho việc lưu trữ dữ liệu. Các tham số
 * kiểu generic cho phép mỗi DAO tự chỉ định kiểu khóa và giá trị riêng.
 * </p>
 *
 * @param <K> kiểu của khóa dùng để định danh thực thể (VD: String username,
 *            Integer auctionId)
 * @param <V> kiểu của thực thể được lưu trữ (VD: User, Item, AuctionSnapshot)
 */
public interface GenericDAO<K, V> {

    /**
     * Lưu một thực thể mới với khóa cho trước.
     * Nếu thực thể với khóa này đã tồn tại, hành vi tùy thuộc vào lớp triển khai
     * (có thể ném ngoại lệ hoặc ghi đè).
     *
     * @param key   khóa duy nhất xác định thực thể
     * @param value thực thể cần lưu
     */
    void save(K key, V value);

    /**
     * Tìm kiếm một thực thể theo khóa duy nhất.
     *
     * @param key khóa của thực thể cần tìm
     * @return thực thể nếu tìm thấy, hoặc {@code null} nếu không tồn tại
     */
    V findById(K key);

    /**
     * Lấy tất cả các thực thể đã lưu.
     *
     * @return danh sách tất cả thực thể; trả về danh sách rỗng nếu không có
     */
    List<V> findAll();

    /**
     * Cập nhật một thực thể đã tồn tại, xác định bằng khóa.
     *
     * @param key   khóa của thực thể cần cập nhật
     * @param value dữ liệu mới để thay thế
     * @return {@code true} nếu tìm thấy và cập nhật thành công, {@code false} nếu
     *         không tồn tại
     */
    boolean update(K key, V value);

    /**
     * Xóa một thực thể xác định bằng khóa.
     *
     * @param key khóa của thực thể cần xóa
     * @return {@code true} nếu tìm thấy và xóa thành công, {@code false} nếu không
     *         tồn tại
     */
    boolean delete(K key);

    /**
     * Kiểm tra xem thực thể với khóa cho trước có tồn tại hay không.
     *
     * @param key khóa cần kiểm tra
     * @return {@code true} nếu tồn tại, {@code false} nếu không
     */
    boolean exists(K key);

    /**
     * Trả về tổng số thực thể đã lưu.
     *
     * @return số lượng thực thể
     */
    int count();
}
