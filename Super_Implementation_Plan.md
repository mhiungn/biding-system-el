# Super Implementation Plan

## Project Context

This plan is for the Java/Maven JavaFX Online Auction System project after the recent network merge.

The project currently has:

- JavaFX desktop UI.
- Maven build.
- MySQL persistence through JDBC DAOs.
- Socket-based network layer.
- Login/signup screens.
- Dashboard screen.
- Sell item screen.
- Bidding detail screen.
- My Bids screen.
- Profile screen.
- Some auction domain logic.
- Some database-backed auction logic.
- Some in-memory `AuctionManager` logic.
- Some short-lived network request logic.
- Some long-lived network client logic.

The system is not yet cleanly runnable because the merge created several separated parts that are not fully connected.

The first priority is **not** to add more features immediately. The first priority is to make the project compile, run, and use one clear architecture.

---

# Main Development Rule

Before adding any new feature, Codex must first stabilize the project.

Do not implement wallet, notifications, search, profile fixes, images, and loading indicators as isolated patches.

Every new feature must be linked through the existing project structure:

```text
JavaFX Controller
        ↓
Client Feature Service
        ↓
NetworkRequestClient if enabled and available
        ↓
Server ClientHandler
        ↓
Server-side Application Service
        ↓
DAO
        ↓
MySQL database
```

Fallback local mode:

```text
JavaFX Controller
        ↓
Client Feature Service
        ↓
Local DAO fallback
        ↓
MySQL database
```

The **database must be the single source of truth**.

Do not make new real auction state inside:

- controllers;
- temporary JavaFX objects;
- `AuctionManager`;
- network packet-only state;
- duplicated static lists.

`AuctionManager` can stay for domain tests or legacy code, but it must not control real auctions shown in the UI.

---

# Critical Existing Problems To Resolve First

## 1. Tests do not compile

The test suite currently references removed/missing classes:

- `Bidder`
- `Seller`
- `Admin`

The current app model appears to use one generic `User` class with role strings instead.

Affected files likely include:

```text
src/test/java/Server/dao/UserDAOTest.java
src/test/java/Server/dao/AuctionDAOTest.java
```

### Codex tasks

1. Open all tests that import or instantiate `Bidder`, `Seller`, or `Admin`.
2. Replace those with `User`.
3. Use the same role values that the current app uses.
4. Do not restore old `Bidder`, `Seller`, and `Admin` subtype classes unless the current production code still truly needs them.
5. Run:

```bash
mvnw.cmd test
```

### Acceptance

```text
[ ] mvnw.cmd compile passes.
[ ] mvnw.cmd test reaches test execution instead of failing test compilation.
[ ] Tests use the current User role model.
```

---

## 2. Network response serialization is broken

`DashboardAuctionRow` is sent through object streams but does not implement `Serializable`.

This can break:

- dashboard page network response;
- auction detail network response;
- my active bids network response;
- my completed bids network response.

Expected failures if not fixed:

- `ClassCastException`;
- `NotSerializableException`.

### Codex tasks

1. Find `DashboardAuctionRow`.
2. Add:

```java
implements Serializable
```

3. Add:

```java
private static final long serialVersionUID = 1L;
```

4. Check every field inside it.
5. Make nested DTOs serializable if needed.
6. Also inspect:

```text
DashboardPageResult
DashboardStats
Auction detail DTOs
Bid history DTOs
My bids DTOs
Notification DTOs
Wallet DTOs
Search result DTOs
```

### Acceptance

```text
[ ] Dashboard page response serializes successfully.
[ ] Auction detail response serializes successfully.
[ ] My bids responses serialize successfully.
[ ] No server-side object stream failure for dashboard rows.
```

---

## 3. Network mode is enabled by default and causes slowness

`NetworkConfig.networkEnabled()` currently defaults to true.

That means screens try socket requests first. If the server is not running, the UI waits for network timeout and then falls back to DAO.

This makes screens feel slow or unreliable.

### Codex tasks

1. Change network default to false.
2. Only enable network if this property is explicitly true:

```bash
-Dauction.network.enabled=true
```

3. Reduce socket timeout to a small value such as 800–1500 ms.
4. Make network failure logs clear but not spammy.

### Acceptance

```text
[ ] App starts without socket server.
[ ] Dashboard/profile/detail pages do not wait for slow socket timeout by default.
[ ] Network can still be enabled manually.
```

---

## 4. Server port is hardcoded

Client reads host/port from:

```text
auction.server.host
auction.server.port
```

But `Server.main` still uses hardcoded port `12345`.

### Codex tasks

1. Update `Server.Server` to use the same config.
2. Default should remain `12345`.
3. Print actual port when server starts.

### Acceptance

```text
[ ] Changing -Dauction.server.port changes server port.
[ ] Client and server can be configured consistently.
```

---

## 5. Server imports client UI classes

`ClientHandler` imports classes from `Client.features.dashboard`.

That makes the server depend on client UI feature packages.

### Codex tasks

1. Create shared DTO package, for example:

```text
CommonClasses/dto/
```

or:

```text
Shared/dto/
```

2. Move shared network DTOs there:

```text
DashboardAuctionRow
DashboardPageResult
DashboardStats
AuctionDetailDTO
BidHistoryDTO
MyBidRowDTO
WalletDTO
NotificationDTO
SearchResultDTO
SellerAuctionRowDTO
```

3. Make all DTOs implement `Serializable`.
4. Update imports in both client and server.
5. Remove server imports from `Client.features.*`.

### Acceptance

```text
[ ] Server no longer imports Client.features.* DTO classes.
[ ] Shared DTO package is used by both client and server.
```

