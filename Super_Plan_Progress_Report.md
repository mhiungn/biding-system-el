# Super Implementation Plan Progress Report

Current status: **Push updates via long-lived NetworkClient complete**.

Latest slice completed on 2026-05-25:

- Added long-lived authenticated push subscription through `NetworkClient`.
- Added server-side targeted push delivery for auction, notification, and wallet updates.
- Wired JavaFX push handlers for dashboard, bidding detail, My Bids, profile wallet state, and open notification popup.
- Kept short-lived `NetworkRequestClient` for request/response calls.
- Kept network disabled by default unless explicitly enabled.
- Verified:

```powershell
.\mvnw.cmd test
```

```text
Tests run: 201, Failures: 0, Errors: 0, Skipped: 0
```

Historical checkpoint before later implementation:

Stopped at: **Phase 12 - Loading Indicators**.

I had just finished the loading-indicator slice and had not started auction lifecycle implementation.

## Done So Far

### 1. Build Stabilization

- Fixed tests using removed `Bidder` / `Seller` / `Admin` classes.
- Project compiles and tests run.
- Network disabled by default.
- Server port now uses shared network config.
- Server no longer imports client dashboard DTO classes.

### 2. Shared DTO / Serialization Cleanup

- Moved dashboard DTOs into `CommonClasses.dto`.
- Added serializable DTOs for:
  - dashboard rows/page/stats
  - wallet
  - notifications
  - seller auction rows
  - profile stats
- Added serialization tests.

### 3. Wallet Backend + UI

- Added wallet tables/DAO/service.
- New users get default `100000`.
- Deposit works with validation and daily cap.
- Header quick info shows available balance.
- Profile shows wallet balance/available balance.
- Bidding now checks wallet availability and reserves/releases holds.

### 4. Bidding Service

- Added `BiddingApplicationService`.
- Local bid path and server bid path use shared service.
- Enforces:
  - auction exists
  - auction not ended
  - owner cannot bid
  - minimum increment
  - wallet affordability
  - previous hold release
  - new hold reserve
- Creates seller and outbid notifications.

### 5. Notifications

- Added notification DAO/service/DTO.
- Added notification message types and server handlers.
- Added notification popup UI.
- Notification badge wired on dashboard, bidding detail, My Bids, Sell Item.

### 6. Images

- Added `ImageStorageService`.
- Sell item image saving uses configurable upload directory.
- Dashboard cards load first image.
- Bidding detail loads main image + thumbnails.
- Image paths included in shared DTOs.

### 7. My Bids

- Increase Bid now opens exact auction detail.
- Added seller selling/sold table.
- Added `SellerAuctionRowDTO`.
- Added DAO/service/network route for seller-owned auction rows.

### 8. Profile

- Added `UserProfileStatsDTO`.
- Added `users.created_at` migration and member-since display.
- Replaced positional stat lookup with explicit `fx:id` labels.
- Real stats now include bids, wins, active participations, sold items, total spent.
- Email edit and password change are wired.
- Phone/location now explicitly show unsupported-schema message instead of silent dead buttons.

### 9. Search

- Added DB-backed auction name search.
- Added search message types/server route/client service.
- Added reusable header search popup.
- Wired search buttons on dashboard, bidding detail, My Bids, and Sell Item.

### 10. Loading Indicators

- Added reusable `LoadingOverlay`.
- Moved major blocking operations onto JavaFX `Task`:
  - dashboard page load
  - bidding detail load
  - place bid
  - My Bids load
  - profile data load
  - profile deposit
  - sell item submit
- Adjusted navigation so bidding detail load starts after the scene is attached.

## Certainly Worked

- Latest full verification passed:

```powershell
.\mvnw.cmd test
```

- Result:

```text
Tests run: 163, Failures: 0, Errors: 0, Skipped: 0
```

- Compile succeeded after loading indicators.
- Added tests passed for:
  - seller auction rows
  - sold count
  - search by auction item name
  - profile/user created date
  - DTO serialization
  - wallet/bidding/notification behavior

## Not Done Yet

- Full shared header component extraction.
- NavigationService extraction.
- Loading overlays may not cover every single small DB/network action, but the main blocking paths are covered.

## Possible Risk / Things That Might Error

- JavaFX UI behavior was compile-tested, not visually tested. FXML binding should compile, but popup/overlay positioning may need real UI QA.
- Loading tasks now call DAOs off the JavaFX thread. That is intended, but any DAO singleton/thread-safety issue would show only under real UI use.
- Profile `created_at` migration adds the column automatically, but existing users may get DB-default behavior depending on MySQL version/settings.
- Sell item submit now runs in a background task; image preview still runs on UI thread, which is fine for local previews but not heavily optimized.
- Push uses a simple in-memory connected-client registry. Server restart drops push subscriptions until clients reconnect.
- Per-auction viewer tracking is not implemented yet; auction update pushes are targeted to known participants and seller, with broadcast fallback only when no recipients are known.
- Long-lived `NetworkClient` reconnect is basic and needs visual QA under real network interruptions.

## Next Pickup Order

1. Extract shared header if that is still desired.
2. Extract navigation service if that is still desired.
3. Do manual JavaFX/network visual QA with the server running and network explicitly enabled.
