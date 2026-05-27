# Bidify Online Auction System

Bidify is a JavaFX desktop auction application for listing items, browsing active auctions, placing bids, tracking personal bids, and managing wallet-backed bidding. The project combines a local database-backed client flow with an optional socket server for authenticated request/response networking and live push updates.

## Technologies

- Java 25 target, based on the current `pom.xml`
- JavaFX 21.0.6 for desktop UI and FXML screens
- Maven Wrapper for build/test/run commands
- MySQL with JDBC through DAO classes
- H2 in MySQL compatibility mode for automated tests
- Java socket networking with serialized packets/DTOs
- Gson and standard Java libraries

## Requirements

- JDK 25, or update `pom.xml` if the team decides to target another JDK
- Maven Wrapper included in this repository: `mvnw.cmd`
- MySQL server or the configured remote MySQL database
- Valid database settings in `Source/resources/db.properties`
- Windows PowerShell examples below assume the project root as the working directory

Database configuration is loaded from:

```text
Source/resources/db.properties
```

Expected keys:

```properties
db.url=jdbc:mysql://host:3306/database_name
db.user=your_user
db.password=your_password
```

The DAO layer creates or upgrades the main tables at runtime. The reference schema is also available at:

```text
Source/src/Server/dao/database-schema.sql
```

## Project Structure

```text
Source/src/Client/app                 JavaFX application entry point
Source/src/Client/components          Shared UI components: AppHeader, notifications, search, loading overlay
Source/src/Client/core                Base UI/navigation and network clients
Source/src/Client/features            Feature controllers and client services
Source/src/Client/navigation          NavigationService screen loading
Source/src/CommonClasses              Domain classes and shared DTOs
Source/src/Packets                    Network message types, packet factory, network config
Source/src/Payload                    Legacy socket payload classes
Source/src/Server                     Socket server and client handler
Source/src/Server/dao                 JDBC DAOs and database schema scripts
Source/src/Server/service             Application services for auth, auctions, bidding, wallet, notifications, images, finalization
Source/resources/client/views         FXML and CSS files
Source/resources/client/images        Bundled UI images
Source/resources/client/fonts         Bundled UI fonts
src/test/java                         JUnit tests
uploads/items                         Runtime uploaded item images
target                                Maven build output
```

## Build Output and JAR Location

After compile/test, classes and copied resources are under:

```text
target/classes
target/test-classes
target/surefire-reports
```

After packaging, the Maven JAR is expected at:

```text
target/HeThongDauGia-1.0-SNAPSHOT.jar
```

Generate it with:

```powershell
.\mvnw.cmd package
```

## Run Order

### Local client mode

Network mode is disabled by default, so the JavaFX client uses local DAO/database fallback paths.

1. Start MySQL or verify the remote database is reachable.
2. Confirm `Source/resources/db.properties` has valid credentials.
3. Compile and copy resources:

```powershell
.\mvnw.cmd compile
```

4. Start the JavaFX client:

```powershell
.\mvnw.cmd javafx:run
```

### Optional network mode with server and push updates

Use this when testing socket request/response and live push updates.

1. Start MySQL.
2. Compile the project:

```powershell
.\mvnw.cmd compile
```

3. Build a runtime classpath file:

```powershell
.\mvnw.cmd dependency:build-classpath -Dmdep.outputFile=target\classpath.txt
```

4. Start the server:

```powershell
$cp = "target\classes;" + (Get-Content target\classpath.txt)
java -cp $cp -Dauction.server.port=12345 Server.Server
```

5. Start the client with network mode enabled:

```powershell
.\mvnw.cmd javafx:run -Dauction.network.enabled=true -Dauction.server.host=127.0.0.1 -Dauction.server.port=12345
```

Optional upload directory override:

```powershell
-Dauction.upload.dir=D:\auction_uploads
```

## Completed Features

- [x] Login and signup
- [x] Session/token support for network requests
- [x] Dashboard with pagination, filters, stats, images, and auction cards
- [x] Auction detail page with item data, bid history, seller display, countdown, and image gallery
- [x] Transaction-safe bidding through `BiddingApplicationService`
- [x] Wallet balance, available balance, deposit validation, holds, release, and spending finalization
- [x] Header wallet quick info using available balance
- [x] My Bids active/completed sections
- [x] Seller selling/sold item table
- [x] Sell Item flow with image upload and external upload storage
- [x] Notification persistence, unread badge, popup, mark read, and action navigation
- [x] Search popup from shared header
- [x] Loading overlays around major slow UI operations
- [x] Auction finalization service for expired auctions
- [x] Optional long-lived network push updates
- [x] Shared DTO serialization for network responses

## Current Limitations

- Manual visual QA is still required on a real JavaFX desktop session.
- Network mode requires the server to be started separately.
- The push registry is in memory, so server restart requires clients to reconnect.
- Some legacy in-memory auction paths remain for old socket/domain flows; the visible UI primarily uses DAO-backed services.
- Uploaded images are local filesystem paths, so demos should run on the same machine or use a shared upload directory.
- Phone/location profile editing is intentionally shown as unsupported because the current schema does not persist those fields.

## Report and Demo Links

- PDF report: TODO - add final PDF link or file path
- Demo video: TODO - add uploaded video link

## Validation

Run all tests:

```powershell
.\mvnw.cmd test
```

Current final cleanup validation:

```text
Tests run: 204, Failures: 0, Errors: 0, Skipped: 0
```
