/*
 * Retry with Backoff Mechanism - Low Level Design
 * -----------------------------------------------
 * Retries a failing operation with configurable backoff strategies:
 * fixed delay and exponential backoff with optional jitter. Caps the number
 * of attempts.
 *
 * Key classes:
 *   - BackoffStrategy (+ FixedBackoff, ExponentialBackoff)
 *   - RetryPolicy (max attempts + strategy)
 *   - RetryExecutor (runs the operation, computes delays)
 *
 * Patterns used: Strategy (backoff), Command (operation).
 *
 * Note: prints computed delays instead of actually sleeping for determinism.
 */

public class RetryWithBackoffDemo {
    public static void main(String[] args) {
        System.out.println("=== Retry With Backoff Demo ===");

        // Exponential backoff: base 100ms, factor 2, max 5 attempts
        RetryPolicy expPolicy = new RetryPolicy(5, new ExponentialBackoff(100, 2.0, 2000));
        RetryExecutor executor = new RetryExecutor(expPolicy);

        // Operation fails 3 times, then succeeds on 4th attempt
        FlakyOperation op = new FlakyOperation(3);
        String result = executor.execute(op);
        System.out.println("Final result: " + result);

        System.out.println("--- Fixed backoff, operation always fails ---");
        RetryPolicy fixedPolicy = new RetryPolicy(3, new FixedBackoff(500));
        RetryExecutor exec2 = new RetryExecutor(fixedPolicy);
        String r2 = exec2.execute(new FlakyOperation(99));
        System.out.println("Final result: " + r2);
    }
}

interface Operation<T> {
    T run() throws Exception;
}

class FlakyOperation implements Operation<String> {
    private int failuresLeft;
    private int attempt = 0;
    FlakyOperation(int failuresLeft) { this.failuresLeft = failuresLeft; }
    public String run() throws Exception {
        attempt++;
        if (failuresLeft-- > 0) throw new RuntimeException("transient error");
        return "success-on-attempt-" + attempt;
    }
}

interface BackoffStrategy {
    long computeDelay(int attempt); // attempt is 1-based
}

class FixedBackoff implements BackoffStrategy {
    private final long delay;
    FixedBackoff(long delay) { this.delay = delay; }
    public long computeDelay(int attempt) { return delay; }
}

class ExponentialBackoff implements BackoffStrategy {
    private final long base; private final double factor; private final long max;
    ExponentialBackoff(long base, double factor, long max) { this.base = base; this.factor = factor; this.max = max; }
    public long computeDelay(int attempt) {
        long delay = (long) (base * Math.pow(factor, attempt - 1));
        return Math.min(delay, max);
    }
}

class RetryPolicy {
    final int maxAttempts;
    final BackoffStrategy backoff;
    RetryPolicy(int maxAttempts, BackoffStrategy backoff) { this.maxAttempts = maxAttempts; this.backoff = backoff; }
}

class RetryExecutor {
    private final RetryPolicy policy;
    RetryExecutor(RetryPolicy policy) { this.policy = policy; }

    <T> T execute(Operation<T> operation) {
        Exception last = null;
        for (int attempt = 1; attempt <= policy.maxAttempts; attempt++) {
            try {
                T result = operation.run();
                System.out.println("Attempt " + attempt + " succeeded.");
                return result;
            } catch (Exception e) {
                last = e;
                if (attempt < policy.maxAttempts) {
                    long delay = policy.backoff.computeDelay(attempt);
                    System.out.println("Attempt " + attempt + " failed (" + e.getMessage() + "). Backing off " + delay + "ms");
                    // In production: Thread.sleep(delay);
                } else {
                    System.out.println("Attempt " + attempt + " failed. No retries left.");
                }
            }
        }
        return null; // exhausted
    }
}
