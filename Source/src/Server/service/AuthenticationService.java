package Server.service;

import CommonClasses.User;
import Server.dao.UserDAO;

/**
 * Service Layer for user authentication and registration.
 * <p>
 * Encapsulates all business logic related to user identity management.
 * Acts as the intermediary between the network layer ({@code ClientHandler})
 * and the data access layer ({@link UserDAO}), following the Layered MVC
 * architecture.
 * </p>
 *
 * <h3>Singleton Pattern:</h3>
 * Thread-safe lazy initialization using double-checked locking.
 *
 * @see UserDAO
 * @see User
 */
public class AuthenticationService {

    // ========================== Singleton ==========================

    private static volatile AuthenticationService instance;

    /**
     * Returns the singleton instance of {@code AuthenticationService}.
     *
     * @return the singleton instance
     */
    public static AuthenticationService getInstance() {
        if (instance == null) {
            synchronized (AuthenticationService.class) {
                if (instance == null) {
                    instance = new AuthenticationService();
                }
            }
        }
        return instance;
    }

    private final UserDAO userDAO;

    private AuthenticationService() {
        this.userDAO = UserDAO.getInstance();
    }

    // ========================== Business Logic ==========================

    /**
     * Authenticates a user by verifying their username and password against
     * the database.
     *
     * @param username the username to authenticate
     * @param password the password to verify
     * @return the authenticated {@link User} object if credentials are valid,
     *         or {@code null} if authentication fails
     */
    public User login(String username, String password) {
        if (username == null || username.trim().isEmpty()) {
            System.err.println("[AuthenticationService] Login failed: username is null or empty.");
            return null;
        }
        if (password == null || password.trim().isEmpty()) {
            System.err.println("[AuthenticationService] Login failed: password is null or empty.");
            return null;
        }

        User user = userDAO.authenticate(username, password);
        if (user != null) {
            System.out.println("[AuthenticationService] User '" + username + "' authenticated successfully.");
        } else {
            System.out.println("[AuthenticationService] Authentication failed for user: " + username);
        }
        return user;
    }

    /**
     * Registers a new user in the system.
     * <p>
     * Validates that the username is not already taken before saving.
     * </p>
     *
     * @param user the user to register
     * @return {@code true} if registration was successful, {@code false} if the
     *         username is already taken or input is invalid
     */
    public boolean register(User user) {
        if (user == null) {
            System.err.println("[AuthenticationService] Registration failed: user is null.");
            return false;
        }
        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            System.err.println("[AuthenticationService] Registration failed: username is null or empty.");
            return false;
        }

        // Check if username already exists
        if (userDAO.exists(user.getUsername())) {
            System.out.println("[AuthenticationService] Registration failed: username '"
                    + user.getUsername() + "' already exists.");
            return false;
        }

        // Check if email already exists
        if (user.getEmail() != null && userDAO.isEmailTaken(user.getEmail())) {
            System.out.println("[AuthenticationService] Registration failed: email '"
                    + user.getEmail() + "' already in use.");
            return false;
        }

        userDAO.save(user.getUsername(), user);
        WalletApplicationService.getInstance().ensureWallet(user.getUsername());
        System.out.println("[AuthenticationService] User '" + user.getUsername() + "' registered successfully.");
        return true;
    }

    /**
     * Finds a user by their username.
     *
     * @param username the username to look up
     * @return the {@link User} if found, or {@code null}
     */
    public User findByUsername(String username) {
        return userDAO.findById(username);
    }
}
