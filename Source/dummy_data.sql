-- --------------------------------------------------------
-- DUMMY DATABASE INSERT SCRIPT CHO HỆ THỐNG ĐẤU GIÁ
-- --------------------------------------------------------

USE blbsc98ma5stojowrgcs;

-- Tạo bảng người dùng (Phải tạo trước vì Items tham chiếu tới đây)
CREATE TABLE IF NOT EXISTS users (
    username VARCHAR(50) PRIMARY KEY,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    role ENUM('BIDDER', 'SELLER', 'ADMIN') NOT NULL
);

-- Tạo bảng sản phẩm
CREATE TABLE IF NOT EXISTS items (
    item_id VARCHAR(36) PRIMARY KEY, -- Để chứa giá trị UUID()
    name VARCHAR(255) NOT NULL,
    starting_price DOUBLE NOT NULL,
    item_type ENUM('ELECTRONICS', 'ART', 'VEHICLE') NOT NULL,
    description TEXT,
    seller_username VARCHAR(50),
    FOREIGN KEY (seller_username) REFERENCES users(username) ON DELETE CASCADE
);

-- 1. Chèn người dùng mẫu (Users)
-- Vai trò (role) có thể là: BIDDER, SELLER, ADMIN
-- INSERT INTO users (username, password, email, role) VALUES
-- 	('admin01', 'adminpass', 'admin@daugia.com', 'ADMIN'),
-- 	('seller_anna', 'anna123', 'anna@seller.com', 'SELLER'),
-- 	('bidder_mike', 'mike123', 'mike@bidder.com', 'BIDDER'),
-- 	('bidder_sarah', 'sarah123', 'sarah@bidder.com', 'BIDDER');

-- 2. Chèn sản phẩm mẫu (Items)
-- item_type có thể là: ELECTRONICS, ART, VEHICLE
-- 	INSERT INTO items (item_id, name, starting_price, item_type, description, seller_username) VALUES
-- 	(UUID(), 'MacBook Pro M3 Max', 3500.0, 'ELECTRONICS', 'Laptop Apple siêu mạnh, RAM 64GB, SSD 2TB.', 'seller_john'),
-- 	(UUID(), 'Bức tranh Mona Lisa (Bản sao)', 500.0, 'ART', 'Bản sao tỷ lệ 1:1 chất lượng cao của bức Mona Lisa.', 'seller_anna'),
-- 	(UUID(), 'Honda Civic 2024', 25000.0, 'VEHICLE', 'Xe ô tô Honda Civic đời mới, odo 0km.', 'seller_john'),
-- 	(UUID(), 'Tượng gỗ Phật Di Lặc', 1200.0, 'ART', 'Tượng gỗ nguyên khối chạm khắc thủ công tinh xảo.', 'seller_anna'),
-- 	(UUID(), 'Sony PlayStation 5 Pro', 800.0, 'ELECTRONICS', 'Máy chơi game thế hệ mới nhất của Sony.', 'seller_john');

-- Xác nhận dữ liệu đã được chèn
SELECT * FROM users;
SELECT * FROM items;
