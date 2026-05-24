package Client.features.auth;

import Client.core.network.NetworkRequestClient;
import CommonClasses.Bidder;
import CommonClasses.User;
import Packets.MessageType;
import Packets.PacketMessage;
import Server.dao.UserDAO;

import java.io.IOException;

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
        if (NetworkRequestClient.isEnabled()) {
            try {
                PacketMessage response = NetworkRequestClient.request(
                        MessageType.REGISTER_REQUEST, newUser, MessageType.REGISTER_RESPONSE);
                if (response.getPayload() instanceof User) {
                    SessionManager.setCurrentUser((User) response.getPayload());
                    return null;
                }
                return "Signup failed on server.";
            } catch (IOException e) {
                System.err.println("[AuthService] Network register unavailable, using DAO fallback: "
                        + e.getMessage());
            }
        }

        userDAO.save(normalizedUsername, newUser);
        SessionManager.setCurrentUser(newUser);
        return null;
    }

    public User login(String username, String password) {
        String normalizedUsername = username == null ? "" : username.trim();
        if (!isNotBlank(normalizedUsername) || !isNotBlank(password)) {
            return null;
        }

        if (NetworkRequestClient.isEnabled()) {
            try {
                PacketMessage response = NetworkRequestClient.request(
                        MessageType.LOGIN_REQUEST,
                        new Bidder(normalizedUsername, password, null),
                        MessageType.LOGIN_RESPONSE);
                if (response.getPayload() instanceof User) {
                    User user = (User) response.getPayload();
                    SessionManager.setCurrentUser(user);
                    return user;
                }
                SessionManager.setCurrentUser(null);
                return null;
            } catch (IOException e) {
                System.err.println("[AuthService] Network login unavailable, using DAO fallback: "
                        + e.getMessage());
            }
        }
        User user = userDAO.authenticate(normalizedUsername, password);
        SessionManager.setCurrentUser(user);
        return user;
    }
}
