# Auction System — Consolidated Development Plan

## 1. Purpose

This Markdown file consolidates the four provided documents into one clean project reference.

It covers:

1. Methods and files that are unused, internal-only, missing, or require verification.
2. Implementation plan.
3. Task checklist.
4. Further suggestions to improve the application.

---

## 2. Current Architecture Rule

The project should follow this layered architecture:

```text
Controller → Service → DAO → Database
UI         → Logic   → JDBC/SQL → MySQL
```

Important rule:

```text
Controllers must not call DAO classes directly.
Controllers should call Services.
Services should call DAOs.
DAOs should communicate with the database.
```

This keeps the code easier to test, maintain, and debug.

---

## 3. AuctionDAO Method Usage Summary

The provided documents contain two slightly different audits of `AuctionDAO` usage.

To avoid incorrect implementation decisions, this file separates the methods into four groups:

1. Confirmed used methods.
2. Unused methods.
3. Internal-only helper methods.
4. Methods that require verification because the source files disagree.

---

## 4. Confirmed Used Methods

These methods are already connected to existing services such as `DashboardService`, `AuctionDetailService`, `MyBidsService`, and `ProfileService`.

| Method | Current Usage |
|---|---|
| `getInstance()` | Used by services to access DAO singleton instance. |
| `findById(String)` | Used by auction detail flow. |
| `countDashboardAuctions(...)` | Used by dashboard statistics. |
| `findDashboardAuctions(...)` | Used by dashboard auction listing. |
| `countActiveAuctions()` | Used by dashboard statistics. |
| `countEndingTodayAuctions()` | Used by dashboard statistics. |
| `countTotalBids()` | Used by dashboard statistics. |
| `findFullAuctionDetail(int)` | Used by auction detail screen. |
| `findAuctionOwner(int)` | Used by auction detail screen. |
| `findActiveAuctionsByParticipant(String)` | Used by My Bids feature. |
| `findCompletedAuctionsByBidder(String)` | Used by My Bids feature. |
| `getUserHighestBid(int, String)` | Used by My Bids feature. |
| `getHighestBidderUsername(int)` | Used by My Bids and Auction Detail features. |
| `getBidHistoryForAuction(int)` | Used by auction detail or bid history display. |
| `countCreatedByUser(String)` | Used by profile statistics. |
| `countWonByUser(String)` | Used by profile statistics. |
| `countBidsByUser(String)` | Used by profile statistics. |
| `countActiveParticipations(String)` | Used by profile statistics. |

---

## 5. Unused Methods

These methods are present in `AuctionDAO`, but the provided files indicate that their supported features are missing, excluded from the current scope, or not yet wired into the UI.

| Method | Related Missing Feature | Suggested Action |
|---|---|---|
| `save(String, AuctionSnapshot)` | Create Auction / Seller Panel | Use only if implementing auction creation. |
| `delete(String)` | Seller auction deletion | Use only if implementing seller auction management. |
| `findByStatus(String)` | Auction status filtering | Use in seller panel, admin panel, or dashboard filters. |
| `findByClientOwner(String)` | Seller-owned auction list | Use in My Auctions / Seller Panel. |
| `updateStatus(String, String)` | Auction lifecycle management | Use for `OPEN → RUNNING → FINISHED` and `CANCELED`. |
| `removeParticipant(String, String)` | Leave auction feature | Use only if bidders can leave an auction. |

---

## 6. Internal-Only Helper Methods

These methods may not be directly called from a feature, but they are still useful as internal DAO helpers.

| Method | Current Status | Suggested Action |
|---|---|---|
| `findAll()` | Internal-only or admin-use candidate | Keep as helper, or expose through `AdminService`. |
| `findAllAsMap()` | Internal-only or admin-use candidate | Keep as helper, or expose through `AdminService`. |
| `update(String, AuctionSnapshot)` | Internal helper or seller-edit candidate | Use only if seller edit feature is implemented. |
| `count()` | Internal helper or ID-generation candidate | Prefer database auto-increment if possible. |

---

## 7. Methods That Require Verification

The uploaded files disagree about these methods.

One audit says they are unused, while another implementation plan says they are already used by the bid flow.

| Method | Conflict | Required Check |
|---|---|---|
| `exists(String)` | One file says internal-only; another says used. | Search the codebase for `exists(` before changing. |
| `addBid(String, Bid)` | One file says unused; another says used. | Check whether `BidService` or `AuctionDetailService` already calls it. |
| `addParticipant(String, String)` | One file says unused; another says used. | Check whether bidder registration is already automatic. |