---

## 6. Network bidding trusts username from payload

The network database bid path accepts a username inside the payload.

This is unsafe because a modified client could claim another username.

### Codex tasks

1. Do not trust `username` from bid payload.
2. Server must derive bidder identity from authenticated session.
3. `PLACE_BID` must require login.
4. If using short-lived `NetworkRequestClient`, either:
   - use a proper session token; or
   - keep local DAO fallback for prototype mode.
5. At minimum, do not let arbitrary username decide bidder.

### Acceptance

```text
[ ] Client cannot place bid as another user by modifying payload.
[ ] Server-side place bid receives authenticated user ID.
```

---

## 7. Network bidding rules do not match DAO bidding rules

DAO fallback checks:

- auction exists;
- auction has not ended;
- amount is positive;
- minimum bid increment;
- auto-extend;
- other richer validation.

Network DB bidding checks only simpler conditions.

### Codex tasks

Create one authoritative method:

```java
BiddingApplicationService.placeBid(userId, auctionId, amount)
```

All paths must call it:

- local DAO fallback;
- network `PLACE_BID`;
- detail page increase bid;
- any future quick bid button.

This method enforces:

```text
auction exists
auction is not ended
amount is positive
amount >= current price + minimum increment
owner cannot bid on own auction
user has enough available wallet balance
previous highest bidder hold is released
new highest bidder hold is reserved
bid row is inserted
auction current price/highest bidder is updated
auto-extend applies if existing system supports it
notifications are created
auction update is broadcast if network push is active
```

### Acceptance

```text
[ ] All bid paths use one service method.
[ ] Network bid and DAO bid behave the same.
```

---

# Chosen Architecture

## Keep for now

```text
NetworkRequestClient
```

Use for normal request/response actions:

- login;
- register;
- dashboard page;
- auction detail;
- search auctions;
- place bid;
- deposit;
- get wallet balance;
- get notifications;
- mark notification read;
- my active bids;
- my completed bids;
- my selling/sold items.

```text
NetworkClient
```

Use only for live push updates:

- new bid update;
- outbid notification;
- auction finished notification;
- wallet/balance refresh;
- unread notification badge refresh.

## Do not do this

Do not randomly use both network clients for the same operation.

Do not let one page use direct DAO while another page uses in-memory `AuctionManager` for the same auction.

Do not add new logic directly in controllers.

---

# New/Updated Service Layer

Create or clean these classes:

```text
Server/service/AuthApplicationService.java
Server/service/AuctionApplicationService.java
Server/service/BiddingApplicationService.java
Server/service/WalletApplicationService.java
Server/service/NotificationApplicationService.java
Server/service/ImageStorageService.java
Server/service/AuctionFinalizationService.java
```

## Responsibilities

| Service | Responsibility |
|---|---|
| `AuthApplicationService` | login, register, user lookup, current user session support |
| `AuctionApplicationService` | dashboard, detail, search, seller items, status updates |
| `BiddingApplicationService` | place bid, validate bid, update current price, call wallet/notification logic |
| `WalletApplicationService` | balance, available balance, deposit, daily cap, reserve/release/convert holds |
| `NotificationApplicationService` | create, list, count unread, mark read, mark all read |
| `ImageStorageService` | save images outside repo, validate file type, return usable paths |
| `AuctionFinalizationService` | mark ended auctions finished, choose winner, convert money, notify users |

---

# Phase 1 — Wallet, Deposit, Money Spent, Bid Restriction

## Required behavior

1. Each new account receives:

```text
100000
```

default balance.

2. Profile page has a deposit/get-more-money box.

3. User inserts a number and balance increases.

4. Daily deposit limit:

```text
less than 10,000,000 per day
```

5. User cannot bid higher than available wallet amount.

6. Current user quick info on every page should display:

```text
Name | Balance: 100,000
```

or better:

```text
Name | Available: 100,000
```

instead of:

```text
Name | Email
```

---

## 1.1 Database migration

Add wallet tables.

```sql
CREATE TABLE IF NOT EXISTS user_wallets (
    user_id BIGINT PRIMARY KEY,
    balance BIGINT NOT NULL DEFAULT 100000,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_wallet_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS wallet_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    amount BIGINT NOT NULL,
    auction_id BIGINT NULL,
    bid_id BIGINT NULL,
    note VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_wallet_tx_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS wallet_holds (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    auction_id BIGINT NOT NULL,
    amount BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_wallet_hold_user_auction (user_id, auction_id),
    CONSTRAINT fk_wallet_hold_user FOREIGN KEY (user_id) REFERENCES users(id)
);
```

For existing users:

```sql
INSERT INTO user_wallets (user_id, balance)
SELECT id, 100000
FROM users
WHERE id NOT IN (SELECT user_id FROM user_wallets);
```

Use `BIGINT` for money. Do not use `double`.

---

## 1.2 Wallet transaction types

Use these types:

```text
INITIAL_CREDIT
DEPOSIT
HOLD
HOLD_RELEASE
SPENT
REFUND
```

Meaning:

| Type | Meaning |
|---|---|
| `INITIAL_CREDIT` | default 100000 given to new user |
| `DEPOSIT` | user manually adds money |
| `HOLD` | money reserved for current winning bid |
| `HOLD_RELEASE` | reserved money released after outbid |
| `SPENT` | money finally spent after auction won |
| `REFUND` | future use if canceled/refunded |

---

## 1.3 WalletDAO

Create:

```text
Server/dao/WalletDAO.java
```

Methods:

