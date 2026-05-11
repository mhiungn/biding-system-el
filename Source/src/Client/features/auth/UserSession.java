package Client.features.auth;

import CommonClasses.User;

/**
 * Fast in-memory holder for the currently logged-in user.
 */
public final class UserSession {
    private static volatile User currentUser;

    private UserSession() {
    }

    public static void setCurrentUser(User user) {
        currentUser = user;
    }

    public static User getCurrentUser() {
        return currentUser;
    }

    public static void clear() {
        currentUser = null;
    }
}
