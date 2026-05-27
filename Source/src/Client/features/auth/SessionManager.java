package Client.features.auth;

import CommonClasses.User;

import java.util.Date;

/**
 * Fast in-memory holder for the currently logged-in user.
 */
public final class SessionManager {
    private static volatile User currentUser;
    private static volatile String authToken;
    private static volatile Date authTokenExpiresAt;

    private SessionManager() {
    }

    public static void setCurrentUser(User user) {
        currentUser = user;
        if (user == null) {
            clearToken();
        }
    }

    public static void setCurrentSession(User user, String token, Date expiresAt) {
        currentUser = user;
        authToken = token;
        authTokenExpiresAt = expiresAt == null ? null : new Date(expiresAt.getTime());
    }

    public static User getCurrentUser() {
        return currentUser;
    }

    public static String getAuthToken() {
        return authToken;
    }

    public static Date getAuthTokenExpiresAt() {
        return authTokenExpiresAt == null ? null : new Date(authTokenExpiresAt.getTime());
    }

    public static void setAuthToken(String token, Date expiresAt) {
        authToken = token;
        authTokenExpiresAt = expiresAt == null ? null : new Date(expiresAt.getTime());
    }

    public static void clear() {
        currentUser = null;
        clearToken();
    }

    public static void clearToken() {
        authToken = null;
        authTokenExpiresAt = null;
    }
}
