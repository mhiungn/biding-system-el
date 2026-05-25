package Client.features.auth;

import Client.core.network.NetworkRequestClient;
import Client.core.network.NetworkPushManager;
import CommonClasses.User;
import CommonClasses.dto.AuthResponse;
import Packets.MessageType;
import Packets.PacketMessage;
import Server.dao.UserDAO;
import Server.service.WalletApplicationService;

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

        User newUser = new User(normalizedUsername, password, normalizedEmail, "USER");
        if (NetworkRequestClient.isEnabled()) {
            try {
                PacketMessage response = NetworkRequestClient.request(
                        MessageType.REGISTER_REQUEST, newUser, MessageType.REGISTER_RESPONSE);
                User authenticatedUser = applyAuthPayload(response.getPayload());
                if (authenticatedUser != null) {
                    return null;
                }
                return "Signup failed on server.";
            } catch (IOException e) {
                System.err.println("[AuthService] Network register unavailable, using DAO fallback: "
                        + e.getMessage());
            }
        }

        userDAO.save(normalizedUsername, newUser);
        WalletApplicationService.getInstance().ensureWallet(normalizedUsername);
        SessionManager.clearToken();
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
                        new User(normalizedUsername, password, null,"USER"),
                        MessageType.LOGIN_RESPONSE);
                User authenticatedUser = applyAuthPayload(response.getPayload());
                if (authenticatedUser != null) {
                    return authenticatedUser;
                }
                SessionManager.setCurrentUser(null);
                return null;
            } catch (IOException e) {
                System.err.println("[AuthService] Network login unavailable, using DAO fallback: "
                        + e.getMessage());
            }
        }
        User user = userDAO.authenticate(normalizedUsername, password);
        SessionManager.clearToken();
        SessionManager.setCurrentUser(user);
        return user;
    }

    private User applyAuthPayload(Object payload) {
        if (payload instanceof AuthResponse) {
            AuthResponse authResponse = (AuthResponse) payload;
            if (authResponse.isSuccess() && authResponse.getUser() != null
                    && authResponse.getToken() != null && !authResponse.getToken().isBlank()) {
                SessionManager.setCurrentSession(
                        authResponse.getUser(),
                        authResponse.getToken(),
                        authResponse.getExpiresAt());
                NetworkPushManager.getInstance().startIfPossible();
                return authResponse.getUser();
            }
            SessionManager.setCurrentUser(null);
            return null;
        }

        if (payload instanceof User) {
            User user = (User) payload;
            SessionManager.clearToken();
            SessionManager.setCurrentUser(user);
            return user;
        }

        SessionManager.setCurrentUser(null);
        return null;
    }
}