Recommended verification command:

```bash
grep -R "exists(" Source/src/main/java
grep -R "addBid(" Source/src/main/java
grep -R "addParticipant(" Source/src/main/java
```

PowerShell equivalent:

```powershell
Select-String -Path "Source/src/main/java/**/*.java" -Pattern "exists\("
Select-String -Path "Source/src/main/java/**/*.java" -Pattern "addBid\("
Select-String -Path "Source/src/main/java/**/*.java" -Pattern "addParticipant\("
```

---

## 8. Missing or Not Yet Implemented Files

The provided task lists mention several files that are not yet implemented or are planned for future phases.

### 8.1 Unit Test Files

```text
Source/src/test/java/Client/services/AuthenticationServiceTest.java
Source/src/test/java/Client/services/AuctionServiceTest.java
Source/src/test/java/Client/services/BidValidationTest.java
Source/src/test/java/Client/dao/AuctionDAOTest.java
```

### 8.2 CI/CD File

```text
.github/workflows/maven.yml
```

### 8.3 Create Auction Feature Files

```text
create_auction.fxml
create_auction.css
CreateAuctionController.java
CreateAuctionService.java
```

### 8.4 Seller Panel Files

```text
my_auctions.fxml
MyAuctionsController.java
MyAuctionsService.java
```

### 8.5 Optional Admin Panel Files

```text
admin_panel.fxml
AdminController.java
AdminService.java
```

---

# 9. Implementation Plan

## Phase 1 — Unit Testing

### Goal

Complete the missing testing requirement and improve code reliability.

### Why This Phase Comes First

The provided implementation plan indicates that the core application features are mostly complete, while unit tests are still missing.

This phase is the safest and most important improvement because it directly supports project grading and reduces bugs.

### Files To Create

```text
Source/src/test/java/Client/services/AuthenticationServiceTest.java
Source/src/test/java/Client/services/AuctionServiceTest.java
Source/src/test/java/Client/services/BidValidationTest.java
Source/src/test/java/Client/dao/AuctionDAOTest.java
```

### Required Tests

#### `AuthenticationServiceTest`

- Test successful login.
- Test failed login with invalid username.
- Test failed login with wrong password.
- Test registration validation.

#### `AuctionServiceTest`

- Test fetching active auctions.
- Test auction filtering.
- Test loading auction details.

#### `BidValidationTest`

- Test valid bid placement.
- Test bid rejection when the amount is lower than the current price.
- Test bid rejection when the amount is equal to the current price.
- Test invalid bid input such as negative numbers or empty fields.

#### `AuctionDAOTest`

- Test basic DAO operations.
- Test whether DAO methods return expected results.
- Test database consistency where possible.

### Verification Command

```bash
mvn clean test
```

---

## Phase 2 — Place Bid Flow Verification

### Goal

Make sure the existing bidding feature is fully connected and working correctly.

### Related Methods

```text
exists(String)
addBid(String, Bid)
addParticipant(String, String)
```

### Important Note

Before implementing new code, verify whether the place-bid flow already exists.

If `BidService` or `AuctionDetailService` already uses `addBid()` and `addParticipant()`, do not duplicate the logic.

### Suggested Service Method If Missing

```java
public boolean placeBid(int auctionId, String username, float amount) {
    AuctionDAO dao = AuctionDAO.getInstance();
    String id = String.valueOf(auctionId);

    if (!dao.exists(id)) {
        return false;
    }

    Bid bid = new Bid(new Date(), amount, username);

    dao.addBid(id, bid);
    dao.addParticipant(id, username);

    return true;
}
```

### Suggested Controller Handler If Missing

```java
@FXML
private void onPlaceBid() {
    float amount = Float.parseFloat(txtBidAmount.getText());
    String username = SessionManager.getCurrentUser().getUsername();

    if (service.placeBid(currentAuctionId, username, amount)) {
        loadAuctionData(currentAuctionId);
    }
}
```

### Required Validation

Before accepting a bid, check:

- Auction exists.
- Auction is still active.
- Bid amount is higher than current highest bid.
- Bid amount is not negative.
- User is logged in.
- User is not bidding on their own auction, if self-bidding is disallowed.

---

## Phase 3 — CI/CD Workflow

### Goal

Automatically build and test the project when code is pushed to GitHub.

### File To Create

```text
.github/workflows/maven.yml
```

### Suggested Workflow

