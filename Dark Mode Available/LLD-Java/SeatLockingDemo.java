/*
 * BookMyShow Seat Locking - Low Level Design
 * ------------------------------------------
 * Focuses on the concurrency-critical seat-locking problem: temporarily
 * lock seats for a user during checkout, auto-expire locks, and confirm
 * booking only if locks are still held.
 *
 * Key classes:
 *   - SeatLock (owner + expiry)
 *   - SeatLockProvider (lock/unlock/validate with TTL)
 *   - BookingFlow (lock -> pay -> confirm)
 *
 * Patterns used: Lock manager, TTL expiry, thread-safe maps.
 *
 * Note: uses a simulated clock (millis param) for deterministic output.
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SeatLockingDemo {
    public static void main(String[] args) {
        SeatLockProvider lockProvider = new SeatLockProvider(5000); // 5s TTL
        BookingFlow flow = new BookingFlow(lockProvider);

        System.out.println("=== Seat Locking Demo ===");
        long now = 0;
        // Alice locks A1, A2
        flow.startCheckout("alice", "SHOW1", new String[]{"A1","A2"}, now);
        // Bob tries to lock A2 (held by Alice) -> fails
        flow.startCheckout("bob", "SHOW1", new String[]{"A2","A3"}, now + 1000);
        // Alice confirms within TTL -> success
        flow.confirm("alice", "SHOW1", new String[]{"A1","A2"}, now + 2000);

        // Carol locks A4, lets it expire, then tries to confirm
        flow.startCheckout("carol", "SHOW1", new String[]{"A4"}, now + 3000);
        flow.confirm("carol", "SHOW1", new String[]{"A4"}, now + 9000); // expired
    }
}

class SeatLock {
    final String seatId; final String owner; final long expiresAt;
    SeatLock(String seatId, String owner, long expiresAt) { this.seatId = seatId; this.owner = owner; this.expiresAt = expiresAt; }
    boolean isExpired(long now) { return now >= expiresAt; }
}

class SeatLockProvider {
    private final long ttlMillis;
    private final Map<String, SeatLock> locks = new HashMap<String, SeatLock>(); // key: show:seat
    SeatLockProvider(long ttlMillis) { this.ttlMillis = ttlMillis; }

    private String key(String show, String seat) { return show + ":" + seat; }

    synchronized boolean lockSeats(String user, String show, String[] seats, long now) {
        // verify all free first
        for (String seat : seats) {
            SeatLock existing = locks.get(key(show, seat));
            if (existing != null && !existing.isExpired(now) && !existing.owner.equals(user)) {
                System.out.println("  LOCK FAIL: seat " + seat + " held by " + existing.owner);
                return false;
            }
        }
        for (String seat : seats) {
            locks.put(key(show, seat), new SeatLock(seat, user, now + ttlMillis));
        }
        return true;
    }

    synchronized boolean validateLocks(String user, String show, String[] seats, long now) {
        for (String seat : seats) {
            SeatLock lock = locks.get(key(show, seat));
            if (lock == null || lock.isExpired(now) || !lock.owner.equals(user)) return false;
        }
        return true;
    }

    synchronized void releaseLocks(String show, String[] seats) {
        for (String seat : seats) locks.remove(key(show, seat));
    }
}

class BookingFlow {
    private final SeatLockProvider lockProvider;
    BookingFlow(SeatLockProvider lockProvider) { this.lockProvider = lockProvider; }

    void startCheckout(String user, String show, String[] seats, long now) {
        List<String> list = new ArrayList<String>();
        for (String s : seats) list.add(s);
        if (lockProvider.lockSeats(user, show, seats, now)) {
            System.out.println(user + " locked seats " + list + " (t=" + now + ")");
        } else {
            System.out.println(user + " could not lock " + list);
        }
    }

    void confirm(String user, String show, String[] seats, long now) {
        List<String> list = new ArrayList<String>();
        for (String s : seats) list.add(s);
        if (lockProvider.validateLocks(user, show, seats, now)) {
            lockProvider.releaseLocks(show, seats);
            System.out.println(user + " CONFIRMED booking for " + list + " (t=" + now + ")");
        } else {
            System.out.println(user + " confirm FAILED for " + list + " (locks expired/lost, t=" + now + ")");
        }
    }
}