```java
long getBalance(long userId);
long getHeldAmount(long userId);
long getAvailableBalance(long userId);
long getTodayDepositTotal(long userId);
long getHoldForAuction(long userId, long auctionId);

void createWalletIfMissing(long userId);
void deposit(long userId, long amount);
void addTransaction(long userId, String type, long amount, Long auctionId, Long bidId, String note);

void reserveHold(long userId, long auctionId, long amount);
void releaseHold(long userId, long auctionId);
void convertHoldToSpent(long userId, long auctionId, long finalAmount);

long getTotalSpent(long userId);
```

---

## 1.4 WalletApplicationService

Create:

```text
Server/service/WalletApplicationService.java
```

Methods:

```java
WalletDTO getWallet(long userId);
WalletDTO deposit(long userId, long amount);
boolean canAffordBid(long userId, long auctionId, long bidAmount);
void reserveBidAmount(long userId, long auctionId, long amount);
void releaseBidHold(long userId, long auctionId);
void finalizeWinningPayment(long userId, long auctionId, long amount);
```

Deposit validation:

```text
amount > 0
amount < 10,000,000
todayDepositTotal + amount < 10,000,000
```

If invalid, return clear error message.

Examples:

```text
Deposit amount must be positive.
Daily deposit limit is below 10,000,000.
You have already deposited 8,000,000 today. You can only add less than 2,000,000 more.
```

---

## 1.5 Registration wallet creation

In registration flow:

1. create user;
2. create wallet with `100000`;
3. insert wallet transaction:

```text
INITIAL_CREDIT, amount = 100000
```

Acceptance:

```text
[ ] New account has wallet row.
[ ] New account shows 100000 available money.
[ ] Existing users are migrated.
```

---

## 1.6 Bid wallet rule

When user places bid:

```text
availableBalance = balance - totalActiveHolds
```

User cannot bid if:

```text
bidAmount > availableBalance
```

Special case:

If the current user is already highest bidder on that same auction:

```text
availableForThisAuction = availableBalance + currentHoldOnThisAuction
```

Then allow increasing bid if:

```text
newBidAmount <= availableForThisAuction
```

This avoids blocking a user from raising their own bid because their old highest bid is already held.

---

## 1.7 Reserve/release flow

When a new highest bid succeeds:

1. Find previous highest bidder.
2. Release previous highest bidder hold for this auction.
3. Reserve new highest bidder hold for this auction.
4. Insert bid row.
5. Update auction current price.
6. Create notifications.

When auction ends:

1. Winner hold becomes `SPENT`.
2. Balance is reduced by final amount.
3. Hold row is removed.
4. Seller/winner/loser notifications are created.

---

## 1.8 Display balance on every page

Find shared header/current-user component.

Replace:

```text
Name | Email
```

with:

```text
Name | Available: 100,000
```

or:

```text
Name | Wallet: 100,000
```

Better variable/class names:

```text
currentUserQuickInfoLabel
currentUserWalletLabel
currentAvailableBalanceLabel
```

Do not name it `mailLabel` anymore if it displays money.

Update balance after:

- login;
- deposit;
- successful bid;
- outbid notification;
- auction finalization;
- page reload.

---

# Phase 2 — Notifications

## Required behavior

Notification button already exists next to profile button on every page.

When clicked, open a popup mini-window inside current scene, similar to Facebook notifications.

It should be:

- scrollable;
- small but readable;
- styled according to `DESIGN.md`;
- anchored near the notification button;
- non-blocking;
- able to open related pages;
- able to show unread state.

Notification cases:

| Event | Receiver | Click action |
|---|---|---|
| Seller item gets new bid | seller | Open bidding detail |
| Bidder is outbid | previous highest bidder | Open bidding detail |
| Auction ends with result | seller/winner/losers | Open My Bids |

---

## 2.1 Notification database table

```sql
CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    auction_id BIGINT NULL,
    type VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    action_target VARCHAR(64) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES users(id)
);
```

Notification types:

```text
NEW_BID_ON_SELLER_ITEM
OUTBID
AUCTION_SOLD
AUCTION_ENDED_NO_BIDS
AUCTION_WON
AUCTION_LOST
```

Action targets:

```text
AUCTION_DETAIL
MY_BIDS
```

---

## 2.2 NotificationDAO

Create:

```text
Server/dao/NotificationDAO.java
```

Methods:

```java
List<NotificationDTO> findRecentByUser(long userId, int limit);
int countUnread(long userId);
void markRead(long notificationId, long userId);
void markAllRead(long userId);
void createNotification(long userId, Long auctionId, String type, String title, String message, String actionTarget);
```

---

## 2.3 NotificationApplicationService

Create:

```text
Server/service/NotificationApplicationService.java
```

Methods:

```java
List<NotificationDTO> getRecentNotifications(long userId);
int getUnreadCount(long userId);
void markRead(long userId, long notificationId);
void markAllRead(long userId);

void notifySellerNewBid(long sellerId, long auctionId, String itemName, long amount);
void notifyBidderOutbid(long bidderId, long auctionId, String itemName, long newAmount);
void notifyAuctionResult(...);
```

---

## 2.4 Create notifications during bidding

Inside `BiddingApplicationService.placeBid(...)`:

### Seller notification

Create when someone places a valid bid:

```text
Title: New bid on your item
Message: Someone placed a bid of [amount] on [item name].
Action: AUCTION_DETAIL
```

Receiver:

```text
seller user id
```

### Outbid notification

Create when previous highest bidder exists and is not the new bidder:

```text
Title: You were outbid
Message: Someone placed a higher bid on [item name].
Action: AUCTION_DETAIL
```

Receiver:

```text
previous highest bidder user id
```

---

## 2.5 Create notifications when auction ends

