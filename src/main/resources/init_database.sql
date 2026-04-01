-- 1. Tạo Database mới (Căn hộ mới) và sử dụng nó
CREATE DATABASE IF NOT EXISTS hethongdaugia;
USE hethongdaugia;

-- 2. Bảng Người dùng (Đáp ứng yêu cầu Role: ADMIN, SELLER, BIDDER)
CREATE TABLE Users (
                       user_id INT AUTO_INCREMENT PRIMARY KEY,
                       username VARCHAR(50) UNIQUE NOT NULL,
                       password VARCHAR(255) NOT NULL,
                       role ENUM('ADMIN', 'SELLER', 'BIDDER') NOT NULL
);

-- 3. Bảng Sản phẩm đấu giá (Phân loại: ELECTRONICS, ART, VEHICLE)
CREATE TABLE Items (
                       item_id INT AUTO_INCREMENT PRIMARY KEY,
                       seller_id INT NOT NULL,
                       name VARCHAR(255) NOT NULL,
                       description TEXT,
                       category ENUM('ELECTRONICS', 'ART', 'VEHICLE') NOT NULL,
                       starting_price DECIMAL(15, 2) NOT NULL,
                       FOREIGN KEY (seller_id) REFERENCES Users(user_id)
);

-- 4. Bảng Phiên đấu giá
CREATE TABLE Auctions (
                          auction_id INT AUTO_INCREMENT PRIMARY KEY,
                          item_id INT NOT NULL,
                          start_time DATETIME NOT NULL,
                          end_time DATETIME NOT NULL,
                          current_highest_bid DECIMAL(15, 2) DEFAULT 0,
                          status ENUM('OPEN', 'RUNNING', 'FINISHED', 'PAID', 'CANCELED') DEFAULT 'OPEN',
                          version INT DEFAULT 1, -- 🌟 CỘT QUAN TRỌNG: Dùng để chống "Lost Update" khi 2 người bid cùng lúc
                          FOREIGN KEY (item_id) REFERENCES Items(item_id)
);

-- 5. Bảng Lịch sử trả giá
CREATE TABLE BidTransactions (
                                 bid_id INT AUTO_INCREMENT PRIMARY KEY,
                                 auction_id INT NOT NULL,
                                 bidder_id INT NOT NULL,
                                 bid_amount DECIMAL(15, 2) NOT NULL,
                                 bid_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                                 FOREIGN KEY (auction_id) REFERENCES Auctions(auction_id),
                                 FOREIGN KEY (bidder_id) REFERENCES Users(user_id)
);