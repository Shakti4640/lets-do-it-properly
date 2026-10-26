/*
 * Database Connection Pool (LLD)
 * ------------------------------
 * A fixed-size connection pool. Clients borrow and return connections; the pool
 * validates a connection on borrow (recreating broken ones), enforces a maximum
 * size, and fails fast when exhausted. Deterministic single-threaded demo.
 *
 * Key classes / abstractions:
 *   - Connection             : a pooled DB connection (id, valid flag)
 *   - ConnectionFactory      : Factory that creates fresh connections
 *   - ConnectionPool         : borrow/return, validate-on-borrow, capacity guard
 *   - PoolExhaustedException : thrown when no connection is available
 *
 * Patterns used: Object Pool, Factory (connection creation).
 */
import java.util.*;

public class DatabaseConnectionPoolDemo {
    public static void main(String[] args) {
        ConnectionFactory factory = new ConnectionFactory("jdbc:demo://localhost/app");
        ConnectionPool pool = new ConnectionPool(factory, 2); // max 2 connections

        System.out.println("=== Borrow up to capacity ===");
        Connection c1 = pool.borrow();
        Connection c2 = pool.borrow();
        System.out.println("borrowed: " + c1 + ", " + c2);
        System.out.println(pool.stats());

        System.out.println("\n=== Borrow when exhausted -> fails fast ===");
        try {
            pool.borrow();
        } catch (PoolExhaustedException e) {
            System.out.println("caught: " + e.getMessage());
        }

        System.out.println("\n=== Return one and borrow again (reuse) ===");
        pool.release(c1);
        Connection c3 = pool.borrow();
        System.out.println("reused same connection? " + (c3 == c1));
        System.out.println(pool.stats());

        System.out.println("\n=== Validate-on-borrow recreates a broken connection ===");
        pool.release(c3);
        c3.markBroken();
        Connection c4 = pool.borrow();
        System.out.println("got valid connection: " + c4.isValid() + " (id=" + c4.id + ")");
        System.out.println(pool.stats());

        System.out.println("\n=== Returning a foreign connection is rejected ===");
        try {
            pool.release(new Connection(999));
        } catch (IllegalArgumentException e) {
            System.out.println("caught: " + e.getMessage());
        }
    }
}

class Connection {
    final int id;
    private boolean valid = true;
    Connection(int id) { this.id = id; }
    boolean isValid() { return valid; }
    void markBroken() { valid = false; }
    public String toString() { return "Conn#" + id + (valid ? "" : "(broken)"); }
}

class ConnectionFactory {
    private final String url;
    private int nextId = 1;
    ConnectionFactory(String url) { this.url = url; }
    Connection create() {
        Connection c = new Connection(nextId++);
        System.out.println("  [factory] opened " + c + " to " + url);
        return c;
    }
}

class PoolExhaustedException extends RuntimeException {
    PoolExhaustedException(String message) { super(message); }
}

class ConnectionPool {
    private final ConnectionFactory factory;
    private final int maxSize;
    private final Deque<Connection> idle = new ArrayDeque<>();
    private final Set<Connection> inUse = new HashSet<>();
    private final Set<Connection> known = new HashSet<>(); // every connection minted by this pool
    private int created = 0;
    private long borrows = 0, recreations = 0;

    ConnectionPool(ConnectionFactory factory, int maxSize) {
        if (maxSize <= 0) throw new IllegalArgumentException("maxSize must be > 0");
        this.factory = factory;
        this.maxSize = maxSize;
    }

    Connection borrow() {
        Connection conn = idle.pollFirst();
        if (conn == null) {
            if (created >= maxSize) {
                throw new PoolExhaustedException("no connections available (max=" + maxSize + ")");
            }
            conn = factory.create();
            created++;
            known.add(conn);
        }
        // Validate on borrow: replace broken connections transparently.
        if (!conn.isValid()) {
            System.out.println("  [pool] discarding " + conn + ", creating replacement");
            known.remove(conn);
            created--;
            recreations++;
            conn = factory.create();
            created++;
            known.add(conn);
        }
        inUse.add(conn);
        borrows++;
        return conn;
    }

    void release(Connection conn) {
        if (!known.contains(conn)) {
            throw new IllegalArgumentException("connection " + conn + " was not created by this pool");
        }
        if (!inUse.remove(conn)) {
            return; // already idle; ignore double-release
        }
        idle.addLast(conn);
    }

    String stats() {
        return String.format("pool[max=%d created=%d idle=%d inUse=%d borrows=%d recreations=%d]",
                maxSize, created, idle.size(), inUse.size(), borrows, recreations);
    }
}
