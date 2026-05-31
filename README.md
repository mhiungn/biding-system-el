# Bidify - Online Auction System (Hệ Thống Đấu Giá Trực Tuyến)

Bidify là ứng dụng desktop đấu giá trực tuyến được xây dựng bằng **JavaFX**, sử dụng kiến trúc **Client-Server** đa luồng qua **TCP Sockets** và mô hình **MVC** hoàn chỉnh. Hệ thống cung cấp đầy đủ các tính năng xác thực người dùng, tạo phiên đấu giá, tham gia đấu giá realtime, bảo mật giao dịch số dư ví, tự động gia hạn tránh bắn tỉa (Anti-Sniping), và thông báo đẩy tức thời.

---

## 1. Công Nghệ & Môi Trường Chạy (Technology Stack & Requirements)

### Công nghệ sử dụng (Technologies)
- **Core Runtime**: Java JDK 25.
- **Desktop Graphical UI**: JavaFX 21.0.6 & FXML.
- **Database & Persistence**: MySQL với thư viện quản lý kết nối hiệu năng cao **HikariCP** Connection Pool.
- **Cloud Storage**: **Cloudinary Java SDK** dùng để upload/lưu trữ ảnh sản phẩm trực tuyến qua giao thức bảo mật HTTPS.
- **Mock DB for Testing**: H2 Database (ở chế độ MySQL compatibility) phục vụ chạy test độc lập.
- **Build Tool**: Maven Wrapper (`mvnw`).
- **Logging**: SLF4J & Logback Classic (`logback.xml`).

### Yêu cầu cài đặt (Requirements)
- **JDK**: Phiên bản Java 25 (Khuyên dùng OpenJDK hoặc Oracle JDK 25).
- **Internet**: Cần thiết để ứng dụng kết nối tới Database online Clever Cloud và upload ảnh sản phẩm lên Cloudinary Cloud.
- **Cấu hình Database & Cloudinary**: Dự án yêu cầu 2 file cấu hình trong thư mục `Source/resources/` (đã được cấu hình sẵn cục bộ và đưa vào `.gitignore` để bảo mật):
  - **`Source/resources/db.properties`**:
    ```properties
    db.url=jdbc:mysql://bnivgjhov6apvpsej5ym-mysql.services.clever-cloud.com:20985/bnivgjhov6apvpsej5ym
    db.user=urhbndcybrfhy0sb
    db.password=Gt37ZauKWCr4UeTUNiMt
    ```
  - **`Source/resources/cloudinary.properties`**:
    ```properties
    cloudinary.cloud_name=dyl5f4uv0
    cloudinary.api_key=536591431215475
    cloudinary.api_secret=5qlt42f-sdJD98j3Eft6eFnz6ro
    ```

---

## 2. Cấu Trúc Thư Mục & Module (Directory & Module Structure)

```text
├── .github/workflows/ci.yml         # Pipeline CI/CD tự động biên dịch và chạy test trên GitHub
├── pom.xml                         # File cấu hình Maven, quản lý các dependencies & đóng gói Shade JAR
├── Source/
│   ├── src/
│   │   ├── Client/                 # Phân hệ Client
│   │   │   ├── app/                # Điểm khởi chạy JavaFX Application
│   │   │   ├── components/         # Các thành phần UI dùng chung (Header, LoadingOverlay, Notifications)
│   │   │   ├── core/               # Thư viện điều hướng màn hình và Client Sockets
│   │   │   └── features/           # Các Controllers & Services cho từng tính năng (Auth, Bidding, Profile, Dashboard)
│   │   ├── CommonClasses/          # Các đối tượng Domain (Auction, User, Bid) & DTOs dùng chung
│   │   ├── Packets/                # Giao thức truyền tin, cấu hình mạng & MessageType
│   │   └── Server/                 # Phân hệ Server
│   │       ├── dao/                # Tầng truy xuất dữ liệu MySQL (JDBC DAOs, H2 Helpers & SQL Scripts)
│   │       └── service/            # Tầng xử lý logic nghiệp vụ phía Server (Auth, Bidding, Wallet, Expire Scheduler)
│   └── resources/                  # Tài nguyên tĩnh
│       ├── client/views/           # File giao diện FXML & CSS (thiết kế theo phong cách Spotify Dark Theme)
│       ├── client/images/          # Các file Icon, Logo, Hình ảnh dạng PNG/JPG
│       └── client/fonts/           # Font chữ thiết kế (Gotham Black, SVN-Canopee, SpaceMono)
└── src/test/java                   # Hơn 220 unit tests chạy tự động bằng JUnit 5
```

