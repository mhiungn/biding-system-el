//Donat Salihu
//Nikolaos Lintas
//Memli Restelica
//Philippos Kalatzis

package CommonClasses;

import java.io.Serializable;
import java.util.Objects;

/**
 * Đại diện cho một sản phẩm đang được đấu giá trong hệ thống.
 * <p>
 * Mỗi {@code Item} chứa các siêu dữ liệu cơ bản về sản phẩm/dịch vụ được bán:
 * tên hiển thị, mô tả văn bản, và giá khởi điểm tối thiểu mà
 * người bán chấp nhận. Lớp này được nhúng bên trong một {@link Auction}
 * và được truyền qua mạng như một phần của các đối tượng payload khác nhau,
 * do đó nó implement {@link Serializable}.
 * </p>
 *
 * <h3>Ví dụ sử dụng:</h3>
 * <pre>{@code
 *   Item laptop = new Item(500.0f, "Gaming Laptop", "Laptop RTX cấu hình cao, ít sử dụng.");
 *   System.out.println(laptop.getName());           // "Gaming Laptop"
 *   System.out.println(laptop.getStartingPrice());   // 500.0
 * }</pre>
 *
 * @see Auction
 * @see Bid
 */
public class Item implements Serializable {

    // ========================== Thuộc tính ==========================

    /** Giá tối thiểu để bắt đầu phiên đấu giá cho sản phẩm này. */
    private float startingPrice;

    /** Tên hiển thị của sản phẩm (VD: "Đồng hồ cổ điển", "Laptop Gaming"). */
    private String name;

    /** Mô tả chi tiết bằng văn bản của sản phẩm. */
    private String description;

    // ========================== Constructor ==========================

    /**
     * Khởi tạo một {@code Item} mới với giá khởi điểm, tên và mô tả cho trước.
     *
     * @param startingPrice giá tối thiểu để bắt đầu đặt mức giá (phải là số dương)
     * @param name          tên hiển thị của sản phẩm
     * @param description   mô tả chi tiết về sản phẩm
     */
    public Item(float startingPrice, String name, String description) {
        this.startingPrice = startingPrice;
        this.name = name;
        this.description = description;
    }

    // ========================== Getter & Setter ==========================

    /**
     * Trả về giá khởi điểm của sản phẩm này.
     *
     * @return giá bắt đầu dưới dạng số thực (float)
     */
    public float getStartingPrice() {
        return startingPrice;
    }

    /**
     * Cập nhật giá khởi điểm của sản phẩm này.
     *
     * @param startingPrice giá khởi điểm mới
     */
    public void setStartingPrice(float startingPrice) {
        this.startingPrice = startingPrice;
    }

    /**
     * Trả về tên hiển thị của sản phẩm này.
     *
     * @return tên sản phẩm
     */
    public String getName() {
        return name;
    }

    /**
     * Cập nhật tên hiển thị của sản phẩm này.
     *
     * @param name tên mới
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Trả về mô tả của sản phẩm này.
     *
     * @return chuỗi mô tả sản phẩm
     */
    public String getDescription() {
        return description;
    }

    /**
     * Cập nhật mô tả cho sản phẩm này.
     *
     * @param description mô tả mới
     */
    public void setDescription(String description) {
        this.description = description;
    }

    // ========================== Phương thức Tiện ích ==========================

    /**
     * Trả về chuỗi đại diện dễ đọc cho sản phẩm này,
     * bao gồm giá khởi điểm, tên và mô tả.
     *
     * @return chuỗi định dạng mô tả sản phẩm
     */
    @Override
    public String toString() {
        return "Item{" +
                "startingPrice=" + startingPrice +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                '}';
    }

    /**
     * So sánh sản phẩm này với một đối tượng khác để xem có bằng nhau không.
     * Hai sản phẩm được coi là bằng nhau nếu chúng có cùng giá khởi điểm,
     * tên, và mô tả.
     *
     * @param o đối tượng cần so sánh
     * @return {@code true} nếu hai đối tượng bằng nhau, ngược lại là {@code false}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Item item = (Item) o;
        return Float.compare(item.startingPrice, startingPrice) == 0 &&
                Objects.equals(name, item.name) &&
                Objects.equals(description, item.description);
    }

    /**
     * Trả về giá trị mã băm (hash code) cho sản phẩm này dựa vào giá khởi điểm,
     * tên, và mô tả.
     *
     * @return mã băm
     */
    @Override
    public int hashCode() {
        return Objects.hash(startingPrice, name, description);
    }
}
