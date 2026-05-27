-- ============================================================================
-- Dummy dashboard seed: items -> auction_snapshots + sync times + sample bids
-- ============================================================================
-- Yêu cầu: MySQL 8.0+ (dùng ROW_NUMBER()).
-- Chạy sau khi đã có bảng users, items (và các cột current_highest_price,
-- auction_start_time, auction_end_time trên items).
--
-- Đổi USE ... sang đúng database của bạn (schema mẫu trong database-schema.sql
-- dùng tên khác — hãy chỉnh lại cho khớp).
--
-- Thống kê trên UI:
--   ACTIVE AUCTIONS = COUNT(*) auction_snapshots WHERE status IN ('OPEN','RUNNING')
--   ENDING TODAY     = ... AND DATE(terminate_at) = CURDATE()
--   TOTAL BIDS       = COUNT(*) từ auction_bids
-- ============================================================================

USE blbsc98ma5stojowrgcs;
SET SQL_SAFE_UPDATES = 0;

-- ---------------------------------------------------------------------------
-- 0) (Tuỳ chọn) Thêm cột nếu DB cũ chưa có — bỏ comment nếu cần
-- ---------------------------------------------------------------------------
-- ALTER TABLE items
--   ADD COLUMN IF NOT EXISTS current_highest_price FLOAT NULL AFTER starting_price,
--   ADD COLUMN IF NOT EXISTS auction_start_time DATETIME NULL AFTER description,
--   ADD COLUMN IF NOT EXISTS auction_end_time DATETIME NULL AFTER auction_start_time;
-- MySQL 8.0.12+ không có IF NOT EXISTS cho ADD COLUMN — chạy thủ công nếu lỗi.

-- ---------------------------------------------------------------------------
-- 1) Chuẩn hoá items: giá cao nhất + thời gian đấu giá (nếu đang NULL)
-- ---------------------------------------------------------------------------
UPDATE items
SET current_highest_price = COALESCE(NULLIF(current_highest_price, 0), starting_price)
WHERE current_highest_price IS NULL OR current_highest_price = 0;

UPDATE items
SET auction_start_time = COALESCE(auction_start_time, created_at, NOW())
WHERE auction_start_time IS NULL;

UPDATE items
SET auction_end_time = COALESCE(
        auction_end_time,
        DATE_ADD(COALESCE(auction_start_time, created_at, NOW()), INTERVAL 7 DAY)
    )
WHERE auction_end_time IS NULL;

-- Gán seller mặc định cho item chưa có chủ (cần ít nhất 1 user SELLER trong users)
UPDATE items i
SET seller_username = (
        SELECT u.username FROM users u WHERE u.role = 'SELLER' LIMIT 1
    )
WHERE (i.seller_username IS NULL OR i.seller_username = '')
  AND EXISTS (SELECT 1 FROM users u2 WHERE u2.role = 'SELLER');

-- ---------------------------------------------------------------------------
-- 2) Tạo phiên đấu giá cho mỗi item CHƯA có dòng trong auction_snapshots
--    created_at / terminate_at lấy từ auction_start_time / auction_end_time
--    của item (đã COALESCE ở bước 1).
-- ---------------------------------------------------------------------------
INSERT INTO auction_snapshots (
    auction_id,
    client_owner,
    item_id,
    created_at,
    terminate_at,
    type,
    status,
    was_in_countdown
)
SELECT
    base.next_id,
    base.seller_username,
    base.item_id,
    base.auction_start_time,
    base.auction_end_time,
    'Time_Fixed',
    'OPEN',
    FALSE
FROM (
    SELECT
        i.item_id,
        i.seller_username,
        i.auction_start_time,
        i.auction_end_time,
        (SELECT IFNULL(MAX(s0.auction_id), 0) FROM auction_snapshots s0)
            + ROW_NUMBER() OVER (ORDER BY i.item_id) AS next_id
    FROM items i
    WHERE NOT EXISTS (
            SELECT 1 FROM auction_snapshots s WHERE s.item_id = i.item_id
        )
      AND i.seller_username IS NOT NULL
      AND EXISTS (
            SELECT 1 FROM users u WHERE u.username = i.seller_username
        )
) AS base;

