/*
 * Authentication System - Low Level Design
 * ----------------------------------------
 * User signup/login with salted password hashing (demo hash), session token
 * issuance and validation, and logout.
 *
 * Key classes:
 *   - User (salted hashed password)
 *   - PasswordHasher
 *   - Session / SessionManager (token -> session, TTL)
 *   - AuthService (facade)
 *
 * Patterns used: Facade, token-based sessions, Encapsulation.
 *
 * Note: hashing here is a simple demo (NOT for production - use bcrypt/argon2).
 */

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AuthenticationSystemDemo {
    public static void main(String[] args) {
        AuthService auth = new AuthService(3600_000L); // 1h sessions
        System.out.println("=== Authentication System Demo ===");

        auth.signup("alice", "S3cret!");
        auth.signup("alice", "again"); // duplicate -> reject

        String token = auth.login("alice", "S3cret!");
        System.out.println("Token: " + token);
        auth.login("alice", "wrong"); // bad password

        System.out.println("Validate token: " + auth.validate(token));
        auth.logout(token);
        System.out.println("Validate after logout: " + auth.validate(token));
    }
}

class PasswordHasher {
    static String generateSalt() { return UUID.randomUUID().toString().substring(0, 8); }
    // Demo-only hash: do NOT use in production.
    static String hash(String password, String salt) {
        return Integer.toHexString((salt + password).hashCode());
    }
}

class User {
    final String username; final String salt; final String passwordHash;
    User(String username, String salt, String passwordHash) {
        this.username = username; this.salt = salt; this.passwordHash = passwordHash;
    }
}

class Session {
    final String token; final String username; final long expiresAt;
    Session(String token, String username, long expiresAt) { this.token = token; this.username = username; this.expiresAt = expiresAt; }
    boolean isExpired() { return System.currentTimeMillis() > expiresAt; }
}

class SessionManager {
    private final Map<String, Session> sessions = new HashMap<String, Session>();
    private final long ttlMillis;
    SessionManager(long ttlMillis) { this.ttlMillis = ttlMillis; }

    String createSession(String username) {
        String token = UUID.randomUUID().toString();
        sessions.put(token, new Session(token, username, System.currentTimeMillis() + ttlMillis));
        return token;
    }
    String validate(String token) {
        Session s = sessions.get(token);
        if (s == null || s.isExpired()) return null;
        return s.username;
    }
    void invalidate(String token) { sessions.remove(token); }
}

class AuthService {
    private final Map<String, User> users = new HashMap<String, User>();
    private final SessionManager sessionManager;

    AuthService(long sessionTtl) { this.sessionManager = new SessionManager(sessionTtl); }

    boolean signup(String username, String password) {
        if (users.containsKey(username)) {
            System.out.println("Signup failed: '" + username + "' already exists");
            return false;
        }
        String salt = PasswordHasher.generateSalt();
        users.put(username, new User(username, salt, PasswordHasher.hash(password, salt)));
        System.out.println("Signed up '" + username + "'");
        return true;
    }

    String login(String username, String password) {
        User u = users.get(username);
        if (u == null) { System.out.println("Login failed: no such user"); return null; }
        if (!u.passwordHash.equals(PasswordHasher.hash(password, u.salt))) {
            System.out.println("Login failed: bad password for '" + username + "'");
            return null;
        }
        String token = sessionManager.createSession(username);
        System.out.println("'" + username + "' logged in");
        return token;
    }

    String validate(String token) {
        if (token == null) return "INVALID";
        String user = sessionManager.validate(token);
        return user == null ? "INVALID" : "VALID (" + user + ")";
    }

    void logout(String token) {
        sessionManager.invalidate(token);
        System.out.println("Logged out");
    }
}