When auction finalization runs:

### Seller, sold

```text
Title: Item sold
Message: Your item [item name] was sold for [amount].
Action: MY_BIDS
```

### Seller, no bids

```text
Title: Auction ended
Message: Your item [item name] ended without bids.
Action: MY_BIDS
```

### Winner

```text
Title: You won the auction
Message: You won [item name] for [amount].
Action: MY_BIDS
```

### Losers

```text
Title: Auction ended
Message: You did not win [item name].
Action: MY_BIDS
```

---

## 2.6 Notification popup UI

Create:

```text
Client/components/NotificationPopup.java
```

Behavior:

1. Click notification button.
2. Popup appears under/near the button.
3. Popup title:

```text
Notifications
```

4. Contains scrollable list.
5. Each notification item shows:
   - title;
   - message;
   - time;
   - unread visual state.
6. Has button:

```text
Mark all read
```

7. Clicking a notification:
   - marks it read;
   - updates unread badge;
   - routes to correct page.

---

## 2.7 Red unread badge

Add badge on notification button.

Behavior:

```text
unread count > 0 -> show red circle
unread count == 0 -> hide red circle
```

If exact count is easy, show number.

If hard, simple red dot is acceptable.

Acceptance:

```text
[ ] Notification popup opens from every page.
[ ] Seller gets new bid notification.
[ ] Previous bidder gets outbid notification.
[ ] Auction result notifications are created.
[ ] Clicking new bid/outbid opens bidding detail.
[ ] Clicking result notification opens My Bids.
[ ] Red unread dot/count disappears after read.
```

---

# Phase 3 — Images For Dashboard And Bidding Detail

## Current issue

User can upload photos when selling items, but images do not load on dashboard or bidding detail.

Also, dumping uploaded images into the repo folder is not a good long-term storage method.

---

## 3.1 Storage strategy

Use configurable external upload folder:

```text
-Dauction.upload.dir=...
```

Default:

```text
${user.home}/.auction-system/uploads
```

Do not store runtime uploads inside:

```text
src/
target/
repo root committed folder
```

Add to `.gitignore`:

```gitignore
uploads/
*.upload
```

Keep only placeholder images in resources.

---

## 3.2 ImageStorageService

Create:

```text
Server/service/ImageStorageService.java
```

Behavior:

1. Accept selected image file.
2. Validate extension:
   - `.jpg`
   - `.jpeg`
   - `.png`
   - `.webp`
3. Optionally validate size.
4. Generate UUID filename.
5. Copy to upload directory.
6. Store filename/path in `item_images`.
7. Return path usable by JavaFX `Image`.

---

## 3.3 Sell item image upload flow

In Sell Item controller/service:

1. User selects image.
2. UI previews image.
3. On submit:
   - save item;
   - save auction snapshot;
   - call `ImageStorageService`;
   - insert image metadata into `item_images`.

Do not copy image before item creation unless there is cleanup on failure.

---

## 3.4 Dashboard image loading

In dashboard card builder:

1. Get first image for item/auction.
2. If image exists, load with:

```java
new Image(file.toURI().toString(), true)
```

3. If image is missing/broken, use placeholder.
4. Do not crash page on image loading failure.

---

## 3.5 Bidding detail image loading

In bidding detail controller:

1. Load primary image.
2. Optionally support multiple images as thumbnails.
3. Use placeholder if missing.

Acceptance:

```text
[ ] Newly uploaded image appears on dashboard.
[ ] Newly uploaded image appears in bidding detail.
[ ] Missing image does not break UI.
[ ] Runtime uploads are outside committed source folders.
```

---

# Phase 4 — My Bids Controller Completion

## Required behavior

Complete controller in My Bids.

The “Increase Bid” action must open bidding detail of the exact auction.

---

## 4.1 Increase bid action

Find My Bids controller and active bids table row action.

Change action to:

```java
NavigationService.openBiddingDetail(auctionId);
```

Use exact auction ID from row DTO.

Do not create a new auction object manually.

Do not navigate using item name.

Do not use selected table index if row DTO already contains ID.

Acceptance:

```text
[ ] Increase Bid opens correct bidding detail page.
[ ] The opened detail page displays the same item/auction.
```

---

## 4.2 Add seller table to My Bids

Add one more table:

```text
My Selling / Sold Items
```

It should list items sold/selling that only the current user can see.

Columns:

```text
Item name
Current/final price
Status
Highest bidder/winner
End time
Action
```

Action behavior:

| Status | Action |
|---|---|
| OPEN/RUNNING | Open bidding detail |
| FINISHED/PAID | Open detail or result summary |
| CANCELED | Open detail or show canceled status |

---

## 4.3 DAO/service method

Add:

```java
List<SellerAuctionRowDTO> findSellerAuctions(long sellerUserId);
```

SQL should filter by current seller ID.

Never show other users’ selling items.

Acceptance:

```text
[ ] My Bids has active bids table.
[ ] My Bids has completed bids table.
[ ] My Bids has selling/sold items table.
[ ] Selling/sold table only shows current user’s items.
```

---

# Phase 5 — Profile Page Fixes

## Required behavior

1. Update profile user stats.
2. Show real count of bids.
3. Show real count of items selling/sold.
4. Replace fixed visual `Admin` with actual role.
5. Fix typo/logic for member since.
6. Get current user data.
7. Add function for every clickable button in profile.

---

## 5.1 UserProfileStatsDTO

Create shared DTO:

```java
public class UserProfileStatsDTO implements Serializable {
    private String displayName;
    private String role;
    private long walletBalance;
    private long availableBalance;
    private long totalSpent;
    private int bidCount;
    private int sellingItemCount;
    private int soldItemCount;
    private LocalDateTime createdAt;
}
```

