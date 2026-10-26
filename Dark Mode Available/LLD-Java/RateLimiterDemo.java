/*
 * Rate Limiter (LLD)
 * ------------------
 * Pluggable rate limiting with three classic algorithms behind one interface.
 *
 * Key classes / abstractions:
 *   - RateLimiter            : Strategy interface (allow(now) -> boolean)
 *   - TokenBucketLimiter     : refills tokens at a fixed rate up to a burst capacity
 *   - FixedWindowLimiter     : counts requests per fixed time window
 *   - SlidingWindowLimiter   : sliding log of timestamps for smooth limiting
 *   - RateLimiterFactory     : Factory to build a limiter by type
 *
 * Patterns used: Strategy (algorithms), Factory (creation).
 * Time is injected (millis) so the demo is deterministic without real sleeping.
 */
import java.util.*;

public class RateLimiterDemo {
    public static void main(String[] args) {
        long t = 1_000_000L;

        System.out.println("=== Token Bucket: capacity 5, refill 1 token / 200ms ===");
        RateLimiter tb = RateLimiterFactory.create(LimiterType.TOKEN_BUCKET, 5, 200);
        for (int i = 0; i < 7; i++) System.out.println("  req@" + (t) + " -> " + tb.allow(t)); // 5 ok, 2 denied
        t += 500; // 500ms later -> ~2 tokens refilled
        System.out.println("  after 500ms:");
        for (int i = 0; i < 3; i++) System.out.println("  req@" + t + " -> " + tb.allow(t));

        System.out.println("\n=== Fixed Window: 3 requests / 1000ms ===");
        RateLimiter fw = RateLimiterFactory.create(LimiterType.FIXED_WINDOW, 3, 1000);
        long base = 2_000_000L;
        for (int i = 0; i < 4; i++) System.out.println("  req@" + (base + i * 100) + " -> " + fw.allow(base + i * 100));
        System.out.println("  next window:");
        System.out.println("  req@" + (base + 1100) + " -> " + fw.allow(base + 1100));

        System.out.println("\n=== Sliding Window: 3 requests / 1000ms ===");
        RateLimiter sw = RateLimiterFactory.create(LimiterType.SLIDING_WINDOW, 3, 1000);
        long s = 3_000_000L;
        System.out.println("  req@" + s + "      -> " + sw.allow(s));
        System.out.println("  req@" + (s + 200) + "  -> " + sw.allow(s + 200));
        System.out.println("  req@" + (s + 400) + "  -> " + sw.allow(s + 400));
        System.out.println("  req@" + (s + 600) + "  -> " + sw.allow(s + 600)); // denied
        System.out.println("  req@" + (s + 1300) + " -> " + sw.allow(s + 1300)); // first slid out -> ok
    }
}

enum LimiterType { TOKEN_BUCKET, FIXED_WINDOW, SLIDING_WINDOW }

interface RateLimiter {
    boolean allow(long nowMillis);
    String name();
}

class TokenBucketLimiter implements RateLimiter {
    private final double capacity;
    private final double refillPerMs;
    private double tokens;
    private long lastRefill;

    TokenBucketLimiter(int capacity, long refillIntervalMsPerToken) {
        this.capacity = capacity;
        this.refillPerMs = 1.0 / refillIntervalMsPerToken;
        this.tokens = capacity;
        this.lastRefill = -1;
    }

    public boolean allow(long now) {
        if (lastRefill < 0) lastRefill = now;
        double elapsed = now - lastRefill;
        if (elapsed > 0) {
            tokens = Math.min(capacity, tokens + elapsed * refillPerMs);
            lastRefill = now;
        }
        if (tokens >= 1.0) { tokens -= 1.0; return true; }
        return false;
    }
    public String name() { return "TokenBucket"; }
}

class FixedWindowLimiter implements RateLimiter {
    private final int limit;
    private final long windowMs;
    private long windowStart = -1;
    private int count = 0;

    FixedWindowLimiter(int limit, long windowMs) { this.limit = limit; this.windowMs = windowMs; }

    public boolean allow(long now) {
        if (windowStart < 0 || now - windowStart >= windowMs) {
            windowStart = now;
            count = 0;
        }
        if (count < limit) { count++; return true; }
        return false;
    }
    public String name() { return "FixedWindow"; }
}

class SlidingWindowLimiter implements RateLimiter {
    private final int limit;
    private final long windowMs;
    private final Deque<Long> log = new ArrayDeque<>();

    SlidingWindowLimiter(int limit, long windowMs) { this.limit = limit; this.windowMs = windowMs; }

    public boolean allow(long now) {
        while (!log.isEmpty() && now - log.peekFirst() >= windowMs) log.pollFirst();
        if (log.size() < limit) { log.addLast(now); return true; }
        return false;
    }
    public String name() { return "SlidingWindow"; }
}

class RateLimiterFactory {
    static RateLimiter create(LimiterType type, int limit, long timeParamMs) {
        switch (type) {
            case TOKEN_BUCKET:   return new TokenBucketLimiter(limit, timeParamMs);
            case FIXED_WINDOW:   return new FixedWindowLimiter(limit, timeParamMs);
            case SLIDING_WINDOW: return new SlidingWindowLimiter(limit, timeParamMs);
            default: throw new IllegalArgumentException("Unknown limiter: " + type);
        }
    }
}
