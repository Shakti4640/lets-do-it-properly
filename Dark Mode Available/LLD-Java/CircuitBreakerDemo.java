/*
 * Circuit Breaker - Low Level Design
 * ----------------------------------
 * Protects a flaky downstream service. Implements the classic three states
 * via the State pattern:
 *   CLOSED      -> calls pass through; opens after N consecutive failures
 *   OPEN        -> calls fail fast; after a cooldown moves to HALF_OPEN
 *   HALF_OPEN   -> allows a trial call; success closes, failure re-opens
 *
 * Patterns used: State, Strategy-style protected call.
 *
 * Note: uses a simulated clock for deterministic output.
 */

public class CircuitBreakerDemo {
    public static void main(String[] args) {
        CircuitBreaker breaker = new CircuitBreaker(3, 5000); // 3 fails -> open, 5s cooldown
        FlakyService service = new FlakyService();

        System.out.println("=== Circuit Breaker Demo ===");
        long now = 0;
        // Force service to fail
        service.setHealthy(false);
        for (int i = 1; i <= 4; i++) {
            call(breaker, service, now);
            now += 500;
        }
        // Now OPEN -> fail fast
        call(breaker, service, now);

        // Wait past cooldown -> HALF_OPEN trial. Service recovered.
        service.setHealthy(true);
        now += 6000;
        call(breaker, service, now); // half-open trial succeeds -> CLOSED
        call(breaker, service, now + 100);
    }

    static void call(CircuitBreaker breaker, FlakyService service, long now) {
        try {
            String result = breaker.call(() -> service.request(), now);
            System.out.println("t=" + now + " [" + breaker.getState() + "] OK: " + result);
        } catch (Exception e) {
            System.out.println("t=" + now + " [" + breaker.getState() + "] FAIL: " + e.getMessage());
        }
    }
}

interface Supplier<T> { T get() throws Exception; }

class FlakyService {
    private boolean healthy = true;
    void setHealthy(boolean h) { healthy = h; }
    String request() throws Exception {
        if (!healthy) throw new RuntimeException("downstream error");
        return "response-200";
    }
}

enum BreakerState { CLOSED, OPEN, HALF_OPEN }

class CircuitBreaker {
    private final int failureThreshold;
    private final long cooldownMillis;
    private BreakerState state = BreakerState.CLOSED;
    private int consecutiveFailures = 0;
    private long openedAt = 0;

    CircuitBreaker(int failureThreshold, long cooldownMillis) {
        this.failureThreshold = failureThreshold;
        this.cooldownMillis = cooldownMillis;
    }

    BreakerState getState() { return state; }

    <T> T call(Supplier<T> action, long now) throws Exception {
        if (state == BreakerState.OPEN) {
            if (now - openedAt >= cooldownMillis) {
                state = BreakerState.HALF_OPEN;
            } else {
                throw new RuntimeException("circuit OPEN - fail fast");
            }
        }
        try {
            T result = action.get();
            onSuccess();
            return result;
        } catch (Exception e) {
            onFailure(now);
            throw e;
        }
    }

    private void onSuccess() {
        consecutiveFailures = 0;
        state = BreakerState.CLOSED;
    }

    private void onFailure(long now) {
        consecutiveFailures++;
        if (state == BreakerState.HALF_OPEN || consecutiveFailures >= failureThreshold) {
            state = BreakerState.OPEN;
            openedAt = now;
        }
    }
}
