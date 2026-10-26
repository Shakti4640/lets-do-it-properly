/*
 * API Throttling System - Low Level Design
 * ----------------------------------------
 * Per-client request throttling using a Token Bucket algorithm. Each client
 * gets an independent bucket that refills at a fixed rate.
 *
 * Key classes:
 *   - TokenBucket (capacity + refill rate)
 *   - ThrottlingService (per-client buckets, allowRequest)
 *
 * Patterns used: Strategy-ready throttler, per-key state.
 *
 * Note: uses a simulated clock so output is deterministic.
 */

import java.util.HashMap;
import java.util.Map;

public class APIThrottlingDemo {
    public static void main(String[] args) {
        // capacity 5 tokens, refill 1 token per 1000ms
        ThrottlingService service = new ThrottlingService(5, 1, 1000);
        System.out.println("=== API Throttling Demo ===");

        long now = 0;
        // Burst of 7 from clientA at t=0; only 5 should pass
        for (int i = 1; i <= 7; i++) {
            boolean allowed = service.allowRequest("clientA", now);
            System.out.println("t=" + now + "ms clientA req#" + i + " -> " + (allowed ? "ALLOW" : "THROTTLE"));
        }
        // After 3 seconds, 3 tokens refilled
        now = 3000;
        for (int i = 8; i <= 11; i++) {
            boolean allowed = service.allowRequest("clientA", now);
            System.out.println("t=" + now + "ms clientA req#" + i + " -> " + (allowed ? "ALLOW" : "THROTTLE"));
        }
        // Independent client
        System.out.println("clientB req#1 -> " + (service.allowRequest("clientB", 3000) ? "ALLOW" : "THROTTLE"));
    }
}

class TokenBucket {
    private final int capacity;
    private final int refillTokens;
    private final long refillIntervalMs;
    private double tokens;
    private long lastRefillTime;

    TokenBucket(int capacity, int refillTokens, long refillIntervalMs, long startTime) {
        this.capacity = capacity;
        this.refillTokens = refillTokens;
        this.refillIntervalMs = refillIntervalMs;
        this.tokens = capacity;
        this.lastRefillTime = startTime;
    }

    private void refill(long now) {
        if (now <= lastRefillTime) return;
        long elapsed = now - lastRefillTime;
        double added = (double) elapsed / refillIntervalMs * refillTokens;
        if (added > 0) {
            tokens = Math.min(capacity, tokens + added);
            lastRefillTime = now;
        }
    }

    boolean tryConsume(long now) {
        refill(now);
        if (tokens >= 1) { tokens -= 1; return true; }
        return false;
    }
}

class ThrottlingService {
    private final int capacity;
    private final int refillTokens;
    private final long refillIntervalMs;
    private final Map<String, TokenBucket> buckets = new HashMap<String, TokenBucket>();

    ThrottlingService(int capacity, int refillTokens, long refillIntervalMs) {
        this.capacity = capacity; this.refillTokens = refillTokens; this.refillIntervalMs = refillIntervalMs;
    }

    boolean allowRequest(String clientId, long now) {
        TokenBucket bucket = buckets.computeIfAbsent(clientId,
                k -> new TokenBucket(capacity, refillTokens, refillIntervalMs, now));
        return bucket.tryConsume(now);
    }
}