```yaml
name: Maven Build and Test

on:
  push:
    branches:
      - main
      - test
      - ui
  pull_request:
    branches:
      - main
      - test

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout repository
        uses: actions/checkout@v4

      - name: Set up JDK
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Build and test with Maven
        run: mvn clean test
```

---

## Phase 4 — Auction Lifecycle Management

### Goal

Allow auctions to move through clear status states.

### Related DAO Method

```text
updateStatus(String, String)
```

### Suggested Status Flow

```text
OPEN → RUNNING → FINISHED
```

Additional state:

```text
CANCELED
```

### Suggested Service Methods

```java
public boolean startAuction(int auctionId) {
    return AuctionDAO.getInstance()
        .updateStatus(String.valueOf(auctionId), "RUNNING");
}

public boolean finishAuction(int auctionId) {
    return AuctionDAO.getInstance()
        .updateStatus(String.valueOf(auctionId), "FINISHED");
}

public boolean cancelAuction(int auctionId) {
    return AuctionDAO.getInstance()
        .updateStatus(String.valueOf(auctionId), "CANCELED");
}
```

### Auto-Close Logic

When the current time is greater than the auction end time, the auction should automatically become `FINISHED`.

Possible implementation:

```java
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

scheduler.scheduleAtFixedRate(() -> {
    // Load active auctions
    // Check end time
    // Update expired auctions to FINISHED
}, 0, 1, TimeUnit.MINUTES);
```

---

## Phase 5 — Create Auction Feature

### Goal

Allow sellers to create new auctions.

### Related DAO Methods

```text
save(String, AuctionSnapshot)
count()
```

### Files To Create

```text
create_auction.fxml
create_auction.css
CreateAuctionController.java
CreateAuctionService.java
```

### Required UI Fields

- Item name.
- Item description.
- Item category.
- Starting price.
- Auction type.
- Auction end date.
- Create button.

### Suggested Service Method

```java
public int createAuction(
    String ownerUsername,
    Item item,
    Date endDate,
    String type
) {
    AuctionDAO dao = AuctionDAO.getInstance();
    int newId = dao.count() + 1;

    AuctionSnapshot snapshot = new AuctionSnapshot(
        newId,
        ownerUsername,
        new Date(),
        endDate,
        type,
        "OPEN",
        item,
        new LinkedList<>(),
        new ArrayList<>(),
        false
    );

    dao.save(String.valueOf(newId), snapshot);

    return newId;
}
```

### Proofreading Note

Using `dao.count() + 1` for ID generation can create duplicate IDs if auctions are deleted or if two users create auctions at the same time.

Better solution:

```text
Use AUTO_INCREMENT in MySQL for auction IDs.
```

---

## Phase 6 — Seller Panel / My Auctions

### Goal

Allow sellers to manage auctions they created.

### Related DAO Methods

```text
findByClientOwner(String)
findByStatus(String)
update(String, AuctionSnapshot)
delete(String)
```

### Files To Create

```text
my_auctions.fxml
MyAuctionsController.java
MyAuctionsService.java
```

### Required Features

- Show auctions created by the logged-in user.
- Filter auctions by status.
- Edit auction details.
- Delete or cancel auctions.
- View bid count and end date.

### Suggested Service Methods

```java
public List<AuctionSnapshot> loadMyAuctions(String ownerUsername) {
    return AuctionDAO.getInstance().findByClientOwner(ownerUsername);
}

public List<AuctionSnapshot> loadAuctionsByStatus(String status) {
    return AuctionDAO.getInstance().findByStatus(status);
}

public boolean updateAuction(String auctionId, AuctionSnapshot updated) {
    return AuctionDAO.getInstance().update(auctionId, updated);
}

public boolean deleteAuction(String auctionId) {
    return AuctionDAO.getInstance().delete(auctionId);
}
```

### Important Scope Note

One uploaded plan says the Seller Panel is outside the simplified must-have scope.

Therefore, this feature should be implemented only after required tests and core bid features are complete.

---

## Phase 7 — Leave Auction Feature

### Goal

Allow a bidder to leave an auction they joined.

### Related DAO Method

```text
removeParticipant(String, String)
```

### Suggested Service Method

```java
public boolean leaveAuction(int auctionId, String username) {
    return AuctionDAO.getInstance()
        .removeParticipant(String.valueOf(auctionId), username);
}
```

### Important Validation

Do not allow a user to leave if:

- They are currently the highest bidder.
- The auction is already finished.
- The auction does not exist.

---

## Phase 8 — Optional Admin / Statistics Panel

### Goal

Provide an admin-level view of all auctions.