-- ---------------------------------------------------------------------------
-- 3) Đồng bộ hai chiều: snapshot lấy từ item (ưu tiên thời gian trên item)
--    Sau bước này created_at / terminate_at khớp auction_start/end của item.
-- ---------------------------------------------------------------------------
UPDATE auction_snapshots s
JOIN items i ON i.item_id = s.item_id
SET
    s.created_at    = COALESCE(i.auction_start_time, s.created_at, i.created_at, NOW()),
    s.terminate_at  = COALESCE(
        i.auction_end_time,
        s.terminate_at,
        DATE_ADD(COALESCE(i.auction_start_time, s.created_at, NOW()), INTERVAL 7 DAY)
    )
WHERE s.status IN ('OPEN', 'RUNNING');

UPDATE items i
JOIN auction_snapshots s ON s.item_id = i.item_id
SET
    i.auction_start_time = s.created_at,
    i.auction_end_time   = s.terminate_at
WHERE s.status IN ('OPEN', 'RUNNING');

-- ---------------------------------------------------------------------------
-- 4) (Tuỳ chọn) Vài phiên kết thúc “hôm nay” để test ENDING TODAY trên UI
-- ---------------------------------------------------------------------------
UPDATE auction_snapshots s
JOIN (
    SELECT auction_id
    FROM auction_snapshots
    WHERE status IN ('OPEN', 'RUNNING')
    ORDER BY auction_id
    LIMIT 3
) t ON t.auction_id = s.auction_id
SET s.terminate_at = TIMESTAMP(CURRENT_DATE, '23:59:59');

UPDATE items i
JOIN auction_snapshots s ON s.item_id = i.item_id
SET i.auction_end_time = s.terminate_at
WHERE s.status IN ('OPEN', 'RUNNING');

-- ---------------------------------------------------------------------------
-- 5) (Tuỳ chọn) Vài bid mẫu vào auction_bids — TOTAL BIDS đếm từ bảng này
--    Cần ít nhất 1 user có username khác client_owner của phiên.
-- ---------------------------------------------------------------------------
INSERT INTO auction_bids (auction_id, bid_amount, bidder_username, created_at, bid_order)
SELECT
    s.auction_id,
    i.starting_price * 1.08,
    (SELECT u.username FROM users u WHERE u.username <> s.client_owner LIMIT 1),
    NOW(),
    0
FROM auction_snapshots s
JOIN items i ON i.item_id = s.item_id
WHERE s.status IN ('OPEN', 'RUNNING')
  AND NOT EXISTS (SELECT 1 FROM auction_bids b WHERE b.auction_id = s.auction_id)
  AND EXISTS (SELECT 1 FROM users u WHERE u.username <> s.client_owner);

UPDATE items i
JOIN auction_snapshots s ON s.item_id = i.item_id
JOIN (SELECT auction_id, MAX(bid_amount) AS mx FROM auction_bids GROUP BY auction_id) b
  ON b.auction_id = s.auction_id
SET i.current_highest_price = GREATEST(i.starting_price, b.mx);


SET SQL_SAFE_UPDATES = 1;

-- ---------------------------------------------------------------------------
-- Kiểm tra nhanh
-- ---------------------------------------------------------------------------
-- SELECT COUNT(*) AS active_auctions FROM auction_snapshots WHERE status IN ('OPEN','RUNNING');
-- SELECT COUNT(*) AS ending_today FROM auction_snapshots WHERE status IN ('OPEN','RUNNING') AND DATE(terminate_at) = CURDATE();
-- SELECT COUNT(*) AS total_bids FROM auction_bids;
-- SELECT s.auction_id, s.item_id, s.created_at, s.terminate_at, i.auction_start_time, i.auction_end_time
-- FROM auction_snapshots s JOIN items i ON i.item_id = s.item_id LIMIT 20;
