# Hệ thống đấu giá trực tuyến
## Thành viên:
- Nguyễn Minh Hiếu
- Lê Thiện Giáp
- Nguyễn Nam Khánh
- Phạm Tuấn Quang
### Class Diagram:
[class_diagram.md](Source/src/CommonClasses/class_diagram.md)

| STT | Tên thành viên   | Nhiệm vụ                                            |
|:---:|------------------|-----------------------------------------------------|
| 1 | Phạm Tuấn Quang  | Common Classes ,Network                             |
| 2 | Lê Thiện Giáp    | Common Classes, proofread tổng quát database, logic |
| 3 | Nguyễn Nam Khánh | Triển khai DAO, DataStore để lưu/tải dữ liệu        |
| 4 | Nguyễn Minh Hiếu | Giao diện UI, Controller                            |

```
HeThongDauGia/
├── .idea/
├── .mvn/
├── .vscode/
├── Source/
│   ├── openjfx-11.0.2_windows-x64_bin-sdk/
│   │
│   ├── resources/
│   │   ├── css/
│   │   │   ├── fonts/
│   │   │   ├── bidding_detail.css
│   │   │   ├── dashboard.css
│   │   │   ├── login.css
│   │   │   └── mybids.css
│   │   │
│   │   ├── images/
│   │   │   └── logo.png
│   │   │
│   │   └── views/
│   │       ├── bidding_detail.fxml
│   │       ├── dashboard.fxml
│   │       ├── login.fxml
│   │       └── mybids.fxml
│   │
│   └── src/
│       ├── Client/
│       │   ├── controllers/
│       │   │   ├── Controller.java
│       │   │   ├── DashboardController.java
│       │   │   └── LoginController.java
│       │   │
│       │   ├── .gitkeep
│       │   └── ClientApp.java
│       │
│       ├── CommonClasses/
│       │   ├── Exceptions/
│       │   │   ├── AuctionActiveException.java
│       │   │   ├── AuctionAlreadyRegisteredException.java
│       │   │   ├── AuctionClientsOwnerException.java
│       │   │   ├── AuctionHighBidException.java
│       │   │   ├── AuctionLowBidException.java
│       │   │   ├── AuctionNotOwnerException.java
│       │   │   └── AuctionNotRegisteredException.java
│       │   │
│       │   ├── Items/
│       │   │   ├── Art.java
│       │   │   ├── Electronics.java
│       │   │   ├── Item.java
│       │   │   ├── ItemFactory.java
│       │   │   ├── TypeItem.java
│       │   │   └── Vehicle.java
│       │   │
│       │   ├── Admin.java
│       │   ├── Auction.java
│       │   ├── AuctionManager.java
│       │   ├── AuctionState.java
│       │   ├── AuctionType.java
│       │   ├── Bid.java
│       │   ├── Bidder.java
│       │   ├── BidObserver.java
│       │   ├── BidTransaction.java
│       │   ├── class_diagram.md
│       │   ├── Entity.java
│       │   ├── Seller.java
│       │   └── User.java
│       │
│       ├── Packets/
│       │   ├── MessageType.java
│       │   └── PacketMessage.java
│       │
│       ├── Payload/
│       │   ├── AuctionUpdatePayload.java
│       │   ├── ConcludeAuctionPayload.java
│       │   ├── ConfirmAuctionCancellationPayload.java
│       │   ├── NotifyAuctionWinnerPayload.java
│       │   └── NotifyNoAuctionWinnerPayload.java
│       │
│       └── Server/
│           ├── dao/
│           │   ├── AuctionDAO.java
│           │   ├── AuctionSnapshot.java
│           │   ├── BidTransactionDAO.java
│           │   ├── DatabaseConnection.java
│           │   ├── GenericDAO.java
│           │   ├── ItemDAO.java
│           │   └── UserDAO.java
│           │
│           ├── AuctionCountdownTask.java
│           ├── AuctionTerminateTask.java
│           ├── Client.java
│           ├── ClientHandler.java
│           ├── Server.java
│           └── RunApplication.java
│
├── dummy_data.sql
├── Hướng-dẫn-BTL-LTNC-2026.pdf
├── mysql_migration_plan.md
├── sources.txt
├── target/
├── .gitattributes
```