### Related DAO Methods

```text
findAll()
findAllAsMap()
```

### Suggested Files

```text
admin_panel.fxml
AdminController.java
AdminService.java
```

### Suggested Service Methods

```java
public List<AuctionSnapshot> loadAllAuctions() {
    return AuctionDAO.getInstance().findAll();
}

public Map<String, AuctionSnapshot> loadAllAuctionsAsMap() {
    return AuctionDAO.getInstance().findAllAsMap();
}
```

### Suggested Features

- View all auctions.
- Search by auction ID.
- Filter by status.
- View auction owner.
- View total bids.
- Cancel inappropriate auctions.

---

## Phase 9 — Bid History Line Chart

### Goal

Improve the bidding detail screen with a visual bid history.

### Related DAO Method

```text
getBidHistoryForAuction(int)
```

### Suggested UI Component

```text
JavaFX LineChart
```

### Data To Display

- X-axis: bid time.
- Y-axis: bid amount.
- Each point: one bid.

---

# 10. Task Checklist

## Phase 1 — Unit Tests

- [ ] Configure test source directory in `pom.xml` if needed.
- [ ] Create `AuthenticationServiceTest.java`.
- [ ] Test successful login.
- [ ] Test login failure with invalid credentials.
- [ ] Test registration validation.
- [ ] Create `AuctionServiceTest.java`.
- [ ] Test fetching active auctions.
- [ ] Test auction status filtering.
- [ ] Test loading auction details.
- [ ] Create `BidValidationTest.java`.
- [ ] Test valid bid placement.
- [ ] Test invalid bid lower than current price.
- [ ] Test invalid bid equal to current price.
- [ ] Test invalid negative bid.
- [ ] Create `AuctionDAOTest.java`.
- [ ] Run `mvn clean test`.
- [ ] Fix all failed tests.

---

## Phase 2 — Place Bid Flow

- [ ] Verify whether `addBid()` is already used.
- [ ] Verify whether `addParticipant()` is already used.
- [ ] Verify whether `exists()` is already used.
- [ ] Connect Place Bid button if missing.
- [ ] Add bid amount validation.
- [ ] Refresh auction detail after successful bid.
- [ ] Refresh bid history after successful bid.
- [ ] Show error message for invalid bids.

---

## Phase 3 — CI/CD

- [ ] Create `.github/workflows/maven.yml`.
- [ ] Configure JDK version.
- [ ] Configure Maven build.
- [ ] Configure Maven test command.
- [ ] Push workflow to GitHub.
- [ ] Confirm GitHub Actions runs successfully.

---

## Phase 4 — Auction Lifecycle

- [ ] Implement `startAuction()`.
- [ ] Implement `finishAuction()`.
- [ ] Implement `cancelAuction()`.
- [ ] Wire `updateStatus()` into service layer.
- [ ] Add auto-close logic for expired auctions.
- [ ] Prevent bidding on finished auctions.
- [ ] Prevent bidding on canceled auctions.

---

## Phase 5 — Create Auction

- [ ] Create `create_auction.fxml`.
- [ ] Create `create_auction.css`.
- [ ] Create `CreateAuctionController.java`.
- [ ] Create `CreateAuctionService.java`.
- [ ] Add navigation from dashboard.
- [ ] Validate item name.
- [ ] Validate starting price.
- [ ] Validate end date.
- [ ] Save item data.
- [ ] Save auction data.
- [ ] Show success message after creation.

---

## Phase 6 — Seller Panel

- [ ] Create `my_auctions.fxml`.
- [ ] Create `MyAuctionsController.java`.
- [ ] Create `MyAuctionsService.java`.
- [ ] Load auctions by current seller.
- [ ] Add status filter.
- [ ] Add edit button.
- [ ] Add delete or cancel button.
- [ ] Add confirmation dialog before deletion.

---

## Phase 7 — Leave Auction

- [ ] Implement `leaveAuction()` service method.
- [ ] Add Leave Auction button.
- [ ] Prevent highest bidder from leaving.
- [ ] Refresh My Bids screen after leaving.

---

## Phase 8 — Optional Admin Panel

- [ ] Create admin UI.
- [ ] Load all auctions.
- [ ] Add search by auction ID.
- [ ] Add status filter.
- [ ] Add admin cancel action.

---

## Phase 9 — Bid History Chart

- [ ] Add JavaFX `LineChart` to bidding detail screen.
- [ ] Load data from `getBidHistoryForAuction()`.
- [ ] Format bid time on X-axis.
- [ ] Format bid amount on Y-axis.
- [ ] Refresh chart after each new bid.

