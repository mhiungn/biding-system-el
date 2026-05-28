package Server.service;

import CommonClasses.User;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SessionRegistry {
    private static final long DEFAULT_SESSION_TTL_MILLIS = 8L * 60L * 60L * 1000L;
    private static final int TOKEN_BYTES = 32;

    private static volatile SessionRegistry instance;

    public static SessionRegistry getInstance() {
        if (instance == null) {
            synchronized (SessionRegistry.class) {
                if (instance == null) {
                    instance = new SessionRegistry();
                }
            }
        }
        return instance;
    }

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, AuthenticatedSession> sessions = new ConcurrentHashMap<>();

    private SessionRegistry() {
    }

    public AuthenticatedSession createSession(User user) {
        if (user == null || isBlank(user.getUsername())) {
            throw new IllegalArgumentException("Authenticated user is required");
        }

        String token;
        do {
            token = generateToken();
        } while (sessions.containsKey(token));

        Date expiresAt = new Date(System.currentTimeMillis() + DEFAULT_SESSION_TTL_MILLIS);
        AuthenticatedSession session = new AuthenticatedSession(
                token,
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getProfileImageUrl(),
                expiresAt);
        sessions.put(token, session);
        return session;
    }

    public AuthenticatedSession authenticate(String token) {
        if (isBlank(token)) {
            return null;
        }

        AuthenticatedSession session = sessions.get(token);
        if (session == null) {
            return null;
        }
        if (session.isExpired()) {
            sessions.remove(token);
            return null;
        }
        return session;
    }

    public void invalidate(String token) {
        if (!isBlank(token)) {
            sessions.remove(token);
        }
    }

    public void clearAll() {
        sessions.clear();
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static final class AuthenticatedSession {
        private final String token;
        private final String username;
        private final String email;
        private final String role;
        private final String profileImageUrl;
        private final Date expiresAt;

        private AuthenticatedSession(String token, String username, String email, String role,
                                     String profileImageUrl, Date expiresAt) {
            this.token = token;
            this.username = username;
            this.email = email;
            this.role = role;
            this.profileImageUrl = profileImageUrl;
            this.expiresAt = new Date(expiresAt.getTime());
        }

        public String getToken() {
            return token;
        }

        public String getUsername() {
            return username;
        }

        public String getEmail() {
            return email;
        }

        public String getRole() {
            return role;
        }

        public String getProfileImageUrl() {
            return profileImageUrl;
        }

        public Date getExpiresAt() {
            return new Date(expiresAt.getTime());
        }

        public boolean isExpired() {
            return expiresAt.getTime() <= System.currentTimeMillis();
        }

        public User toSafeUser() {
            return new User(username, null, email, role, null, null, profileImageUrl);
        }
    }
}
