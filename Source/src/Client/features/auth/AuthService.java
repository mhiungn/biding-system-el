package Client.features.auth;

import CommonClasses.Bidder;
import CommonClasses.User;
import Server.dao.UserDAO;

/**
 * Handles auth validation and persistence through MySQL-backed DAO.
 */
public final class AuthService {
    private final UserDAO userDAO = UserDAO.getInstance();

    public boolean passwordsMatch(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return a.equals(b);
    }

    public boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public boolean isValidEmail(String email) {
        return isNotBlank(email) && email.contains("@");
    }

    public String register(String username, String email, String password) {
        String normalizedUsername = username == null ? "" : username.trim();
        String normalizedEmail = email == null ? "" : email.trim();

        if (!isNotBlank(normalizedUsername) || !isNotBlank(password) || !isValidEmail(normalizedEmail)) {
            return "Invalid signup data.";
        }
        if (userDAO.exists(normalizedUsername)) {
            return "Username already exists.";
        }
        if (userDAO.isEmailTaken(normalizedEmail)) {
            return "Email already exists.";
        }

        User newUser = new Bidder(normalizedUsername, password, normalizedEmail);
        userDAO.save(normalizedUsername, newUser);
        UserSession.setCurrentUser(newUser);
        return null;
    }

    public User login(String username, String password) {
        String normalizedUsername = username == null ? "" : username.trim();
        if (!isNotBlank(normalizedUsername) || !isNotBlank(password)) {
            return null;
        }

        User user = userDAO.authenticate(normalizedUsername, password);
        UserSession.setCurrentUser(user);
        return user;
    }
}