---

# 11. Recommended Priority Order

| Priority | Feature | Reason | Difficulty |
|---|---|---|---|
| P0 | Unit Tests | Required by grading rubric and improves reliability. | Small |
| P1 | Place Bid Flow Verification | Core auction functionality. | Small |
| P2 | Auction Lifecycle | Required for realistic auction behavior. | Small to Medium |
| P3 | CI/CD | Helpful for automatic build and testing. | Small |
| P4 | Bid History Chart | Improves presentation and user experience. | Small to Medium |
| P5 | Create Auction | Useful seller feature but may be outside must-have scope. | Medium |
| P6 | Seller Panel | Useful but larger scope. | Medium |
| P7 | Admin Panel | Optional advanced feature. | Medium |

---

# 12. Further Suggestions To Improve The Application

## 12.1 Real-Time Bid Updates

### Current Issue

Users may not instantly see new bids from other users.

### Suggested Solutions

- WebSocket.
- Java Socket Server.
- Firebase Realtime Database.
- Periodic polling as a simpler fallback.

### Benefit

The auction experience becomes closer to a real live auction platform.

---

## 12.2 Database Connection Pooling

### Current Issue

Dashboard loading may be slow if the application repeatedly opens database connections.

### Suggested Solution

Use a connection pool such as:

```text
HikariCP
```

### Benefit

- Faster database access.
- Less connection overhead.
- Better performance for multiple users.

---

## 12.3 Better Database ID Generation

### Current Issue

Using `count() + 1` for IDs can cause duplicate IDs.

### Suggested Solution

Use MySQL `AUTO_INCREMENT` primary keys.

### Benefit

- Safer ID generation.
- Better concurrency support.
- Less manual ID logic in Java.

---

## 12.4 Stronger Bid Validation

Add checks for:

- Empty bid input.
- Non-numeric bid input.
- Negative bid amount.
- Bid lower than current price.
- Bid equal to current price.
- Bidding after auction end time.
- Bidding on canceled auction.
- Seller bidding on their own auction.

---

## 12.5 Password Security

### Current Risk

If passwords are stored as plain text, user accounts are not safe.

### Suggested Solution

Use password hashing:

```text
BCrypt
```

### Benefit

Even if the database is leaked, raw passwords are not directly exposed.

---

## 12.6 Better Error Handling

Use clear custom exceptions such as:

```text
AuctionNotFoundException
AuctionClosedException
InvalidBidAmountException
BidTooLowException
UnauthorizedActionException
```

Benefits:

- Cleaner service code.
- Better messages for users.
- Easier debugging.

---

## 12.7 Improved UI/UX

Suggested improvements:

- Loading indicators while fetching data.
- Countdown timer for each auction.
- Toast notifications after successful bid.
- Clear validation messages.
- Better empty-state screens.
- Confirmation dialog before delete or cancel actions.
- Bid history chart.
- Responsive dashboard layout.

---

## 12.8 Logging System

Suggested library:

```text
SLF4J + Logback
```

Log important events:

- User login.
- User registration.
- Bid placement.
- Auction creation.
- Auction cancellation.
- Database errors.
- Unexpected exceptions.

---

## 12.9 Query Optimization

Suggested improvements:

- Add indexes on frequently searched columns.
- Use pagination for dashboard auction list.
- Avoid loading unnecessary full auction details on dashboard.
- Lazy-load images.
- Cache dashboard statistics when possible.

---

## 12.10 Role-Based Access Control

Suggested roles:

```text
USER
SELLER
ADMIN
```

Example permissions:

| Role | Permissions |
|---|---|
| USER | Browse auctions, place bids, view bids. |
| SELLER | Create auctions, manage own auctions. |
| ADMIN | Manage all auctions and users. |

---

## 12.11 Deployment Improvements

Suggested deployment stack:

```text
Docker
Docker Compose
MySQL container
Java application container
```

Benefits:

- Easier project setup.
- Consistent environment across machines.
- Easier deployment demonstration.

---

# 13. Final Recommendation

The best development order is:

1. Finish unit tests.
2. Verify and complete the Place Bid flow.
3. Implement auction lifecycle status updates.
4. Add CI/CD workflow.
5. Add bid history chart if time allows.
6. Add Create Auction and Seller Panel only after required features are stable.
7. Keep Admin Panel as optional.

This order is safer because it prioritizes grading requirements, core auction behavior, and project stability before optional expansion features.