---

## 3. Vị Trí File .jar Đóng Gói (Executable Fat JAR Location)

Sau khi biên dịch và đóng gói, file JAR được tạo ra tại thư mục:
📂 **`target/HeThongDauGia-1.0-SNAPSHOT.jar`**

### Cách tự đóng gói ứng dụng (How to Build the Fat JAR):
Mở terminal tại thư mục gốc của dự án và chạy lệnh sau (yêu cầu cấu hình `JAVA_HOME` trỏ tới JDK 25):
```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-25.0.2"
.\mvnw.cmd clean package
```
Lệnh này sẽ tự động chạy toàn bộ 220 bài kiểm thử đơn vị (Unit Tests), biên dịch mã nguồn và đóng gói tất cả các dependencies vào **một file Fat JAR duy nhất**.

---

## 4. Hướng Dẫn Chạy Chương Trình (Running Instructions)

Ứng dụng chạy theo mô hình Client-Server. Để khởi chạy hệ thống, bạn cần thực hiện theo đúng 2 bước sau trên các cửa sổ dòng lệnh riêng biệt:

### Bước 1: Khởi chạy Server trước (Start the Server first)
Mở một cửa sổ Terminal (Git Bash, PowerShell hoặc CMD) tại thư mục gốc của dự án và chạy lệnh:
```bash
java -jar HeThongDauGia.jar server
```
*Hệ thống sẽ kết nối Database và hiển thị thông báo: `Server Đấu Giá đã khởi động tại cổng: 12345`.*

### Bước 2: Khởi chạy các Client sau (Start the Clients second)
Sau khi Server đã hoạt động, mở thêm một hoặc nhiều cửa sổ Terminal mới độc lập và chạy lệnh:
```bash
java -jar HeThongDauGia.jar
```
*Giao diện đăng nhập Dark Theme sẽ xuất hiện, tự động kết nối tới Server đang chạy ở Bước 1. Bạn có thể mở nhiều Client đồng thời để thực hiện đấu giá trực tiếp.*

---

## 5. Danh Sách Chức Năng Đã Hoàn Thành (Features List)

### Chức năng bắt buộc (100% Completed)
- [x] **Quản lý người dùng**: Đăng ký, đăng nhập bảo mật, phân quyền người dùng (Bidder / Seller / Admin).
- [x] **Quản lý sản phẩm**: Đăng bán sản phẩm đầy đủ mô tả, giá khởi điểm, ảnh minh họa trực quan.
- [x] **Chức năng đấu giá**: Đặt giá tăng dần hợp lệ theo bước giá tối thiểu, hiển thị lịch sử đấu giá tức thì.
- [x] **Kiến trúc Client-Server**: Kết nối Socket TCP đa luồng xử lý đồng thời hàng chục Client kết nối cùng lúc.
- [x] **Cập nhật realtime**: Đồng bộ trạng thái đấu giá, số dư khả dụng và thông báo tức thời thông qua TCP Pushes.
- [x] **Kết thúc tự động**: Server chạy Scheduler ngầm để kết thúc phiên đấu giá đúng giờ, trích ví người thắng và chuyển khoản cho người bán.
- [x] **Xử lý đồng thời (Concurrency)**: Sử dụng **Pessimistic Row Lock (`SELECT ... FOR UPDATE`)** để loại bỏ hoàn toàn lỗi tranh chấp số dư ví hoặc đặt giá đồng thời (Race Condition).
- [x] **Kiểm thử tự động**: Tích hợp **220 Unit Tests** chạy trên H2 Database độc lập.
- [x] **CI/CD Pipeline**: Đồng bộ hóa tự động hóa build và test với GitHub Actions.

### Chức năng nâng cao (Optional Feature - Điểm cộng +0.5đ)
- [x] **Anti-Sniping (Gia hạn thời gian tự động)**: Nếu có lượt đặt giá mới xuất hiện trong vòng **2 phút cuối cùng** trước khi hết hạn, hệ thống sẽ tự động gia hạn thêm **5 phút** cho phiên đấu giá để đảm bảo tính công bằng và tối đa hóa lợi nhuận cho người bán.

---

## 6. Tài Liệu Báo Cáo & Demo (Submission Artifacts)


---

