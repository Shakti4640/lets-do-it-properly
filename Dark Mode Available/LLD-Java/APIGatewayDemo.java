/*
 * API Gateway (LLD)
 * -----------------
 * A minimal API gateway: a route registry maps path prefixes to backend
 * handlers, and an ordered middleware chain (auth, rate limit, logging) runs
 * before dispatch using the Chain-of-Responsibility pattern.
 *
 * Key classes / abstractions:
 *   - HttpRequest / HttpResponse : simple request/response value objects
 *   - Backend                    : terminal handler that produces a response
 *   - Middleware                 : Chain-of-Responsibility link (may short-circuit)
 *   - AuthMiddleware / RateLimitMiddleware / LoggingMiddleware
 *   - ApiGateway                 : route registry + chain assembly + dispatch
 *
 * Patterns used: Chain of Responsibility (middleware), Strategy (Backend), Facade.
 */
import java.util.*;
import java.util.function.Function;

public class APIGatewayDemo {
    public static void main(String[] args) {
        ApiGateway gateway = new ApiGateway();

        gateway.route("/users", req ->
                new HttpResponse(200, "user-service: " + req.path));
        gateway.route("/orders", req ->
                new HttpResponse(200, "order-service: " + req.path));

        // Middleware order matters: logging -> auth -> rate limit -> dispatch.
        gateway.use(new LoggingMiddleware());
        gateway.use(new AuthMiddleware(new HashSet<>(Arrays.asList("token-abc", "token-xyz"))));
        gateway.use(new RateLimitMiddleware(2)); // 2 requests allowed per client

        System.out.println("=== Authorized request to /users ===");
        System.out.println(gateway.handle(req("/users", "token-abc", "alice")));

        System.out.println("\n=== Missing token -> 401 ===");
        System.out.println(gateway.handle(req("/orders", null, "bob")));

        System.out.println("\n=== Unknown route -> 404 ===");
        System.out.println(gateway.handle(req("/payments", "token-abc", "alice")));

        System.out.println("\n=== Rate limit: alice's 3rd request -> 429 ===");
        gateway.handle(req("/orders", "token-abc", "alice")); // 2nd allowed
        System.out.println(gateway.handle(req("/orders", "token-abc", "alice"))); // 3rd blocked
    }

    private static HttpRequest req(String path, String token, String client) {
        Map<String, String> headers = new HashMap<>();
        if (token != null) headers.put("Authorization", token);
        headers.put("X-Client-Id", client);
        return new HttpRequest("GET", path, headers);
    }
}

class HttpRequest {
    final String method;
    final String path;
    final Map<String, String> headers;
    HttpRequest(String method, String path, Map<String, String> headers) {
        this.method = method; this.path = path; this.headers = headers;
    }
    String header(String name) { return headers.get(name); }
    public String toString() { return method + " " + path; }
}

class HttpResponse {
    final int status;
    final String body;
    HttpResponse(int status, String body) { this.status = status; this.body = body; }
    public String toString() { return status + " " + body; }
}

interface Backend {
    HttpResponse handle(HttpRequest request);
}

abstract class Middleware {
    private Middleware next;
    Middleware setNext(Middleware next) { this.next = next; return next; }

    // Pass control down the chain; terminal dispatch happens via the supplied function.
    final HttpResponse proceed(HttpRequest req, Function<HttpRequest, HttpResponse> dispatch) {
        if (next != null) return next.handle(req, dispatch);
        return dispatch.apply(req);
    }
    abstract HttpResponse handle(HttpRequest req, Function<HttpRequest, HttpResponse> dispatch);
}

class LoggingMiddleware extends Middleware {
    HttpResponse handle(HttpRequest req, Function<HttpRequest, HttpResponse> dispatch) {
        System.out.println("  [log] -> " + req);
        HttpResponse resp = proceed(req, dispatch);
        System.out.println("  [log] <- " + resp.status);
        return resp;
    }
}

class AuthMiddleware extends Middleware {
    private final Set<String> validTokens;
    AuthMiddleware(Set<String> validTokens) { this.validTokens = validTokens; }
    HttpResponse handle(HttpRequest req, Function<HttpRequest, HttpResponse> dispatch) {
        String token = req.header("Authorization");
        if (token == null || !validTokens.contains(token)) {
            return new HttpResponse(401, "Unauthorized");
        }
        return proceed(req, dispatch);
    }
}

class RateLimitMiddleware extends Middleware {
    private final int limit;
    private final Map<String, Integer> counts = new HashMap<>();
    RateLimitMiddleware(int limit) { this.limit = limit; }
    HttpResponse handle(HttpRequest req, Function<HttpRequest, HttpResponse> dispatch) {
        String client = req.header("X-Client-Id");
        int used = counts.getOrDefault(client, 0);
        if (used >= limit) {
            return new HttpResponse(429, "Too Many Requests for " + client);
        }
        counts.put(client, used + 1);
        return proceed(req, dispatch);
    }
}

class ApiGateway {
    private final Map<String, Backend> routes = new LinkedHashMap<>();
    private final List<Middleware> middlewares = new ArrayList<>();

    void route(String pathPrefix, Backend backend) { routes.put(pathPrefix, backend); }
    void use(Middleware middleware) { middlewares.add(middleware); }

    HttpResponse handle(HttpRequest request) {
        Function<HttpRequest, HttpResponse> dispatch = req -> {
            Backend backend = match(req.path);
            if (backend == null) return new HttpResponse(404, "Not Found: " + req.path);
            return backend.handle(req);
        };
        if (middlewares.isEmpty()) return dispatch.apply(request);

        // Link the chain freshly each request to keep links stateless about order.
        for (int i = 0; i < middlewares.size() - 1; i++) {
            middlewares.get(i).setNext(middlewares.get(i + 1));
        }
        return middlewares.get(0).handle(request, dispatch);
    }

    private Backend match(String path) {
        for (Map.Entry<String, Backend> e : routes.entrySet()) {
            if (path.equals(e.getKey()) || path.startsWith(e.getKey() + "/")) return e.getValue();
        }
        return null;
    }
}
