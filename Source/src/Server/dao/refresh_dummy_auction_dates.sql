-- ============================================================================
-- Refresh dummy auction dates for active UI testing
-- ============================================================================
-- Use this when seed data has expired and the dashboard needs active auctions.
-- It keeps existing items, bids, and participants, but moves auction windows
-- into the future and syncs item auction_start_time / auction_end_time.
--
-- Adjust the database name if your local schema differs.

USE blbsc98ma5stojowrgcs;

START TRANSACTION;

UPDATE auction_snapshots
SET
    created_at = NOW(),
    terminate_at = DATE_ADD(NOW(), INTERVAL (5 + MOD(auction_id, 10)) DAY),
    status = 'OPEN',
    was_in_countdown = FALSE
WHERE auction_id IS NOT NULL;

UPDATE items i
JOIN auction_snapshots s ON s.item_id = i.item_id
SET
    i.auction_start_time = s.created_at,
    i.auction_end_time = s.terminate_at
WHERE s.auction_id IS NOT NULL;

COMMIT;

-- Verification:
-- SELECT
--     s.auction_id,
--     s.status,
--     s.created_at,
--     s.terminate_at,
--     TIMESTAMPDIFF(HOUR, NOW(), s.terminate_at) AS hours_remaining,
--     i.name
-- FROM auction_snapshots s
-- JOIN items i ON i.item_id = s.item_id
-- ORDER BY s.auction_id;
