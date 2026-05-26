# Final PDF Report Tutorial

Use this file as a guide for writing the final project PDF report. Keep the PDF practical: explain what the system does, how it is built, what is complete, what is limited, and how each teammate contributed.

## Suggested PDF Structure

1. Introduction
2. Goals and Scope
3. Architecture
4. Features Implemented
5. Technical Decisions
6. Challenges and Issues
7. Performance Notes and Future Improvements
8. Team Contribution
9. Conclusion
10. Appendix: setup steps, screenshots, links

## Introduction

Briefly describe Bidify as a JavaFX online auction system. Mention that users can register, log in, list items, browse auctions, bid with wallet constraints, receive notifications, and track bidding/selling history.

## Goals and Scope

Explain the project goal:

- Build a desktop auction application.
- Store real auction data in MySQL.
- Support wallet-backed bidding and auction finalization.
- Provide optional socket networking and push updates.
- Prepare a runnable demo for submission.

Also mention what is out of scope or limited, such as payment gateway integration, advanced admin moderation, and fully cloud-hosted image storage.

## Architecture Guidance

Include at least these diagrams or screenshots:

- High-level layered architecture diagram:
  - JavaFX Controller
  - Client Feature Service
  - NetworkRequestClient or local DAO fallback
  - Server ClientHandler
  - Server Application Service
  - DAO
  - MySQL
- Runtime flow for placing a bid.
- Database relationship diagram for users, items, auctions, bids, wallet tables, notifications, and item images.
- Screenshot of each main UI screen:
  - Login
  - Signup
  - Dashboard
  - Bidding Detail
  - My Bids
  - Sell Item
  - Profile
  - Notification popup

When describing architecture, explain that the visible UI is primarily database-backed and that `BiddingApplicationService`, `WalletApplicationService`, `NotificationApplicationService`, and `AuctionFinalizationService` contain the core business behavior.

## Feature Explanation Guidance

For each feature, write four short parts:

1. Feature: what the user can do.
2. Implementation approach: which controller/service/DAO/database tables are involved.
3. Reason: why this approach was chosen.
4. Evidence: screenshot, test name, or demo step.

Example:

```text
Feature: Wallet-backed bidding
Implementation: BiddingApplicationService validates the bid and uses WalletApplicationService/WalletDAO to reserve or release holds in one transaction.
Reason: Wallet rules must be enforced in the backend, not only in the UI.
Evidence: demo bid flow and WalletApplicationServiceTest.
```

## Performance and Issue Notes

Document the current slowness investigation honestly. Practical causes to discuss:

- Some screens still load several DAO queries per refresh.
- Dashboard and detail refresh can be triggered again by push updates.
- MySQL connection creation happens frequently because there is no connection pool.
- Image loading uses local file paths and JavaFX CSS backgrounds, which can slow first render for large images.
- FXML scenes are loaded repeatedly during navigation instead of being cached.
- Network mode adds socket request overhead and fallback waits if the server is not running.
- Some refresh work is now moved to JavaFX `Task`, but visual testing is still needed for all edge cases.

Future improvements:

- Add a small connection pool.
- Cache dashboard summaries and item image paths.
- Debounce push-triggered dashboard reloads.
- Resize/compress uploaded images.
- Cache frequently used FXML scenes where safe.
- Add UI integration tests for FXML loading and navigation.

## Team Contribution Table

Fill `PIC`, `Status`, and `Notes` before exporting the final PDF.

| Feature/System | Description | Status | PIC | Notes |
|---|---|---|---|---|
| Login/Register | User account creation and authentication | TODO | TODO | Include screenshot and AuthService notes |
| Session/token support | Authenticated request tokens for network calls | TODO | TODO | Mention server-side session registry |
| Dashboard | Auction list, filters, stats, pagination | TODO | TODO | Include screenshot |
| Bidding Detail | Item detail, countdown, bid history, bid submit | TODO | TODO | Include screenshot |
| Transaction-safe bidding | Validates bid rules and updates bid/wallet state | TODO | TODO | Mention DB transaction |
| Wallet | Balance, available balance, deposit, holds | TODO | TODO | Include wallet tables |
| Notifications | Persisted notifications, unread badge, popup | TODO | TODO | Include popup screenshot |
| Auction finalization | Expired auctions become finished and notify users | TODO | TODO | Mention scheduler/load triggers |
| Push updates | Long-lived client receives auction/wallet/notification updates | TODO | TODO | Mention optional network mode |
| Search | Header search popup opens matching auctions | TODO | TODO | Include search screenshot |
| Image upload | Sell item uploads main and gallery images | TODO | TODO | Mention upload directory |
| Image display | Dashboard/detail image rendering with fallback behavior | TODO | TODO | Include screenshot |
| My Bids | Active bids, completed bids, increase bid | TODO | TODO | Include screenshot |
| Seller items | Selling/sold table for current seller | TODO | TODO | Include screenshot |
| Sell Item | Listing form, validation, image selection | TODO | TODO | Include screenshot |
| Profile | Stats, wallet display, deposit, email/password actions | TODO | TODO | Include screenshot |
| AppHeader | Shared navigation, wallet quick info, search, notifications | TODO | TODO | Explain reuse across pages |
| NavigationService | Centralized screen loading and detail navigation | TODO | TODO | Mention cleanup on navigation |
| LoadingOverlay | Loading indicator for slow operations | TODO | TODO | Mention JavaFX Task usage |
| DAO layer | MySQL persistence for users/items/auctions/bids/wallet/notifications | TODO | TODO | Include schema diagram |
| NetworkRequestClient | Short-lived request/response networking | TODO | TODO | Optional mode |
| NetworkClient | Long-lived push connection | TODO | TODO | Optional mode |
| Tests | DAO/service/network serialization tests | TODO | TODO | Include final test count |
| README/setup | Build, run, and feature documentation | TODO | TODO | Link README |
| Demo preparation | Scripted demo flow and sample data | TODO | TODO | Add video link |

## Submission Checklist

- [ ] README is complete and reviewed.
- [ ] PDF report is exported.
- [ ] Demo video is uploaded.
- [ ] JAR is generated with `.\mvnw.cmd package`.
- [ ] Database setup is verified.
- [ ] `db.properties` is correct for the demo machine.
- [ ] Server run command is tested if using network mode.
- [ ] Client run command is tested.
- [ ] Login/register demo accounts are ready.
- [ ] Sample auctions and images are ready.
- [ ] Wallet/deposit demo flow is prepared.
- [ ] Notification demo flow is prepared.
- [ ] Final test command passes: `.\mvnw.cmd test`.
