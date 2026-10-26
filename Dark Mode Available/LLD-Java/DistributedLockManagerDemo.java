/*
 * Distributed Lock Manager (LLD)
 * ------------------------------
 * Named locks with an owner and a TTL/lease. Leases expire against a simulated
 * clock, locks are reentrant for the same owner, and each grant returns a
 * monotonically increasing fencing token to guard against stale holders.
 *
 * Key classes / abstractions:
 *   - ManualClock        : deterministic, injectable time source
 *   - Lease              : owner, expiry, reentrancy hold count, fencing token
 *   - LockManager        : acquire / release / renew with TTL + fencing tokens
 *   - LockResult         : success flag + fencing token (or reason)
 *
 * Patterns used: Strategy-style injected clock (dependency injection),
 *                Facade (LockManager). Deterministic time, no real sleeping.
 */
import java.util.*;

public class DistributedLockManagerDemo {
    public static void main(String[] args) {
        ManualClock clock = new ManualClock(0);
        LockManager lm = new LockManager(clock);

        System.out.println("=== Acquire + reentrancy ===");
        LockResult r1 = lm.acquire("res-A", "node1", 100);
        System.out.println("node1 acquire res-A: " + r1);
        LockResult r1b = lm.acquire("res-A", "node1", 100); // reentrant
        System.out.println("node1 reacquire res-A: " + r1b + " (holds=" + lm.holdCount("res-A") + ")");

        System.out.println("\n=== Contention ===");
        LockResult r2 = lm.acquire("res-A", "node2", 100);
        System.out.println("node2 acquire res-A: " + r2);

        System.out.println("\n=== Release honors reentrancy ===");
        lm.release("res-A", "node1");
        System.out.println("after 1 release, still held by node1? holds=" + lm.holdCount("res-A"));
        lm.release("res-A", "node1");
        System.out.println("after 2nd release, owner=" + lm.ownerOf("res-A"));

        System.out.println("\n=== Lease expiry via clock advance ===");
        LockResult r3 = lm.acquire("res-B", "node2", 50);
        System.out.println("node2 acquire res-B: " + r3);
        clock.advance(60); // lease (50) now expired
        System.out.println("clock=" + clock.now() + ", node3 tries res-B...");
        LockResult r4 = lm.acquire("res-B", "node3", 50);
        System.out.println("node3 acquire res-B: " + r4 + " (token increased -> fencing)");

        System.out.println("\n=== Fencing token guard ===");
        System.out.println("stale write with token " + r3.token + " accepted? "
                + lm.validToken("res-B", r3.token));
        System.out.println("fresh write with token " + r4.token + " accepted? "
                + lm.validToken("res-B", r4.token));
    }
}

class ManualClock {
    private long t;
    ManualClock(long start) { this.t = start; }
    long now() { return t; }
    void advance(long delta) { t += delta; }
}

class Lease {
    String owner;
    long expiry;
    int holdCount;
    long fencingToken;
    Lease(String owner, long expiry, long fencingToken) {
        this.owner = owner; this.expiry = expiry; this.holdCount = 1; this.fencingToken = fencingToken;
    }
}

class LockResult {
    final boolean ok;
    final long token;
    final String reason;
    LockResult(boolean ok, long token, String reason) {
        this.ok = ok; this.token = token; this.reason = reason;
    }
    static LockResult granted(long token) { return new LockResult(true, token, "granted"); }
    static LockResult denied(String reason) { return new LockResult(false, -1, reason); }
    public String toString() { return ok ? ("OK token=" + token) : ("DENIED(" + reason + ")"); }
}

class LockManager {
    private final ManualClock clock;
    private final Map<String, Lease> locks = new HashMap<>();
    private long tokenSeq = 0;

    LockManager(ManualClock clock) { this.clock = clock; }

    private boolean expired(Lease l) { return clock.now() >= l.expiry; }

    LockResult acquire(String resource, String owner, long ttl) {
        Lease l = locks.get(resource);
        if (l != null && !expired(l)) {
            if (l.owner.equals(owner)) {        // reentrant
                l.holdCount++;
                l.expiry = clock.now() + ttl;   // renew on reentry
                return LockResult.granted(l.fencingToken);
            }
            return LockResult.denied("held by " + l.owner + " until " + l.expiry);
        }
        // free or expired -> grant fresh lease with a new (higher) fencing token
        long token = ++tokenSeq;
        locks.put(resource, new Lease(owner, clock.now() + ttl, token));
        return LockResult.granted(token);
    }

    void release(String resource, String owner) {
        Lease l = locks.get(resource);
        if (l == null) return;
        if (!l.owner.equals(owner)) throw new RuntimeException(owner + " does not own " + resource);
        if (--l.holdCount <= 0) locks.remove(resource);
    }

    boolean renew(String resource, String owner, long ttl) {
        Lease l = locks.get(resource);
        if (l == null || expired(l) || !l.owner.equals(owner)) return false;
        l.expiry = clock.now() + ttl;
        return true;
    }

    int holdCount(String resource) {
        Lease l = locks.get(resource);
        return (l == null || expired(l)) ? 0 : l.holdCount;
    }

    String ownerOf(String resource) {
        Lease l = locks.get(resource);
        return (l == null || expired(l)) ? "<none>" : l.owner;
    }

    // A write is only valid if it carries the current (highest) fencing token.
    boolean validToken(String resource, long token) {
        Lease l = locks.get(resource);
        return l != null && token == l.fencingToken;
    }
}
