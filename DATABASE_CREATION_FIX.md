# Giải Thích Lỗi Không Thể Tạo Bảng & Phương Án Khắc Phục

Tài liệu này giải thích chi tiết lỗi không thể tạo bảng trong cơ sở dữ liệu khi người dùng tương tác với các nút chức năng trên giao diện ứng dụng, đồng thời đề xuất giải pháp xử lý triệt để.

---

## 1. Mô Tả Lỗi Thực Tế

Khi ứng dụng đã khởi động thành công, người dùng bắt đầu tương tác với các nút trên giao diện (như đăng nhập, đăng ký, mở Dashboard, đặt giá, tạo phiên đấu giá...), ứng dụng bất ngờ bị treo hoặc báo lỗi trong console:
```text
RuntimeException: [AuctionDAO] Không thể tạo bảng
    at Server.dao.AuctionDAO.createTablesIfNotExist(AuctionDAO.java:179)
    at Server.dao.AuctionDAO.<init>(AuctionDAO.java:125)
...
```
Hoặc lỗi tương tự đối với các lớp DAO khác.

---

## 2. Nguyên Nhân Cốt Lõi

Lỗi này phát sinh từ sự kết hợp giữa **Thiết kế Khởi tạo Chậm (Lazy Initialization)** và **Ràng buộc Khóa Ngoại (Foreign Key Constraint)** trong cơ sở dữ liệu MySQL:

### A. Cơ chế khởi tạo chậm (Lazy Loading) của Singleton
Tất cả các lớp DAO (`UserDAO`, `ItemDAO`, `AuctionDAO`, `BidTransactionDAO`) đều được thiết kế theo mẫu **Singleton**:
```java
// Chỉ khởi tạo khi gọi getInstance() lần đầu tiên
public static AuctionDAO getInstance() {
    if (instance == null) {
        synchronized (AuctionDAO.class) {
            if (instance == null) {
                instance = new AuctionDAO(); // Constructor chạy ở đây
            }
        }
    }
    return instance;
}
```
Và việc tạo bảng (`CREATE TABLE IF NOT EXISTS`) được đặt trực tiếp trong Constructor của mỗi DAO. Điều này có nghĩa là **bảng chỉ được tạo ra khi có một chức năng trên giao diện gọi đến DAO đó lần đầu tiên**.

### B. Thứ tự phụ thuộc khóa ngoại bị vi phạm (Foreign Key Order Violation)
Trong thiết kế cơ sở dữ liệu MySQL:
1. Bảng `auction_snapshots` có ràng buộc khóa ngoại tham chiếu đến bảng `items`:
   ```sql
   FOREIGN KEY (item_id) REFERENCES items(item_id) ON DELETE CASCADE
   ```
2. Bảng `auction_participants` có ràng buộc khóa ngoại tham chiếu đến bảng `users`:
   ```sql
   FOREIGN KEY (username) REFERENCES users(username) ON DELETE CASCADE
   ```

Do đó, **bảng `users` và `items` bắt buộc phải tồn tại trước** khi bảng `auction_snapshots` và `auction_participants` được tạo.

**Kịch bản gây lỗi:**
* Khi người dùng click vào Dashboard, hệ thống gọi `DashboardService` -> gọi `AuctionDAO.getInstance()`.
* Constructor của `AuctionDAO` chạy và cố gắng thực thi câu lệnh SQL tạo bảng `auction_snapshots` chứa khóa ngoại tham chiếu tới `items`.
* Nhưng do `ItemDAO.getInstance()` chưa từng được gọi trước đó, **bảng `items` chưa hề tồn tại trong database**.
* MySQL từ chối tạo bảng và ném ra lỗi `SQLException: Cannot add foreign key constraint` (Lỗi ràng buộc khóa ngoại), dẫn đến ứng dụng bị crash.

---

## 3. Giải Pháp Khắc Phục Triệt Để

Thay vì để các DAO khởi tạo chậm một cách ngẫu nhiên phụ thuộc vào thao tác click nút của người dùng, chúng ta sẽ thực hiện **Khởi tạo sớm chủ động (Eager Initialization)** tất cả các DAO ngay tại hàm `main` của **`RunApplication.java`** sau khi kết nối cơ sở dữ liệu thành công.

Chúng ta sẽ khởi tạo chúng theo **đúng thứ tự phụ thuộc**:
1. **`UserDAO.getInstance()`**: Tạo bảng độc lập `users` trước.
2. **`ItemDAO.getInstance()`**: Tạo bảng độc lập `items`.
3. **`AuctionDAO.getInstance()`**: Tạo các bảng phụ thuộc `auction_snapshots`, `auction_bids`, và `auction_participants` (khi này `users` và `items` đã chắc chắn tồn tại).
4. **`BidTransactionDAO.getInstance()`**: Tạo bảng `bid_transactions`.

---

## 4. Kế Hoạch Thay Đổi Mã Nguồn (Implementation Plan)

### File cần chỉnh sửa: [RunApplication.java](file:///c:/Users/Admin/Desktop/clone/Source/src/RunApplication.java)

Chúng ta sẽ sửa đổi khối lệnh kết nối thành công trong hàm `main` để kích hoạt sớm các DAO:

```diff
         // Kiểm tra kết nối Database trước khi chạy App
         try (Connection conn = DatabaseConnection.getConnection()) {
             if (conn != null && !conn.isClosed()) {
                 System.out.println("--- CONNECTED SUCCESSFULLY ---");
                 System.out.println("Server: Clever Cloud Online");
                 System.out.println("Database: " + conn.getCatalog());
                 System.out.flush(); // Ensure success messages appear before UI starts
+
+                System.out.println("Initializing database tables...");
+                // Khởi tạo các DAO theo đúng thứ tự phụ thuộc để tạo bảng an toàn
+                Server.dao.UserDAO.getInstance();
+                Server.dao.ItemDAO.getInstance();
+                Server.dao.AuctionDAO.getInstance();
+                Server.dao.BidTransactionDAO.getInstance();
+                System.out.println("Database tables initialized successfully!");
+                System.out.flush();
             }
         } catch (SQLException e) {
```

### Lợi ích của giải pháp này:
1. **An toàn tuyệt đối**: Đảm bảo tất cả các bảng luôn được tạo theo đúng thứ tự phụ thuộc chính xác, loại bỏ hoàn toàn lỗi khóa ngoại.
2. **Tập trung hóa**: Tất cả bảng được tạo ngay khi ứng dụng khởi động (chỉ mất vài mili-giây), người dùng tương tác sau đó sẽ mượt mà, không gặp bất cứ độ trễ hay lỗi phát sinh nào.
3. **Dễ kiểm soát lỗi**: Nếu có lỗi cấu hình hoặc lỗi tạo bảng, hệ thống sẽ báo ngay lập tức tại thời điểm khởi động thay vì đợi người dùng click nút mới phát sinh lỗi.
