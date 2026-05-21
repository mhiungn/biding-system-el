# AuctionDAO — Unused Methods Analysis & Action Plan

## Summary

**AuctionDAO has 31 public/package methods. 18 are used. 13 are UNUSED.**

This document identifies every unused method and groups them into **5 feature areas** you need to build to reach 100% usage.

---

## Current Usage Matrix

| # | Method | Used By | Status |
|---|--------|---------|--------|
| 1 | `getInstance()` | All services | ✅ Used |
| 2 | `save(String, AuctionSnapshot)` | — | ❌ **UNUSED** |
| 3 | `findById(String)` | AuctionDetailService | ✅ Used |
| 4 | `findAll()` | Only internal (`findAllAsMap`) | ⚠️ Internal only |
| 5 | `update(String, AuctionSnapshot)` | Only internal (`save`) | ⚠️ Internal only |
| 6 | `delete(String)` | — | ❌ **UNUSED** |
| 7 | `exists(String)` | Only internal (`save`) | ⚠️ Internal only |
| 8 | `count()` | Only internal (constructor) | ⚠️ Internal only |
| 9 | `findByStatus(String)` | — | ❌ **UNUSED** |
| 10 | `findByClientOwner(String)` | — | ❌ **UNUSED** |
| 11 | `updateStatus(String, String)` | — | ❌ **UNUSED** |
| 12 | `findAllAsMap()` | Only internal (`getItemIdFromItem`) | ⚠️ Internal only |
| 13 | `countDashboardAuctions(...)` | DashboardService | ✅ Used |
| 14 | `findDashboardAuctions(...)` | DashboardService | ✅ Used |
| 15 | `countActiveAuctions()` | DashboardService | ✅ Used |
| 16 | `countEndingTodayAuctions()` | DashboardService | ✅ Used |
| 17 | `countTotalBids()` | DashboardService | ✅ Used |
| 18 | `findFullAuctionDetail(int)` | AuctionDetailService | ✅ Used |
| 19 | `findAuctionOwner(int)` | AuctionDetailService | ✅ Used |
| 20 | `findActiveAuctionsByParticipant(String)` | MyBidsService | ✅ Used |
| 21 | `findCompletedAuctionsByBidder(String)` | MyBidsService | ✅ Used |
| 22 | `getUserHighestBid(int, String)` | MyBidsService | ✅ Used |
| 23 | `getHighestBidderUsername(int)` | MyBidsService, AuctionDetailService | ✅ Used |
| 24 | `getBidHistoryForAuction(int)` | AuctionDetailService | ✅ Used |
| 25 | `countCreatedByUser(String)` | ProfileService | ✅ Used |
| 26 | `countWonByUser(String)` | ProfileService | ✅ Used |
| 27 | `countBidsByUser(String)` | ProfileService | ✅ Used |
| 28 | `countActiveParticipations(String)` | ProfileService | ✅ Used |
| 29 | `addBid(String, Bid)` | — | ❌ **UNUSED** |
| 30 | `addParticipant(String, String)` | — | ❌ **UNUSED** |
| 31 | `removeParticipant(String, String)` | — | ❌ **UNUSED** |

---

## The 13 Unused Methods, Grouped by Feature

### Feature 1: 🎯 Place Bid Flow (BiddingDetailController)

> [!IMPORTANT]
> This is the **highest priority** — the bidding detail screen already exists but the Place Bid button does nothing.

**Unused methods consumed:**
- `addBid(String, Bid)` — insert the new bid into the database
- `addParticipant(String, String)` — auto-register the bidder as a participant when they bid
- `exists(String)` — validate that the auction exists before accepting a bid

**What to do:**

1. **Add to `AuctionDetailService`:**
```java
public boolean placeBid(int auctionId, String username, float amount) {
    AuctionDAO dao = AuctionDAO.getInstance();
    String id = String.valueOf(auctionId);

    // Validate auction exists
    if (!dao.exists(id)) return false;

    // Create and persist the bid
    Bid bid = new Bid(new Date(), amount, username);
    dao.addBid(id, bid);

    // Auto-register as participant
    dao.addParticipant(id, username);

    return true;
}
```

2. **Wire in `BiddingDetailController`** — add an `@FXML` handler for `btnPlaceBid`:
```java
@FXML
private void onPlaceBid() {
    float amount = Float.parseFloat(txtBidAmount.getText());
    String username = SessionManager.getCurrentUser().getUsername();

    if (service.placeBid(currentAuctionId, username, amount)) {
        // Refresh bid info & history
        loadAuctionData(currentAuctionId);
    }
}
```

---

### Feature 2: 🏗️ Create Auction (New Screen)

**Unused methods consumed:**
- `save(String, AuctionSnapshot)` — persist the new auction
- `count()` — generate next auction ID (e.g. `count() + 1`)

**What to do:**

1. **Create `CreateAuctionService`** (in `Client/features/auction/` or `Client/features/dashboard/`):
```java
public int createAuction(String ownerUsername, Item item, Date endDate, String type) {
    AuctionDAO dao = AuctionDAO.getInstance();
    int newId = dao.count() + 1;

    AuctionSnapshot snapshot = new AuctionSnapshot(
        newId, ownerUsername, new Date(), endDate,
        type, "OPEN", item, new LinkedList<>(), new ArrayList<>(), false
    );
    dao.save(String.valueOf(newId), snapshot);
    return newId;
}
```