---

## 5.2 Profile service method

Create:

```java
UserProfileStatsDTO getProfileStats(long userId);
```

Queries:

```sql
SELECT COUNT(*) FROM auction_bids WHERE bidder_id = ?;

SELECT COUNT(*) FROM auction_snapshots
WHERE seller_id = ?
AND status IN ('OPEN', 'RUNNING');

SELECT COUNT(*) FROM auction_snapshots
WHERE seller_id = ?
AND status IN ('FINISHED', 'PAID');

SELECT COALESCE(SUM(amount), 0)
FROM wallet_transactions
WHERE user_id = ?
AND type = 'SPENT';
```

Also get:

- user display name;
- user role;
- user created date;
- wallet balance;
- available balance.

---

## 5.3 Replace hardcoded role

Replace visual fixed text:

```text
Admin
```

with:

```text
Active User
```

or:

```text
Active Admin
```

based on actual role.

If role is unknown:

```text
Active Member
```

---

## 5.4 Fix member since

Correct typo:

```text
memer since
```

to:

```text
Member since
```

Use real `users.created_at`.

Examples:

```text
Member since May 2026
```

or:

```text
Member since 2026-05-24
```

---

## 5.5 Make profile buttons work

Check all buttons on profile page.

Implement:

| Button | Action |
|---|---|
| Deposit / Get more money | focus/open deposit input |
| My Bids | navigate to My Bids |
| Sell Item | navigate to Sell Item |
| Dashboard/Home | navigate to Dashboard |
| Edit Profile | open edit profile dialog or hide if not supported |
| Logout | clear current session and return login |
| Notification | open notification popup |
| Search | open shared search bar |

Do not leave dead clickable buttons.

If a feature is not ready, hide the button or show a clear “Coming soon” message.

Acceptance:

```text
[ ] Profile stats are real.
[ ] Role display is real.
[ ] Member since is real.
[ ] All profile buttons do something clear.
```

---

# Phase 6 — Search Bar From Header Search Button

## Required behavior

When user clicks the search button in the 3-button group near profile/notification, a search bar appears.

Search should find auctions where the name contains the typed word.

---

## 6.1 Shared search component

Create:

```text
Client/components/HeaderSearchBox.java
```

Behavior:

1. Click search icon.
2. Search field appears.
3. User types keyword.
4. Press Enter or debounce 300 ms.
5. Search results appear as small dropdown/list.
6. Clicking result opens bidding detail.

---

## 6.2 Search service method

Add:

```java
List<DashboardAuctionRow> searchAuctionsByName(String keyword, int limit);
```

---

## 6.3 SQL idea

```sql
SELECT ...
FROM auction_snapshots a
JOIN items i ON ...
WHERE LOWER(i.name) LIKE LOWER(CONCAT('%', ?, '%'))
ORDER BY a.end_time ASC
LIMIT ?;
```

Also search auction title if project has separate auction title:

```sql
OR LOWER(a.title) LIKE LOWER(CONCAT('%', ?, '%'))
```

---

## 6.4 Empty search behavior

If search box is empty:

- hide result dropdown; or
- restore normal dashboard results if on dashboard.

Do not search everything on each empty input.

Acceptance:

```text
[ ] Search button opens search box.
[ ] Searching "phone" finds "iPhone", "Samsung Phone", etc.
[ ] Clicking result opens correct bidding detail.
[ ] Search works from every page with shared header.
```

---

# Phase 7 — Loading Indicators

## Required behavior

The app is slow in several places. Add loading indicator on every page that waits for DB/network work.

Must be small but noticeable and follow `DESIGN.md`.

---

## 7.1 Reusable component

Create:

```text
Client/components/LoadingOverlay.java
```

Visual:

- small `ProgressIndicator`;
- optional “Loading...” text;
- corner or center overlay;
- not ugly/oversized;
- consistent with design.

---

## 7.2 Use JavaFX Task

Slow DB/network operations must not run directly on JavaFX UI thread.

Pattern:

```java
Task<T> task = new Task<>() {
    @Override
    protected T call() {
        return service.loadSomething();
    }
};

loadingOverlay.show();

task.setOnSucceeded(e -> {
    loadingOverlay.hide();
    render(task.getValue());
});

task.setOnFailed(e -> {
    loadingOverlay.hide();
    showError(task.getException());
});

new Thread(task).start();
```

Apply to:

```text
Dashboard load
Bidding detail load
My Bids load
Profile load
Sell item submit
Deposit
Search
Notifications popup load
Place bid
Image upload
```

Acceptance:

```text
[ ] UI does not freeze during loading.
[ ] Each slow page/action shows loading indicator.
[ ] Loading indicator hides after success/failure.
[ ] Errors are visible to user.
```

---

# Phase 8 — Network Integration

## Current network files

The project has:

```text
NetworkConfig
NetworkRequestClient
NetworkClient
PacketFactory
NetworkErrorPayload
expanded MessageType
ClientHandler routes
```

They are partially merged but not cleanly integrated.

---

## 8.1 Use request/response for normal actions

Use `NetworkRequestClient` for:

```text
LOGIN
REGISTER
DASHBOARD_PAGE
DASHBOARD_STATS
AUCTION_DETAIL
AUCTION_OWNER
BID_HISTORY
PARTICIPANT_COUNT
HIGHEST_BIDDER
MY_ACTIVE_BIDS
MY_COMPLETED_BIDS
MY_SELLING_ITEMS
USER_HIGHEST_BID
CREATE_AUCTION
JOIN_AUCTION
LEAVE_AUCTION
PLACE_BID
CANCEL_AUCTION
WALLET_BALANCE
WALLET_DEPOSIT
NOTIFICATION_LIST
NOTIFICATION_MARK_READ
SEARCH_AUCTIONS
```

