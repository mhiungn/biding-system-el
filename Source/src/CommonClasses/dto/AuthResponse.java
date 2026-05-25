package CommonClasses.dto;

import CommonClasses.User;

import java.io.Serializable;
import java.util.Date;

public class AuthResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private final boolean success;
    private final User user;
    private final String token;
    private final Date expiresAt;
    private final String message;

    public AuthResponse(boolean success, User user, String token, Date expiresAt, String message) {
        this.success = success;
        this.user = user;
        this.token = token;
        this.expiresAt = expiresAt == null ? null : new Date(expiresAt.getTime());
        this.message = message;
    }

    public static AuthResponse success(User user, String token, Date expiresAt) {
        return new AuthResponse(true, user, token, expiresAt, null);
    }

    public static AuthResponse failure(String message) {
        return new AuthResponse(false, null, null, null, message);
    }

    public boolean isSuccess() {
        return success;
    }

    public User getUser() {
        return user;
    }

    public String getToken() {
        return token;
    }

    public Date getExpiresAt() {
        return expiresAt == null ? null : new Date(expiresAt.getTime());
    }

    public String getMessage() {
        return message;
    }
}
