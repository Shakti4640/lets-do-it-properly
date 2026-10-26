/*
 * Load Balancer (LLD)
 * -------------------
 * Routes requests to a pool of backend servers using a pluggable selection
 * strategy. Unhealthy (down) servers are skipped, and least-connections tracks
 * in-flight load.
 *
 * Key classes / abstractions:
 *   - Backend                 : server with weight, health flag, active-conn count
 *   - LoadBalancingStrategy   : Strategy interface for choosing a backend
 *   - RoundRobinStrategy / RandomStrategy / LeastConnectionsStrategy /
 *     WeightedRoundRobinStrategy : concrete strategies (random is seeded)
 *   - LoadBalancer            : holds backends, delegates choice, tracks health
 *
 * Patterns used: Strategy (selection algorithm), Facade (LoadBalancer).
 *                Randomness is seeded for deterministic output.
 */
import java.util.*;

public class LoadBalancerDemo {
    public static void main(String[] args) {
        List<Backend> pool = new ArrayList<>(Arrays.asList(
                new Backend("s1", 1), new Backend("s2", 1), new Backend("s3", 3)));

        System.out.println("=== Round Robin ===");
        run(new LoadBalancer(pool, new RoundRobinStrategy()), 6);

        System.out.println("\n=== Weighted Round Robin (s3 weight=3) ===");
        run(new LoadBalancer(pool, new WeightedRoundRobinStrategy()), 10);

        System.out.println("\n=== Seeded Random ===");
        run(new LoadBalancer(pool, new RandomStrategy(42)), 6);

        System.out.println("\n=== Least Connections (with lingering load) ===");
        LoadBalancer lc = new LoadBalancer(pool, new LeastConnectionsStrategy());
        // Simulate s1 already busy so it gets skipped until others catch up.
        pool.get(0).activeConnections = 2;
        for (int i = 1; i <= 6; i++) {
            Backend b = lc.route("req" + i);
            System.out.println("  req" + i + " -> " + b.id + " (conns now=" + b.activeConnections + ")");
        }

        System.out.println("\n=== Health-aware skip ===");
        LoadBalancer hb = new LoadBalancer(pool, new RoundRobinStrategy());
        resetConns(pool);
        hb.markDown("s2");
        System.out.println("s2 marked DOWN");
        run(hb, 6);

        System.out.println("\n=== All down -> no backend ===");
        hb.markDown("s1"); hb.markDown("s3");
        try { hb.route("orphan"); }
        catch (RuntimeException e) { System.out.println("  error: " + e.getMessage()); }
    }

    static void run(LoadBalancer lb, int n) {
        resetConns(lb.backends());
        for (int i = 1; i <= n; i++) {
            Backend b = lb.route("req" + i);
            System.out.println("  req" + i + " -> " + b.id);
        }
    }

    static void resetConns(List<Backend> pool) {
        for (Backend b : pool) b.activeConnections = 0;
    }
}

class Backend {
    final String id;
    final int weight;
    boolean healthy = true;
    int activeConnections = 0;
    Backend(String id, int weight) { this.id = id; this.weight = weight; }
}

interface LoadBalancingStrategy {
    Backend choose(List<Backend> healthy);
    String name();
}

class RoundRobinStrategy implements LoadBalancingStrategy {
    private int idx = 0;
    public Backend choose(List<Backend> healthy) {
        Backend b = healthy.get(idx % healthy.size());
        idx++;
        return b;
    }
    public String name() { return "RoundRobin"; }
}

class RandomStrategy implements LoadBalancingStrategy {
    private final Random rnd;
    RandomStrategy(long seed) { this.rnd = new Random(seed); }
    public Backend choose(List<Backend> healthy) {
        return healthy.get(rnd.nextInt(healthy.size()));
    }
    public String name() { return "Random(seeded)"; }
}

class LeastConnectionsStrategy implements LoadBalancingStrategy {
    public Backend choose(List<Backend> healthy) {
        Backend best = healthy.get(0);
        for (Backend b : healthy)
            if (b.activeConnections < best.activeConnections) best = b;
        return best;
    }
    public String name() { return "LeastConnections"; }
}

class WeightedRoundRobinStrategy implements LoadBalancingStrategy {
    // Smooth weighted round robin using current/effective weights.
    private final Map<String, Integer> current = new HashMap<>();
    public Backend choose(List<Backend> healthy) {
        int total = 0;
        Backend best = null;
        for (Backend b : healthy) {
            int cw = current.getOrDefault(b.id, 0) + b.weight;
            current.put(b.id, cw);
            total += b.weight;
            if (best == null || cw > current.get(best.id)) best = b;
        }
        current.put(best.id, current.get(best.id) - total);
        return best;
    }
    public String name() { return "WeightedRoundRobin"; }
}

class LoadBalancer {
    private final List<Backend> backends;
    private final LoadBalancingStrategy strategy;

    LoadBalancer(List<Backend> backends, LoadBalancingStrategy strategy) {
        this.backends = backends;
        this.strategy = strategy;
    }

    List<Backend> backends() { return backends; }

    void markDown(String id) { find(id).healthy = false; }
    void markUp(String id)   { find(id).healthy = true; }

    private Backend find(String id) {
        for (Backend b : backends) if (b.id.equals(id)) return b;
        throw new RuntimeException("no such backend: " + id);
    }

    Backend route(String request) {
        List<Backend> healthy = new ArrayList<>();
        for (Backend b : backends) if (b.healthy) healthy.add(b);
        if (healthy.isEmpty()) throw new RuntimeException("no healthy backends available");
        Backend chosen = strategy.choose(healthy);
        chosen.activeConnections++;
        return chosen;
    }
}
