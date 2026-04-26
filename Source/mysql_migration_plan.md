# Kế hoạch chuyển đổi DAO sang MySQL

## Tổng quan

Hiện tại, hệ thống dùng **Java Serialization** (lưu vào file `.dat`) thông qua `DataStore`.
Khi chuyển sang **MySQL**, kiến trúc thay đổi như sau:

| Thành phần | Hiện tại | Sau khi đổi |
|---|---|---|
| Lưu trữ | File `.dat` (binary serialize) | MySQL database |
| Kết nối | `DataStore` (file I/O) | `DatabaseConnection` (JDBC) |
| Truy vấn | Map trong RAM | SQL queries |
| Cache | `HashMap` trong memory | Tùy chọn (có hoặc không) |
| Lock | `ReentrantReadWriteLock` | Transaction của MySQL |

---

## Phân tích từng class cần thay đổi

### 1. `DataStore.java` → **XÓA / THAY BẰNG** `DatabaseConnection.java`

`DataStore` hiện quản lý đọc/ghi file serialize. Với MySQL, cần thay bằng một class quản lý kết nối JDBC.

**Tạo mới: `DatabaseConnection.java`**
```java
public class DatabaseConnection {
    private static final String URL = "jdbc:mysql://localhost:3306/bidding_db";
    private static final String USER = "root";
    private static final String PASSWORD = "your_password";

    // Connection Pool (dùng HikariCP hoặc giữ Connection đơn giản)
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
```

> [!IMPORTANT]
> Nên dùng **Connection Pool** (HikariCP) thay vì tạo Connection mới mỗi lần để tránh overhead.

---

### 2. `GenericDAO.java` — **GIỮ NGUYÊN** (interface không đổi)

Interface này chỉ định nghĩa contract (CRUD), không phụ thuộc vào storage backend.
Chỉ cần **xóa hoặc điều chỉnh** 2 method không còn ý nghĩa với MySQL:

```diff
- void flush();   // Không cần — MySQL tự commit
- void reload();  // Không cần — đọc trực tiếp từ DB mỗi lần
```

Hoặc giữ lại nhưng để implementation rỗng / no-op.

---

### 3. `UserDAO.java` — **VIẾT LẠI HOÀN TOÀN**

#### Xóa bỏ:
- `DataStore dataStore` field
- `HashMap<String, User> users` cache field
- `ReentrantReadWriteLock lock` field
- `persistData()` method
- Mọi logic đọc/ghi cache trong RAM

#### Thay bằng SQL:

| Method hiện tại | SQL tương đương |
|---|---|
| `save(username, user)` | `INSERT INTO users (username, password, email, role) VALUES (?, ?, ?, ?)` |
| `findById(username)` | `SELECT * FROM users WHERE username = ?` |
| `findAll()` | `SELECT * FROM users` |
| `update(username, user)` | `UPDATE users SET password=?, email=? WHERE username=?` |
| `delete(username)` | `DELETE FROM users WHERE username = ?` |
| `exists(username)` | `SELECT COUNT(*) FROM users WHERE username = ?` |
| `count()` | `SELECT COUNT(*) FROM users` |
| `authenticate(username, password)` | `SELECT * FROM users WHERE username=? AND password=?` |
| `findByEmail(email)` | `SELECT * FROM users WHERE email = ?` |
| `findByRole(role)` | `SELECT * FROM users WHERE role = ?` |

#### Schema bảng `users`:
```sql
CREATE TABLE users (
    username    VARCHAR(50)  PRIMARY KEY,
    password    VARCHAR(255) NOT NULL,
    email       VARCHAR(100) NOT NULL UNIQUE,
    role        ENUM('BIDDER','SELLER','ADMIN') NOT NULL,
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);
```

> [!NOTE]
> Vì `User` là abstract class với 3 subtype (Bidder, Seller, Admin), cần mapping thêm
> cột `role` để biết tạo lại subclass nào khi `SELECT`.

---

### 4. `ItemDAO.java` — **VIẾT LẠI HOÀN TOÀN**

#### Xóa bỏ:
- `DataStore itemStore`, `DataStore ownerStore`
- `HashMap<String, Item> items`
- `HashMap<String, String> itemOwners`
- `UUID.randomUUID()` tự sinh ID (MySQL có thể dùng `VARCHAR UUID` hoặc `AUTO_INCREMENT`)

#### Schema bảng `items` và `item_owners`:
```sql
CREATE TABLE items (
    item_id      VARCHAR(36)    PRIMARY KEY,   -- UUID
    name         VARCHAR(255)   NOT NULL,
    starting_price DECIMAL(15,2) NOT NULL,
    item_type    VARCHAR(50)    NOT NULL,       -- ELECTRONICS, ART, VEHICLE...
    description  TEXT,
    -- Các field đặc thù theo type (hoặc dùng bảng riêng)
    extra_data   JSON           -- Lưu các thuộc tính riêng của từng type
);

CREATE TABLE item_owners (
    item_id         VARCHAR(36)  PRIMARY KEY,
    seller_username VARCHAR(50)  NOT NULL,
    FOREIGN KEY (item_id) REFERENCES items(item_id) ON DELETE CASCADE,
    FOREIGN KEY (seller_username) REFERENCES users(username)
);
```

> [!WARNING]
> `Item` là abstract class có nhiều subtype (Electronics, Art, Vehicle...). Cần chiến lược mapping:
> - **Cách 1**: Dùng cột `item_type` + JSON `extra_data` cho các thuộc tính khác nhau (đơn giản nhất)
> - **Cách 2**: Tạo bảng riêng cho mỗi subtype (chuẩn hơn nhưng phức tạp)