---

## 8.2 Use long-lived client for push events only

Use `NetworkClient` for:

```text
AUCTION_UPDATE_PUSH
NOTIFICATION_PUSH
WALLET_UPDATE_PUSH
```

Example push events:

- another user placed bid;
- user was outbid;
- auction ended;
- notification badge count changed;
- wallet changed.

---

## 8.3 Add/verify message types

Add if missing:

```java
WALLET_BALANCE_REQUEST
WALLET_BALANCE_RESPONSE
WALLET_DEPOSIT_REQUEST
WALLET_DEPOSIT_RESPONSE

NOTIFICATION_LIST_REQUEST
NOTIFICATION_LIST_RESPONSE
NOTIFICATION_MARK_READ_REQUEST
NOTIFICATION_MARK_READ_RESPONSE
NOTIFICATION_MARK_ALL_READ_REQUEST
NOTIFICATION_MARK_ALL_READ_RESPONSE
NOTIFICATION_PUSH

SEARCH_AUCTIONS_REQUEST
SEARCH_AUCTIONS_RESPONSE

MY_SELLING_ITEMS_REQUEST
MY_SELLING_ITEMS_RESPONSE

AUCTION_UPDATE_PUSH
WALLET_UPDATE_PUSH
```

---

## 8.4 Make payloads serializable

Every network payload must implement `Serializable`.

Check:

```text
WalletDTO
NotificationDTO
SellerAuctionRowDTO
SearchAuctionRequest
SearchAuctionResponse
DepositRequest
DepositResponse
NotificationListResponse
```

Acceptance:

```text
[ ] Network request/response does not fail due to serialization.
[ ] Server routes call service methods, not UI classes.
```

---

# Phase 9 — Auction Ending Lifecycle

## Current issue

The UI countdown can show an auction ended, but the database may still keep status as `OPEN` or `RUNNING`.

There are timer task classes like:

```text
AuctionCountdownTask
AuctionTerminateTask
```

but database-backed flow is incomplete.

---

## 9.1 AuctionFinalizationService

Create:

```text
Server/service/AuctionFinalizationService.java
```

Core method:

```java
void finalizeEndedAuctions();
```

Find auctions:

```sql
SELECT *
FROM auction_snapshots
WHERE end_time <= NOW()
AND status IN ('OPEN', 'RUNNING');
```

For each auction:

1. find highest bid;
2. if no bids:
   - mark auction `FINISHED`;
   - notify seller no bids;
3. if has winner:
   - mark auction `FINISHED` or `PAID` depending current design;
   - convert winner hold to spent;
   - notify seller sold;
   - notify winner won;
   - notify losing participants lost;
4. broadcast push update if network push is active.

---

## 9.2 Run finalizer

Run finalizer:

- on server startup;
- periodically, for example every 30–60 seconds;
- when auction detail page loads;
- before dashboard query if status matters.

Do not rely only on JavaFX countdown.

Acceptance:

```text
[ ] Ended auctions become FINISHED in DB.
[ ] Winner payment is finalized.
[ ] Seller/winner/loser notifications are created.
[ ] Dashboard no longer treats expired auctions as active.
```

---

# Phase 10 — Database Transaction Safety

Bidding and wallet updates must be transactional.

## Required transaction for place bid

Inside one DB transaction:

1. lock auction row;
2. read current highest bid;
3. validate minimum increment;
4. validate owner cannot bid;
5. validate wallet available balance;
6. release previous highest bidder hold;
7. reserve new bidder hold;
8. insert bid;
9. update auction current price/highest bidder;
10. create notifications;
11. commit.

If any step fails, rollback.

### Example SQL locking idea

```sql
SELECT *
FROM auction_snapshots
WHERE id = ?
FOR UPDATE;
```

Use equivalent table/column names from the actual project.

Acceptance:

```text
[ ] Two users bidding at same time cannot corrupt current price.
[ ] Wallet holds cannot become inconsistent after failed bid.
```

---

# Phase 11 — Header Integration On Every Page

Several required features live in the header:

- current user quick info;
- notification button;
- search button;
- profile button.

Create or clean one shared header component.

Possible class:

```text
Client/components/AppHeader.java
```

It should contain:

```text
Search button
Notification button
Profile button
Current user quick info label
```

Every page should use the same component instead of duplicating header code.

Pages:

```text
Dashboard
Bidding Detail
Sell Item
My Bids
Profile
```

Acceptance:

```text
[ ] Header layout is consistent on every page.
[ ] Balance appears everywhere.
[ ] Notification popup works everywhere.
[ ] Search works everywhere.
[ ] Profile button works everywhere.
```

---

# Phase 12 — Navigation Service

Create or clean:

```text
Client/navigation/NavigationService.java
```

Methods:

```java
void openDashboard();
void openBiddingDetail(long auctionId);
void openMyBids();
void openSellItem();
void openProfile();
void openLogin();
```

Do not manually load pages in many controllers with duplicated code.

All notification/search/table actions should use this service.

Acceptance:

```text
[ ] Notification click opens pages through NavigationService.
[ ] Search result opens detail through NavigationService.
[ ] Increase Bid opens detail through NavigationService.
```

---

# Phase 13 — Feature-by-Feature Implementation Order

Use this exact order to avoid breaking the project.

## Step 1 — Build stabilization

