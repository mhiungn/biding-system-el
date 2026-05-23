# Hướng Dẫn Khắc Phục Lỗi & Chạy Ứng Dụng Đấu Giá

Tài liệu này giải thích chi tiết các lỗi hệ thống gặp phải khi chạy `RunApplication`, nguyên nhân cốt lõi và các bước đã được thực hiện để sửa lỗi giúp ứng dụng khởi động thành công.

---

## 1. Chi Tiết Lỗi Gặp Phải

Khi khởi động ứng dụng bằng `RunApplication`, hệ thống gặp 2 lỗi nghiêm trọng:

### Lỗi 1: Không tìm thấy file cấu hình Database (`db.properties`)
```text
Không tìm thấy file db.properties!
CONNECT FAILED! CHECK YOUR INTERNET or User/Pass.
java.sql.SQLException: Cấu hình database chưa được tải!
    at Server.dao.DatabaseConnection.getConnection(DatabaseConnection.java:95)
    at RunApplication.main(RunApplication.java:12)
```

### Lỗi 2: Lỗi NullPointerException khi khởi tạo giao diện JavaFX
```text
java.lang.NullPointerException: Location is required.
    at javafx.fxml.FXMLLoader.loadImpl(FXMLLoader.java:3369)
    ...
    at Client.app.ClientApp.start(ClientApp.java:29)
```

---

## 2. Nguyên Nhân Cốt Lõi

### Nguyên nhân A: Tài nguyên chưa được đồng bộ vào thư mục Build (`target/classes`)
* Các file tài nguyên bao gồm cấu hình database (`db.properties`), giao diện người dùng (`client/views/**/*.fxml`), phông chữ (`client/fonts/`), và ảnh (`client/images/`) nằm trong thư mục **`Source/resources/`**.
* Khi chạy trực tiếp `RunApplication` từ IDE hoặc bằng lệnh java thông thường không qua Maven Build, các file tài nguyên này **chưa được copy** sang thư mục biên dịch chạy thực tế (`target/classes/`). 
* Do đó, ClassLoader trả về `null` khi tìm kiếm `/db.properties` và `/client/views/auth/login.fxml`, gây ra các lỗi thiếu cấu hình database và lỗi `Location is required`.

### Nguyên nhân B: Tiến trình Java ngầm bị treo khóa tệp tin (File Locking)
* Trong quá trình chạy thử nghiệm trước đó, có nhiều tiến trình `java` ngầm vẫn chạy ngầm và giữ khóa (lock) các tệp tài nguyên tĩnh (đặc biệt là các font như `SVN-Canopee.otf`).
* Điều này khiến việc biên dịch mới hoặc dọn dẹp thư mục build (`mvn clean`) bị lỗi quyền truy cập và thất bại.

---

## 3. Các Bước Đã Thực Hiện Để Khắc Phục

### Bước 1: Giải phóng tài nguyên bị khóa
Chúng tôi đã tìm kiếm các tiến trình Java đang chạy ẩn và buộc dừng (kill) chúng hoàn toàn để giải phóng tệp tin:
```powershell
Stop-Process -Name java -Force
```

### Bước 2: Đồng bộ tài nguyên bằng Maven Wrapper
Chúng tôi đã thiết lập đường dẫn JDK chính xác trên máy của bạn (`C:\Program Files\Java\jdk-25.0.2`) và chạy lệnh của Maven Wrapper (`mvnw.cmd`) để thực hiện dọn dẹp và sao chép lại toàn bộ tài nguyên:
```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-25.0.2"
.\mvnw.cmd clean process-resources compile
```
Lệnh này đã:
1. Dọn dẹp thư mục build cũ bị lỗi (`target/`).
2. Tự động sao chép chính xác 21 tệp tài nguyên từ `Source/resources/` vào `target/classes/` (trong đó có `db.properties` và thư mục `client/views/`).
3. Biên dịch lại toàn bộ 67 file mã nguồn Java.

### Bước 3: Khởi chạy và kiểm tra ứng dụng
Ứng dụng được chạy thử nghiệm trực tiếp thông qua plugin JavaFX của Maven:
```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-25.0.2"
.\mvnw.cmd javafx:run
```
**Kết quả kiểm tra thực tế:**
* Hệ thống tìm thấy tệp `db.properties` và kết nối database thành công:
  ```text
  Checking database connection...
  --- CONNECTED SUCCESSFULLY ---
  Server: Clever Cloud Online
  Database: bidding_db
  [UserDAO] Đã khởi tạo với MySQL...
  ```
* Ứng dụng JavaFX khởi động hoàn toàn bình thường, không còn lỗi FXML NullPointerException.

---

## 4. Hướng Dẫn Chạy Dự Án Cho Lần Sau

Để tránh gặp lại các lỗi trên, vui lòng áp dụng một trong hai cách dưới đây để chạy dự án:

### Cách 1: Sử dụng dòng lệnh (Khuyên dùng)
Mở PowerShell tại thư mục gốc của dự án (`c:\Users\Admin\Desktop\clone`) và chạy:

1. **Build dự án & cập nhật tài nguyên (Chỉ cần chạy khi bạn sửa đổi file FXML, ảnh hoặc properties):**
   ```powershell
   $env:JAVA_HOME="C:\Program Files\Java\jdk-25.0.2"
   .\mvnw.cmd clean process-resources compile
   ```
2. **Khởi chạy ứng dụng:**
   ```powershell
   $env:JAVA_HOME="C:\Program Files\Java\jdk-25.0.2"
   .\mvnw.cmd javafx:run
   ```

### Cách 2: Cấu hình trên IDE (IntelliJ IDEA)
Nếu bạn muốn click nút "Run" trên IntelliJ IDEA mà không bị lỗi:
1. Đảm bảo bạn đã mở thư mục này dưới dạng **Maven Project** (để IntelliJ đọc cấu hình từ file `pom.xml`).
2. Kiểm tra xem thư mục `Source/resources` đã được định nghĩa làm tài nguyên chưa:
   * Click chuột phải vào thư mục `Source/resources` -> Chọn **Mark Directory as** -> **Resources Root**.
3. Nếu gặp lỗi khóa file khi build, hãy vào thanh công cụ bên phải chọn **Maven** -> Click đúp vào **clean** rồi đến **compile** trước khi ấn nút Run.