2. **Create `create_auction.fxml`** — a form with:
   - Item name, description, category, starting price
   - Auction end date picker
   - Auction type selector
   - "Create" button

3. **Create `CreateAuctionController`** — wire the form to call the service.

---

### Feature 3: 📋 My Auctions — Seller View (New Screen or Tab)

**Unused methods consumed:**
- `findByClientOwner(String)` — load all auctions created by the current user
- `findByStatus(String)` — filter seller's auctions by status (OPEN, FINISHED, etc.)
- `delete(String)` — allow seller to delete/remove their auction
- `update(String, AuctionSnapshot)` — allow seller to edit auction details

**What to do:**

1. **Create `MyAuctionsService`**:
```java
public List<AuctionSnapshot> loadMyAuctions(String ownerUsername) {
    return AuctionDAO.getInstance().findByClientOwner(ownerUsername);
}

public List<AuctionSnapshot> loadMyAuctionsByStatus(String status) {
    return AuctionDAO.getInstance().findByStatus(status);
}

public boolean deleteAuction(String auctionId) {
    return AuctionDAO.getInstance().delete(auctionId);
}

public boolean updateAuction(String auctionId, AuctionSnapshot updated) {
    return AuctionDAO.getInstance().update(auctionId, updated);
}
```

2. **Create `my_auctions.fxml`** — a list/table view showing:
   - Auction title, status, bid count, end date
   - "Edit" and "Delete" buttons per row
   - Filter combo box by status (OPEN / RUNNING / FINISHED / CANCELED)

3. **Create `MyAuctionsController`** — loads seller's auctions and handles edit/delete.

4. **Add navigation** — add a "My Auctions" button/tab in the dashboard sidebar.

---

### Feature 4: ⚙️ Auction Lifecycle Management

**Unused methods consumed:**
- `updateStatus(String, String)` — transition auction status (OPEN → RUNNING → FINISHED / CANCELED)
- `removeParticipant(String, String)` — allow a user to leave an auction they joined

**What to do:**

1. **Add to `AuctionDetailService`** (or `MyAuctionsService`):
```java
public boolean finishAuction(int auctionId) {
    return AuctionDAO.getInstance().updateStatus(String.valueOf(auctionId), "FINISHED");
}

public boolean cancelAuction(int auctionId) {
    return AuctionDAO.getInstance().updateStatus(String.valueOf(auctionId), "CANCELED");
}

public boolean startAuction(int auctionId) {
    return AuctionDAO.getInstance().updateStatus(String.valueOf(auctionId), "RUNNING");
}

public boolean leaveAuction(int auctionId, String username) {
    return AuctionDAO.getInstance().removeParticipant(String.valueOf(auctionId), username);
}
```

2. **Wire in controllers:**
   - **Seller side** (`MyAuctionsController`): "Cancel Auction", "Start Auction" buttons
   - **Bidder side** (`BiddingDetailController` or `MyBidsController`): "Leave Auction" button

---

### Feature 5: 📊 Admin / Stats Panel (Optional but covers remaining methods)

**Unused methods consumed:**
- `findAll()` — show all auctions in a management table
- `findAllAsMap()` — map-based lookup for admin operations

**What to do:**

1. **Add to `DashboardService`** or create an `AdminService`:
```java
public List<AuctionSnapshot> loadAllAuctions() {
    return AuctionDAO.getInstance().findAll();
}

public Map<String, AuctionSnapshot> loadAllAuctionsAsMap() {
    return AuctionDAO.getInstance().findAllAsMap();
}
```

2. **Use in DashboardController** or a new Admin panel to show total auction listings, searchable by ID.

> [!TIP]
> Alternatively, `findAll()` and `findAllAsMap()` are already used **internally** by other AuctionDAO methods (`findAllAsMap` → `getItemIdFromItem`, `findAll` → `findAllAsMap`). If you don't want to build an admin screen, these are acceptable as internal-only helpers. Focus on Features 1–4 first.

---

## Recommended Priority Order

| Priority | Feature | Methods It Uses | Effort |
|----------|---------|-----------------|--------|
| 🔴 **P0** | Place Bid Flow | `addBid`, `addParticipant`, `exists` | Small — controller already exists |
| 🟠 **P1** | Auction Lifecycle | `updateStatus`, `removeParticipant` | Small — add methods to existing services |
| 🟡 **P2** | Create Auction | `save`, `count` | Medium — new screen + controller |
| 🟢 **P3** | My Auctions (Seller) | `findByClientOwner`, `findByStatus`, `delete`, `update` | Medium — new screen + controller |
| 🔵 **P4** | Admin/Stats | `findAll`, `findAllAsMap` | Low — optional, already used internally |

---

## Architecture Reminder

```
Controller  →  Service  →  AuctionDAO  →  Database
   (UI)       (logic)      (SQL/JDBC)     (MySQL)
```

> [!WARNING]
> Never call `AuctionDAO` directly from a Controller. Always go through the Service layer. This is already the established pattern in your codebase.