| Method | SQL tương đương |
|---|---|
| `saveItem(item, seller)` | `INSERT INTO items...` + `INSERT INTO item_owners...` |
| `findById(itemId)` | `SELECT * FROM items WHERE item_id = ?` |
| `findBySeller(seller)` | `SELECT i.* FROM items i JOIN item_owners o ON ... WHERE o.seller_username = ?` |
| `getItemOwner(itemId)` | `SELECT seller_username FROM item_owners WHERE item_id = ?` |
| `findByName(namePart)` | `SELECT * FROM items WHERE name LIKE ?` |
| `delete(itemId)` | `DELETE FROM items WHERE item_id = ?` (CASCADE xóa item_owners) |

---

### 5. `AuctionDAO.java` — **VIẾT LẠI HOÀN TOÀN** (phức tạp nhất)

Đây là class phức tạp nhất vì `AuctionSnapshot` chứa `LinkedList<Bid>` (danh sách bid)
và `List<String>` (danh sách người tham gia) — những cấu trúc lồng nhau cần bảng riêng.

#### Schema (3 bảng):
```sql
CREATE TABLE auctions (
    auction_id      INT          PRIMARY KEY,
    client_owner    VARCHAR(50)  NOT NULL,
    item_id         VARCHAR(36)  NOT NULL,
    created_at      TIMESTAMP    NOT NULL,
    terminate_at    TIMESTAMP    NOT NULL,
    type            VARCHAR(30)  NOT NULL,    -- Time_Fixed / Time_With_Reset
    status          ENUM('OPEN','RUNNING','FINISHED','PAID','CANCELED') NOT NULL,
    was_in_countdown BOOLEAN     DEFAULT FALSE,
    FOREIGN KEY (client_owner) REFERENCES users(username),
    FOREIGN KEY (item_id) REFERENCES items(item_id)
);

CREATE TABLE bids (
    bid_id          INT          AUTO_INCREMENT PRIMARY KEY,
    auction_id      INT          NOT NULL,
    bidder_username VARCHAR(50)  NOT NULL,
    bid_amount      DECIMAL(15,2) NOT NULL,
    bid_time        TIMESTAMP    NOT NULL,
    FOREIGN KEY (auction_id) REFERENCES auctions(auction_id) ON DELETE CASCADE,
    FOREIGN KEY (bidder_username) REFERENCES users(username)
);

CREATE TABLE auction_participants (
    auction_id      INT          NOT NULL,
    username        VARCHAR(50)  NOT NULL,
    PRIMARY KEY (auction_id, username),
    FOREIGN KEY (auction_id) REFERENCES auctions(auction_id) ON DELETE CASCADE,
    FOREIGN KEY (username) REFERENCES users(username)
);
```

| Method | SQL tương đương |
|---|---|
| `save(auctionId, snapshot)` | `INSERT INTO auctions...` + insert item + bids + participants |
| `findById(id)` | JOIN 3 bảng để build lại `AuctionSnapshot` |
| `update(id, snapshot)` | `UPDATE auctions SET status=?..` + sync bids/participants |
| `addBid(id, bid)` | `INSERT INTO bids...` + `UPDATE auctions SET status='RUNNING'...` |
| `addParticipant(id, user)` | `INSERT IGNORE INTO auction_participants...` |
| `removeParticipant(id, user)` | `DELETE FROM auction_participants WHERE...` |
| `updateStatus(id, status)` | `UPDATE auctions SET status=? WHERE auction_id=?` |
| `findActiveAuctions()` | `SELECT * FROM auctions WHERE status NOT IN ('FINISHED','PAID','CANCELED')` |
| `getBidHistory(id)` | `SELECT * FROM bids WHERE auction_id=? ORDER BY bid_amount DESC` |
| `getMaxAuctionId()` | `SELECT MAX(auction_id) FROM auctions` |

> [!IMPORTANT]
> `AuctionSnapshot` phải được tái tạo từ JOIN nhiều bảng. Cần viết method `buildSnapshot(ResultSet rs, Connection conn)`
> để map từ SQL result về object Java.

---

### 6. `AuctionSnapshot.java` — **GIỮ NGUYÊN hoặc tinh chỉnh nhỏ**

Class này chỉ là Plain Java Object (POJO) dùng để truyền dữ liệu giữa DAO và tầng Service.
- Có thể **xóa** `implements Serializable` (không còn cần serialize ra file)
- Xóa `serialVersionUID`
- Giữ nguyên tất cả field, getter/setter, và utility methods

---

## Tóm tắt công việc cần làm

```
1. [ ] Tạo database schema MySQL (6 bảng: users, items, item_owners, auctions, bids, auction_participants)
2. [ ] Thêm MySQL Connector/J vào classpath (download JAR hoặc dùng Maven/Gradle)
3. [ ] Tạo class DatabaseConnection.java (thay DataStore.java)
4. [ ] Viết lại UserDAO.java dùng JDBC
5. [ ] Viết lại ItemDAO.java dùng JDBC
6. [ ] Viết lại AuctionDAO.java dùng JDBC (phức tạp nhất)
7. [ ] Cập nhật GenericDAO.java (bỏ/giữ flush/reload)
8. [ ] Tinh chỉnh AuctionSnapshot.java (bỏ Serializable)
9. [ ] Test lại toàn bộ luồng
```

> [!TIP]
> Nếu dùng Maven, thêm dependency sau vào `pom.xml`:
> ```xml
> <dependency>
>     <groupId>com.mysql</groupId>
>     <artifactId>mysql-connector-j</artifactId>
>     <version>8.3.0</version>
> </dependency>
> ```
> Nếu không dùng Maven, download file `mysql-connector-j-8.x.x.jar` và thêm vào Build Path.
