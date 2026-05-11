package Client.features.auth;

/**
 * Placeholder for login / registration orchestration (server, validation, tokens).
 */
public final class AuthService {

    public boolean passwordsMatch(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return a.equals(b);
    }
}