```text
[ ] Fix tests using removed Bidder/Seller/Admin classes.
[ ] Fix serializable network DTOs.
[ ] Disable network-by-default.
[ ] Make server port configurable.
[ ] Confirm mvnw.cmd compile.
[ ] Confirm mvnw.cmd test compiles.
```

## Step 2 — Architecture cleanup

```text
[ ] Create shared DTO package.
[ ] Move DTOs out of Client.features.
[ ] Add application service layer.
[ ] Stop real UI routes from using AuctionManager.
[ ] Make DB the single source of truth.
```

## Step 3 — Wallet backend

```text
[ ] Add wallet tables.
[ ] Add WalletDAO.
[ ] Add WalletApplicationService.
[ ] Create wallet on register.
[ ] Migrate existing users.
[ ] Add wallet DTOs.
```

## Step 4 — Wallet UI

```text
[ ] Add profile deposit box.
[ ] Display wallet/available balance on profile.
[ ] Display balance in header on every page.
[ ] Refresh balance after deposit and bid.
```

## Step 5 — Bid restriction

```text
[ ] Route all bid actions to BiddingApplicationService.
[ ] Enforce available balance.
[ ] Enforce owner cannot bid.
[ ] Enforce minimum increment.
[ ] Add wallet hold/release.
[ ] Add DB transaction.
```

## Step 6 — Notifications backend

```text
[ ] Add notifications table.
[ ] Add NotificationDAO.
[ ] Add NotificationApplicationService.
[ ] Create seller new bid notification.
[ ] Create outbid notification.
[ ] Create auction result notifications.
```

## Step 7 — Notifications UI

```text
[ ] Add NotificationPopup.
[ ] Add unread red badge/dot.
[ ] Mark notification read.
[ ] Click notification opens correct page.
```

## Step 8 — Images

```text
[ ] Add configurable upload directory.
[ ] Add ImageStorageService.
[ ] Save image path on sell item.
[ ] Load dashboard images.
[ ] Load bidding detail images.
[ ] Add placeholder fallback.
```

## Step 9 — My Bids

```text
[ ] Fix Increase Bid button to open exact auction detail.
[ ] Add seller selling/sold table.
[ ] Add service/DAO method for seller auctions.
```

## Step 10 — Profile

```text
[ ] Add UserProfileStatsDTO.
[ ] Load real user profile stats.
[ ] Replace hardcoded Admin.
[ ] Fix Member since.
[ ] Wire all profile buttons.
```

## Step 11 — Search

```text
[ ] Add shared header search box.
[ ] Add search DAO/service.
[ ] Show search dropdown/list.
[ ] Open detail on result click.
```

## Step 12 — Loading indicators

```text
[ ] Add LoadingOverlay.
[ ] Use JavaFX Task on slow operations.
[ ] Add loading indicator to all pages/actions.
```

## Step 13 — Auction lifecycle

```text
[ ] Add AuctionFinalizationService.
[ ] Finalize ended auctions.
[ ] Convert winner hold to spent.
[ ] Notify seller/winner/losers.
[ ] Refresh dashboard/detail statuses.
```

---

# File-Specific Edit Guide

Codex should inspect exact paths, but likely files/classes include:

## Build/tests

```text
src/test/java/Server/dao/UserDAOTest.java
src/test/java/Server/dao/AuctionDAOTest.java
```

## Network

```text
NetworkConfig
NetworkRequestClient
NetworkClient
PacketFactory
NetworkErrorPayload
MessageType
ClientHandler
Server.Server
```

## DAO

```text
UserDAO
ItemDAO
AuctionDAO
BidTransactionDAO
```

Add:

```text
WalletDAO
NotificationDAO
```

## Client screens/controllers

```text
Dashboard controller
Bidding detail controller
Sell item controller
My Bids controller
Profile controller
Login/signup controllers
Shared header component if exists
```

Add or clean:

```text
AppHeader
NotificationPopup
HeaderSearchBox
LoadingOverlay
NavigationService
```

## Server services

Add:

```text
AuthApplicationService
AuctionApplicationService
BiddingApplicationService
WalletApplicationService
NotificationApplicationService
ImageStorageService
AuctionFinalizationService
```

---

# Warnings For Future Partial Fixes

## Do not patch only the UI

Example of bad fix:

```text
Only check balance in BiddingDetailController.
```

Why bad:

A user could still bid through another path.

Correct fix:

```text
Check balance in BiddingApplicationService.
```

---

## Do not patch only local DAO path

Example of bad fix:

```text
Deposit works locally but not through network route.
```

Correct fix:

```text
Both local fallback and network route call WalletApplicationService.
```

---

## Do not store images in source folders

Example of bad fix:

```text
Copy images to src/main/resources/uploads.
```

Why bad:

- source folder becomes dirty;
- build may not update runtime files;
- repo gets huge;
- paths break after packaging.

Correct fix:

```text
Use configurable external upload directory.
```

---

## Do not create duplicate notification state in JavaFX

Example of bad fix:

```text
NotificationPopup stores notifications only in memory.
```

Correct fix:

```text
Notifications are stored in DB and loaded by NotificationDAO.
```

---

## Do not keep auction state in multiple worlds

Example of bad fix:

```text
Dashboard uses DB but socket bid uses AuctionManager.
```

Correct fix:

```text
Both use AuctionApplicationService/BiddingApplicationService backed by DAO.
```

---

## Do not trust username from client payload

Example of bad fix:

```text
PlaceBidRequest(username, auctionId, amount)
```

Correct fix:

```text
PlaceBidRequest(auctionId, amount)
```

Server derives user from authenticated session.

If short-lived request client has no session, add token/session support or keep prototype local DAO mode.

