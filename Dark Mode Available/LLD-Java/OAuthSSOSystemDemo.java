/*
 * OAuth 2.0 SSO System - Low Level Design
 * ---------------------------------------
 * Simulates the authorization-code flow deterministically: a client is
 * registered, the authorization server issues an auth code then exchanges it
 * for access/refresh tokens, a resource server validates tokens, and expired
 * access tokens are refreshed.
 *
 * Key classes / abstractions:
 *   - ClientApp (registered OAuth client)
 *   - AuthorizationServer (issues codes + tokens, validates them)
 *   - ResourceServer (guards a protected resource)
 *   - AuthCode, Token (value objects with deterministic expiry)
 *   - Clock (injected logical time for deterministic demo)
 *
 * Patterns used: Facade (AuthorizationServer), Factory (token creation), Strategy-free deterministic Clock.
 */

import java.util.HashMap;
import java.util.Map;

public class OAuthSSOSystemDemo {
    public static void main(String[] args) {
        Clock clock = new Clock(1000L);
        AuthorizationServer authServer = new AuthorizationServer(clock);
        ResourceServer resourceServer = new ResourceServer(authServer);

        System.out.println("=== OAuth 2.0 SSO System Demo ===");

        // 1. Client registration
        ClientApp client = authServer.registerClient("photo-app", "https://photo-app/callback");
        System.out.println("Registered client: " + client.clientId + " secret=" + client.clientSecret);

        // 2. User authorizes -> auth server issues an authorization code
        AuthCode code = authServer.authorize(client.clientId, "https://photo-app/callback", "alice");
        System.out.println("\n-- Authorization grant --");
        System.out.println("Issued auth code: " + code.value + " for user " + code.userId);

        // 3. Exchange code for tokens
        System.out.println("\n-- Token exchange --");
        Token token = authServer.exchangeCode(client.clientId, client.clientSecret, code.value);
        System.out.println("Access token : " + token.accessToken + " (expires@" + token.accessExpiry + ")");
        System.out.println("Refresh token: " + token.refreshToken);

        // 4. Resource server validates token
        System.out.println("\n-- Access protected resource --");
        System.out.println(resourceServer.getProfile(token.accessToken));

        // 5. Advance time past expiry -> access denied
        clock.advance(400L);
        System.out.println("\n-- After token expiry (t=" + clock.now() + ") --");
        System.out.println(resourceServer.getProfile(token.accessToken));

        // 6. Refresh the token
        System.out.println("\n-- Refresh flow --");
        Token refreshed = authServer.refresh(client.clientId, client.clientSecret, token.refreshToken);
        System.out.println("New access token: " + refreshed.accessToken + " (expires@" + refreshed.accessExpiry + ")");
        System.out.println(resourceServer.getProfile(refreshed.accessToken));
    }
}

class Clock {
    private long current;
    Clock(long start) { this.current = start; }
    long now() { return current; }
    void advance(long delta) { current += delta; }
}

class ClientApp {
    final String clientId;
    final String clientSecret;
    final String redirectUri;
    ClientApp(String clientId, String clientSecret, String redirectUri) {
        this.clientId = clientId; this.clientSecret = clientSecret; this.redirectUri = redirectUri;
    }
}

class AuthCode {
    final String value;
    final String clientId;
    final String userId;
    boolean used = false;
    AuthCode(String value, String clientId, String userId) {
        this.value = value; this.clientId = clientId; this.userId = userId;
    }
}

class Token {
    final String accessToken;
    final String refreshToken;
    final String userId;
    final long accessExpiry;
    Token(String accessToken, String refreshToken, String userId, long accessExpiry) {
        this.accessToken = accessToken; this.refreshToken = refreshToken;
        this.userId = userId; this.accessExpiry = accessExpiry;
    }
}

class AuthorizationServer {
    private static final long ACCESS_TTL = 300L;
    private final Clock clock;
    private int seq = 0;
    private final Map<String, ClientApp> clients = new HashMap<String, ClientApp>();
    private final Map<String, AuthCode> codes = new HashMap<String, AuthCode>();
    private final Map<String, Token> accessIndex = new HashMap<String, Token>();
    private final Map<String, String> refreshToUser = new HashMap<String, String>();

    AuthorizationServer(Clock clock) { this.clock = clock; }

    ClientApp registerClient(String name, String redirectUri) {
        String id = name + "-id";
        ClientApp app = new ClientApp(id, name + "-secret", redirectUri);
        clients.put(id, app);
        return app;
    }

    AuthCode authorize(String clientId, String redirectUri, String userId) {
        ClientApp app = clients.get(clientId);
        if (app == null || !app.redirectUri.equals(redirectUri)) {
            throw new IllegalArgumentException("Invalid client or redirect URI");
        }
        AuthCode code = new AuthCode("code-" + (++seq), clientId, userId);
        codes.put(code.value, code);
        return code;
    }

    Token exchangeCode(String clientId, String clientSecret, String codeValue) {
        ClientApp app = clients.get(clientId);
        if (app == null || !app.clientSecret.equals(clientSecret)) {
            throw new IllegalArgumentException("Client authentication failed");
        }
        AuthCode code = codes.get(codeValue);
        if (code == null || code.used || !code.clientId.equals(clientId)) {
            throw new IllegalArgumentException("Invalid or reused authorization code");
        }
        code.used = true;
        return issueToken(code.userId);
    }

    Token refresh(String clientId, String clientSecret, String refreshToken) {
        ClientApp app = clients.get(clientId);
        if (app == null || !app.clientSecret.equals(clientSecret)) {
            throw new IllegalArgumentException("Client authentication failed");
        }
        String userId = refreshToUser.get(refreshToken);
        if (userId == null) throw new IllegalArgumentException("Invalid refresh token");
        return issueToken(userId);
    }

    private Token issueToken(String userId) {
        Token token = new Token("at-" + (++seq), "rt-" + (++seq), userId, clock.now() + ACCESS_TTL);
        accessIndex.put(token.accessToken, token);
        refreshToUser.put(token.refreshToken, userId);
        return token;
    }

    String validateAccess(String accessToken) {
        Token token = accessIndex.get(accessToken);
        if (token == null) return null;
        if (clock.now() > token.accessExpiry) return null;
        return token.userId;
    }
}

class ResourceServer {
    private final AuthorizationServer authServer;
    ResourceServer(AuthorizationServer authServer) { this.authServer = authServer; }
    String getProfile(String accessToken) {
        String userId = authServer.validateAccess(accessToken);
        if (userId == null) return "401 Unauthorized: token invalid or expired";
        return "200 OK: profile of '" + userId + "' { plan: pro }";
    }
}
