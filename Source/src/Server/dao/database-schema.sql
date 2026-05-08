-- ============================================================================
-- BIDDING SYSTEM - MYSQL DATABASE SCHEMA
-- Database: hethongdaugia
-- Created for: Hệ Thống Đấu Giá Trực Tuyến (Online Bidding System)
-- ============================================================================

-- Create database
-- CREATE DATABASE IF NOT EXISTS hethongdaugia 
-- CHARACTER SET utf8mb4 
--COLLATE utf8mb4_unicode_ci;

-- USE hethongdaugia;

-- ============================================================================
-- Table 1: users - Quản lý người dùng (Bidder, Seller, Admin)
-- ============================================================================
CREATE TABLE IF NOT EXISTS users (
    username VARCHAR(50) PRIMARY KEY COMMENT 'Tên đăng nhập duy nhất',
    password VARCHAR(255) NOT NULL COMMENT 'Mật khẩu ',
    email VARCHAR(100) NOT NULL UNIQUE COMMENT 'Địa chỉ email duy nhất',
    role VARCHAR(20) NOT NULL COMMENT 'Vai trò: BIDDER, SELLER, ADMIN',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'Thời điểm tạo tài khoản',
    
    INDEX idx_role (role),
    INDEX idx_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Bảng quản lý người dùng hệ thống';

-- ============================================================================
-- Table 2: items - Quản lý sản phẩm đấu giá
-- ============================================================================
CREATE TABLE IF NOT EXISTS items (
    item_id VARCHAR(36) PRIMARY KEY COMMENT 'ID sản phẩm (UUID)',
    name VARCHAR(255) NOT NULL COMMENT 'Tên sản phẩm',
    starting_price FLOAT NOT NULL COMMENT 'Giá khởi điểm',
    item_type VARCHAR(50) NOT NULL COMMENT 'Loại sản phẩm: ELECTRONICS, ART, VEHICLE',
    description TEXT COMMENT 'Mô tả chi tiết sản phẩm',
    seller_username VARCHAR(50) COMMENT 'Người bán sở hữu sản phẩm',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'Thời điểm tạo sản phẩm',
    
    FOREIGN KEY (seller_username) REFERENCES users(username) ON DELETE SET NULL,
    INDEX idx_seller (seller_username),
    INDEX idx_item_type (item_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Bảng quản lý sản phẩm đấu giá';

-- ============================================================================
-- Table 3: auction_snapshots - Thông tin chính của phiên đấu giá
-- ============================================================================
CREATE TABLE IF NOT EXISTS auction_snapshots (
    auction_id INT PRIMARY KEY COMMENT 'ID phiên đấu giá',
    client_owner VARCHAR(50) NOT NULL COMMENT 'Username người tạo phiên',
    item_id VARCHAR(36) NOT NULL COMMENT 'ID sản phẩm được đấu giá',
    created_at DATETIME COMMENT 'Thời điểm tạo phiên',
    terminate_at DATETIME COMMENT 'Thời điểm kết thúc phiên',
    type VARCHAR(30) COMMENT 'Loại phiên: Time_Fixed, Time_With_Reset',
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT 'Trạng thái: OPEN, RUNNING, FINISHED, PAID, CANCELED',
    was_in_countdown BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Có đang ở giai đoạn countdown không',
    
    FOREIGN KEY (client_owner) REFERENCES users(username) ON DELETE CASCADE,
    FOREIGN KEY (item_id) REFERENCES items(item_id) ON DELETE CASCADE,
    INDEX idx_status (status),
    INDEX idx_client_owner (client_owner),
    INDEX idx_terminate_at (terminate_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Bảng snapshot chính của phiên đấu giá';

-- ============================================================================
-- Table 4: auction_bids - Lịch sử đặt giá (Bid)
-- ============================================================================
CREATE TABLE IF NOT EXISTS auction_bids (
    id INT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID bid tự động',
    auction_id INT NOT NULL COMMENT 'ID phiên đấu giá',
    bid_amount FLOAT NOT NULL COMMENT 'Số tiền đặt giá',
    bidder_username VARCHAR(50) COMMENT 'Username người đặt giá',
    created_at DATETIME COMMENT 'Thời điểm đặt giá',
    bid_order INT NOT NULL COMMENT 'Thứ tự bid (0 = cao nhất)',
    
    FOREIGN KEY (auction_id) REFERENCES auction_snapshots(auction_id) ON DELETE CASCADE,
    FOREIGN KEY (bidder_username) REFERENCES users(username) ON DELETE SET NULL,
    INDEX idx_auction_id (auction_id),
    INDEX idx_bid_order (bid_order),
    INDEX idx_bidder (bidder_username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Bảng lưu trữ lịch sử tất cả các lượt đặt giá';

-- ============================================================================
-- Table 5: auction_participants - Người tham gia phiên đấu giá
-- ============================================================================
CREATE TABLE IF NOT EXISTS auction_participants (
    auction_id INT NOT NULL COMMENT 'ID phiên đấu giá',
    username VARCHAR(50) NOT NULL COMMENT 'Username người tham gia',
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'Thời điểm tham gia',
    
    PRIMARY KEY (auction_id, username),
    FOREIGN KEY (auction_id) REFERENCES auction_snapshots(auction_id) ON DELETE CASCADE,
    FOREIGN KEY (username) REFERENCES users(username) ON DELETE CASCADE,
    INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Bảng danh sách người tham gia mỗi phiên đấu giá';

-- ============================================================================
-- Table 6: bid_transactions - Ghi nhận các giao dịch bid
-- ============================================================================
CREATE TABLE IF NOT EXISTS bid_transactions (
    transaction_id VARCHAR(36) PRIMARY KEY COMMENT 'ID giao dịch (UUID)',
    auction_id INT NOT NULL COMMENT 'ID phiên đấu giá',
    bid_amount FLOAT NOT NULL COMMENT 'Số tiền đặt giá',
    bid_created_at DATETIME NULL COMMENT 'Thời điểm đặt giá',
    bidder_username VARCHAR(50) NOT NULL COMMENT 'Username người đặt giá',
    timestamp DATETIME NOT NULL COMMENT 'Thời điểm ghi nhận giao dịch',
    successful BOOLEAN NOT NULL COMMENT 'Giao dịch có thành công không',
    
    INDEX idx_auction_id (auction_id),
    INDEX idx_bidder (bidder_username),
    INDEX idx_timestamp (timestamp),
    INDEX idx_successful (successful)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Bảng ghi nhận chi tiết tất cả các giao dịch bid';

-- ============================================================================
-- SAMPLE DATA - Dữ liệu mẫu để test (tùy chọn)
-- ============================================================================

-- Thêm người dùng mẫu
INSERT INTO users (username, password, email, role) VALUES 
('seller_john', 'pass123', 'john@example.com', 'SELLER'),
('seller_ann', 'pass456', 'ann@example.com', 'SELLER'),
('bidder_bob', 'pass789', 'bob@example.com', 'BIDDER'),
('bidder_alice', 'pass000', 'alice@example.com', 'BIDDER'),
('admin_root', 'admin123', 'admin@example.com', 'ADMIN')
ON DUPLICATE KEY UPDATE username=username;

-- Thêm sản phẩm mẫu
INSERT INTO items (item_id, name, starting_price, item_type, description, seller_username) VALUES 
('item-001', 'Gaming Laptop RTX 4090', 1500.0, 'ELECTRONICS', 'Laptop gaming cao cấp', 'seller_john'),
('item-002', 'Tranh sơn dầu cổ', 500.0, 'ART', 'Tranh sơn dầu thế kỷ 19', 'seller_ann'),
('item-003', 'Xe máy Honda Air Blade', 3000.0, 'VEHICLE', 'Xe máy 110cc, tình trạng tốt', 'seller_john')
ON DUPLICATE KEY UPDATE name=name;

