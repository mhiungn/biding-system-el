# Auction Finalization Lifecycle Report

## Scope Completed

Implemented the backend-only auction finalization slice from the super implementation plan.

This slice was intentionally limited to:

- Finding expired `OPEN` / `RUNNING` auctions.
- Marking expired auctions as `FINISHED`.
- Converting the winner wallet hold to spent when an auction has bids.
- Handling expired auctions with no bids.
- Creating seller, winner, and losing-bidder notifications.
- Adding focused tests.

This slice did **not** implement transaction-safe bidding, network session/token cleanup, shared header extraction, navigation service extraction, or live push updates.

## Files Changed

### `Source/src/Server/dao/AuctionDAO.java`

Added:

```java
public List<AuctionSnapshot> findExpiredOpenRunningAuctions()
```

Behavior:

- Selects auctions where:
  - `status IN ('OPEN', 'RUNNING')`
  - `terminate_at IS NOT NULL`
  - `terminate_at <= NOW()`
- Orders by `terminate_at ASC, auction_id ASC`.
- Loads full snapshot data:
  - item via `ItemDAO`
  - bid list
  - participants

Purpose:

- Provides the database-backed source for auctions that need lifecycle finalization.

### `Source/src/Server/service/AuctionFinalizationService.java`

New service added.

Main method:

```java
public int finalizeEndedAuctions()
```

Behavior:

- Loads expired open/running auctions from `AuctionDAO`.
- For each expired auction:
  - marks it `FINISHED`
  - if no bids:
    - sends seller no-bid notification
  - if bids exist:
    - treats first bid in bid list as winner
    - converts winner wallet hold to spent
    - sends seller sold notification
    - sends winner won notification
    - sends losing bidders lost notification
- Returns number of auctions finalized.

Important details:

- Uses existing `WalletApplicationService.finalizeWinningPayment(...)`.
- Uses existing bid ordering where `bid_order = 0` is the highest/current winning bid.
- Uses a `LinkedHashSet` to notify each losing bidder once.
- Keeps implementation non-transactional, as requested.

### `Source/src/Server/service/NotificationApplicationService.java`

Added notification helpers:

```java
public void notifyAuctionEndedNoBids(String sellerUsername, int auctionId, String itemName)
public void notifyAuctionLost(String bidderUsername, int auctionId, String itemName)
```

Existing helpers reused:

```java
notifyAuctionSold(...)
notifyAuctionWon(...)
```

Notification types now used for finalization:

- `AUCTION_SOLD`
- `AUCTION_WON`
- `AUCTION_LOST`
- `AUCTION_ENDED_NO_BIDS`

Action target:

- finalization result notifications use `MY_BIDS`.

### `src/test/java/Server/service/AuctionFinalizationServiceTest.java`

New focused test class added.

Tests added:

1. `expiredAuctionWithWinnerIsFinishedAndWinnerHoldBecomesSpent`
   - Creates future auction.
   - Places two bids through `BiddingApplicationService`.
   - Moves auction end time into the past.
   - Runs finalizer.
   - Verifies:
     - auction status becomes `FINISHED`
     - winner balance decreases
     - winner hold becomes `0`
     - winner total spent increases
     - seller receives `AUCTION_SOLD`
     - winner receives `AUCTION_WON`

2. `expiredAuctionWithNoBidsIsFinishedAndSellerIsNotified`
   - Creates expired auction with no bids.
   - Runs finalizer.
   - Verifies:
     - auction status becomes `FINISHED`
     - seller receives `AUCTION_ENDED_NO_BIDS`

3. `losingBidderReceivesAuctionLostNotification`
   - Creates auction with two bidders.
   - Finalizes after expiry.
   - Verifies losing bidder receives `AUCTION_LOST`.

## Verification

Command run:

```powershell
.\mvnw.cmd test
```

Result:

```text
Tests run: 166, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## What Certainly Works

- Expired DB auctions can be found by `AuctionDAO`.
- Expired `OPEN` / `RUNNING` auctions are marked `FINISHED`.
- Winner wallet hold is converted to spent when bids were placed through `BiddingApplicationService`.
- Seller receives sold notification when auction ends with a winner.
- Winner receives won notification.
- Losing bidders receive lost notification.
- Seller receives no-bid notification when auction ends with no bids.
- Focused H2-backed service tests pass.
- Full Maven test suite passes.

## Remaining Risks

- Finalization is not transactional yet.
  - If wallet conversion or notification creation fails after status update, data can become partially finalized.
  - This was intentionally left for the later transaction-safety slice.

- Finalizer is not yet invoked automatically.
  - It is implemented and tested, but not wired into:
    - server startup
    - periodic scheduler
    - dashboard load
    - bidding detail load

- Legacy bids created outside `BiddingApplicationService` may not have wallet holds.
  - In that case, `finalizeWinningPayment` may still reduce balance, but there may be no hold to release.

- No live network push was added.
  - Notifications are persisted in DB.
  - Connected clients will not receive real-time finalization pushes yet.

- Finalizer uses current bid ordering assumption.
  - It assumes `auction_bids.bid_order = 0` / first loaded bid is the winner.
  - This matches current `AuctionDAO.addBid` behavior.

## Recommended Next Steps

1. Wire `AuctionFinalizationService.finalizeEndedAuctions()` into server startup.
2. Add a periodic server-side scheduler, for example every 30-60 seconds.
3. Optionally call finalizer before dashboard and bidding-detail reads so stale expired auctions disappear quickly in local mode.
4. Add idempotency checks if finalizer is called frequently.
5. Implement transaction safety for finalization:
   - lock auction row
   - mark finished
   - convert wallet hold
   - create notifications
   - commit or rollback as one unit
6. Later add network push events for:
   - auction finished
   - wallet update
   - notification badge update

