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
- **Cấu hình Database & Cloudinary**: Dự án yêu cầu 2 file cấu hình trong thư mục `Source/resources/` (đã được cấu hình sẵn và đẩy trực tiếp lên GitHub để chương trình chạy được ngay lập tức):
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

## 2. Cấu Trúc Thư Mục & Bản Đồ Lớp (Directory & Detailed Class Map)

Dưới đây là cấu trúc thư mục chi tiết, bao gồm toàn bộ các class và tệp tin mã nguồn trong dự án để phục vụ việc chấm điểm:

```text
├── .github/workflows/ci.yml         # Pipeline CI/CD tự động biên dịch và chạy test trên GitHub
├── pom.xml                         # Cấu hình Maven, Shade build & dependencies
├── HeThongDauGia.jar               # File JAR thực thi đóng gói hoàn chỉnh (Chạy trực tiếp ở thư mục gốc)
├── Source/
│   ├── src/
│   │   ├── RunApplication.java     # Hàm main kiểm tra DB & phân luồng khởi chạy Server/Client
│   │   ├── Client/                 # Phân hệ Client (JavaFX Application & MVC Controllers)
│   │   │   ├── app/
│   │   │   │   └── ClientApp.java  # Lớp khởi chạy chính của Client App
│   │   │   ├── components/         # Các component UI tái sử dụng
│   │   │   │   ├── AppHeader.java            # Thanh Header chính (Navigation, Số dư, Avatar)
│   │   │   │   ├── HeaderSearchPopup.java    # Thanh tìm kiếm nhanh thời gian thực ở Header
│   │   │   │   ├── LoadingOverlay.java       # Màn hình chờ khóa giao diện khi xử lý tác vụ
│   │   │   │   └── NotificationPopup.java    # Khung thông báo đẩy tức thời cho người dùng
│   │   │   ├── core/               # Phần lõi điều hướng & kết nối Sockets của Client
│   │   │   │   ├── network/        # Client Sockets & xử lý bất đồng bộ Push events
│   │   │   │   │   ├── NetworkClient.java, NetworkRequestClient.java, NetworkPushManager.java, PushEventListener.java, PushEventRouter.java
│   │   │   │   └── ui/             # Tiện ích giao diện & dịch vụ quản lý avatar
│   │   │   │       ├── AvatarService.java, BaseController.java, FxDebouncer.java, ItemImageUrl.java, NavigationController.java, RefreshablePage.java
│   │   │   ├── features/           # Các luồng nghiệp vụ & Controllers màn hình
│   │   │   │   ├── auth/           # Đăng nhập & Đăng ký: AuthService.java, LoginController.java, SessionManager.java, SignupController.java
│   │   │   │   ├── bidding/        # Chi tiết đấu giá & Lịch sử: AuctionDetailService.java, BiddingDetailController.java, MyBidsController.java, MyBidsService.java
│   │   │   │   ├── dashboard/      # Màn hình chính danh sách phiên: DashboardController.java, DashboardService.java
│   │   │   │   ├── notifications/  # Nhận thông báo: NotificationClientService.java
│   │   │   │   ├── profile/        # Quản lý cá nhân & Nạp tiền: ProfileService.java, UserProfileController.java
│   │   │   │   ├── search/         # Dịch vụ tìm kiếm: SearchService.java
│   │   │   │   └── sell/           # Đăng bán sản phẩm: SellItemController.java, SellItemRequest.java, SellItemResult.java, SellItemService.java
│   │   │   └── navigation/         # Dịch vụ chuyển trang và lưu cache View
│   │   │   │   └── NavigationService.java
│   │   ├── CommonClasses/          # Các đối tượng nghiệp vụ Domain & DTOs chia sẻ giữa Client-Server
│   │   │   ├── dto/                # WalletDTO.java, NotificationDTO.java, DashboardAuctionRow.java, vv.
│   │   │   ├── Exceptions/         # Các ngoại lệ tuỳ chỉnh (LowBidException, NotOwnerException, vv.)
│   │   │   ├── Items/              # Các loại mặt hàng đấu giá thừa kế từ Item (Art, Vehicle, RealEstate, Fashion, Collectibles, Electronics)
│   │   │   ├── Auction.java, User.java, Bid.java, BidObserver.java, BidTransaction.java, Entity.java
│   │   ├── Packets/                # Định nghĩa giao thức mạng gói tin
│   │   │   ├── MessageType.java, NetworkConfig.java, NetworkErrorPayload.java, PacketFactory.java, PacketMessage.java
│   │   └── Server/                 # Phân hệ Server (Socket Server, Threading & Tầng DAO)
│   │       ├── Server.java         # Điểm khởi chạy Multi-client Socket Server trên cổng 12345
│   │       ├── ClientHandler.java, Client.java # Luồng xử lý giao tiếp socket của từng client kết nối
│   │       ├── AuctionCountdownTask.java, AuctionTerminateTask.java # Bộ lập lịch quét phiên đấu giá hết hạn
│   │       ├── dao/                # Tầng truy xuất CSDL MySQL (JDBC DAOs)
│   │       │   ├── DatabaseConnection.java, database-schema.sql, UserDAO.java, ItemDAO.java, AuctionDAO.java, NotificationDAO.java, WalletDAO.java, BidTransactionDAO.java
│   │       └── service/            # Tầng xử lý logic nghiệp vụ Socket phía Server
│   │           ├── AuctionService.java, AuthenticationService.java, BiddingApplicationService.java, BidService.java, ImageStorageService.java, NetworkPushService.java, WalletApplicationService.java
│   └── resources/                  # Tài nguyên tĩnh được đóng gói vào classpath
│       ├── Client/views/           # File giao diện FXML & CSS (thiết kế Spotify Dark Theme)
│       ├── Client/images/          # Các Icon, Logo, Hình ảnh dạng PNG/JPG
│       ├── Client/fonts/           # Font chữ thiết kế (Gotham, SVN-Canopee, SpaceMono)
│       ├── db.properties           # Tệp cấu hình cơ sở dữ liệu MySQL online
│       └── cloudinary.properties   # Tệp cấu hình API lưu trữ ảnh Cloudinary online
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