---

## Do not block JavaFX UI thread

Example of bad fix:

```text
controller.initialize() directly calls DAO and waits.
```

Correct fix:

```text
Use JavaFX Task and LoadingOverlay.
```

---

# Final Acceptance Checklist

Codex should finish when all items below are true.

## Build and architecture

```text
[ ] mvnw.cmd compile passes.
[ ] mvnw.cmd test compiles.
[ ] App can run without socket server when network is disabled.
[ ] Server and client use same configurable port.
[ ] DashboardAuctionRow and all network DTOs are Serializable.
[ ] Server no longer imports Client.features DTOs.
[ ] Database is the single source of truth for visible auctions.
[ ] Real UI auction routes no longer depend on in-memory AuctionManager.
```

## Wallet and bid rules

```text
[ ] User wallet is created with 100000 default.
[ ] Existing users receive wallet row after migration.
[ ] Deposit works.
[ ] Deposit rejects invalid/negative amount.
[ ] Deposit daily cap below 10,000,000 is enforced.
[ ] Bid fails when available balance is insufficient.
[ ] Owner cannot bid on own item.
[ ] Minimum bid increment is enforced.
[ ] Previous highest bidder hold is released.
[ ] New highest bidder hold is reserved.
[ ] Winner hold becomes spent when auction ends.
[ ] Balance appears in current user quick info on every page.
```

## Notifications

```text
[ ] Notification popup opens from header button.
[ ] Notification popup is scrollable.
[ ] Unread notification badge appears.
[ ] Unread notification badge disappears after read.
[ ] Seller receives new bid notification.
[ ] Previous bidder receives outbid notification.
[ ] Seller receives auction sold/no-bid result.
[ ] Winner receives win notification.
[ ] Losing bidders receive loss notification.
[ ] New bid/outbid notification opens bidding detail.
[ ] Result notification opens My Bids.
```

## Images

```text
[ ] User can upload image while selling item.
[ ] Image path is saved in DB.
[ ] Uploaded files are stored outside repo or in configurable upload dir.
[ ] Dashboard item images load.
[ ] Bidding detail images load.
[ ] Placeholder appears if image missing.
```

## My Bids

```text
[ ] My Bids active table loads real current user bids.
[ ] My Bids completed table loads real completed bids.
[ ] Increase Bid opens exact auction detail.
[ ] My Bids has selling/sold items table.
[ ] Selling/sold table only shows current user's items.
```

## Profile

```text
[ ] Profile stats use real current user data.
[ ] Bid count is real.
[ ] Selling item count is real.
[ ] Sold item count is real.
[ ] Total spent is real.
[ ] Role is not hardcoded as Admin.
[ ] Role displays Active User or Active Admin.
[ ] Member since uses real account created date.
[ ] All clickable profile buttons have behavior.
```

## Search

```text
[ ] Search button opens search bar.
[ ] Search finds auctions by name containing typed word.
[ ] Search results display clearly.
[ ] Clicking search result opens exact bidding detail.
[ ] Search works from shared header on every page.
```

## Loading

```text
[ ] Dashboard shows loading indicator.
[ ] Bidding detail shows loading indicator.
[ ] My Bids shows loading indicator.
[ ] Profile shows loading indicator.
[ ] Sell item submit shows loading indicator.
[ ] Deposit shows loading indicator.
[ ] Search shows loading indicator.
[ ] Notifications load with indicator or graceful loading state.
[ ] UI does not freeze during DB/network calls.
```

## Network

```text
[ ] NetworkRequestClient is used only for request/response.
[ ] NetworkClient is used only for live push updates.
[ ] Network bidding requires authenticated user.
[ ] Network bidding uses same service as DAO fallback.
[ ] New message types are serializable.
[ ] Network failure gracefully falls back or shows clear error.
```

## Auction lifecycle

```text
[ ] Ended auctions are finalized in DB.
[ ] Finished auctions stop accepting bids.
[ ] Result notifications are created.
[ ] Wallet payment is finalized.
[ ] Dashboard status is updated.
```

---

# Suggested Commit Order

Use small commits so future partial fixes do not break the whole system.

```text
commit 1: Fix tests and compile errors
commit 2: Fix network serialization and config defaults
commit 3: Move shared DTOs and remove server UI imports
commit 4: Add service layer skeleton
commit 5: Add wallet schema, DAO, and service
commit 6: Add wallet UI and header balance
commit 7: Refactor bidding through one service with wallet rules
commit 8: Add notification schema, DAO, and service
commit 9: Add notification popup and unread badge
commit 10: Fix image storage and loading
commit 11: Complete My Bids actions and seller table
commit 12: Fix profile stats and buttons
commit 13: Add search bar and search service
commit 14: Add loading overlay to pages
commit 15: Add auction finalization lifecycle
commit 16: Final cleanup and test
```

---

# Final Instruction For Codex

Implement in this order:

1. Make project compile and run.
2. Remove/contain split source-of-truth problems.
3. Make database-backed services authoritative.
4. Add wallet.
5. Add bid money restrictions.
6. Add notifications.
7. Add image loading/storage.
8. Complete My Bids.
9. Fix profile.
10. Add search.
11. Add loading indicators.
12. Clean network integration.
13. Finalize ended auctions.

Do not add isolated UI-only logic that bypasses services.

Do not add new feature paths that only work in one screen.

Do not use in-memory auction state for real auctions shown in the JavaFX app.

Do not trust user identity sent from client payload.

Do not store runtime images inside source folders.

Do not block JavaFX UI thread with DAO/network calls.

The goal is one connected auction system, not more separated parts.
