# Login + Signup UI (Complete)

## Implemented

- Complete login UI is connected and functional in `Source/resources/client/views/auth/login.fxml`.
- Complete signup UI is connected and functional in `Source/resources/client/views/auth/signup.fxml`.
- Login validation:
  - Username must not be blank.
  - Password must not be blank.
- Signup validation:
  - Username must not be blank.
  - Email must contain `@`.
  - Password must not be blank.
  - Confirm password must match password.
- On successful login/signup, app navigates to dashboard.

## Database (MySQL) Integration

- Auth logic is implemented in `Source/src/Client/features/auth/AuthService.java`.
- Data persistence/auth query uses MySQL-backed DAO:
  - `Source/src/Server/dao/UserDAO.java`
  - `Source/src/Server/dao/DatabaseConnection.java`
- User data saved in MySQL table: `users`
  - `username` (PK)
  - `password`
  - `email`
  - `role`
- DB connection config is loaded from `Source/resources/db.properties`.

## Where Logged-in User Information Is Kept

- Fast in-memory session holder:
  - `Source/src/Client/features/auth/UserSession.java`
- Saved immediately after:
  - successful signup (`AuthService.register`)
  - successful login (`AuthService.login`)
- Retrieve instantly anywhere:
  - `UserSession.getCurrentUser()`

## Where It Is Shown After Login

- Dashboard displays quick user info (`username | email`) in header:
  - UI: `Source/resources/client/views/dashboard/dashboard.fxml`
  - Logic: `Source/src/Client/features/dashboard/DashboardController.java`

## Entry Screen

- Client app now starts from login screen:
  - `Source/src/Client/app/ClientApp.java`
