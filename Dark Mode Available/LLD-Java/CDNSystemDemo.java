/*
 * CDN System (LLD)
 * ----------------
 * An origin server fronted by regional edge caches. Each edge serves cache hits
 * locally and fetches from origin on a miss; cached entries expire by TTL and
 * the edge evicts least-recently-used entries when full.
 *
 * Key classes / abstractions:
 *   - ManualClock        : deterministic, injectable time source
 *   - OriginServer       : authoritative key->content store
 *   - CacheEntry         : content + expiry timestamp
 *   - EdgeCache          : per-region LRU cache with TTL + hit/miss stats
 *   - CDN                : routes a region's request to its edge (Facade)
 *
 * Patterns used: Facade (CDN), LRU eviction (LinkedHashMap access order),
 *                injected clock for deterministic TTL behavior.
 */
import java.util.*;

public class CDNSystemDemo {
    public static void main(String[] args) {
        ManualClock clock = new ManualClock(0);
        OriginServer origin = new OriginServer();
        origin.put("/logo.png", "PNG-BYTES");
        origin.put("/app.js", "JS-BUNDLE");
        origin.put("/style.css", "CSS-RULES");

        CDN cdn = new CDN(origin, clock);
        cdn.addEdge("us-east", 2, 100); // capacity 2, TTL 100
        cdn.addEdge("eu-west", 2, 100);

        System.out.println("=== Cold cache: misses fetch from origin ===");
        System.out.println("us-east /logo.png -> " + cdn.get("us-east", "/logo.png"));
        System.out.println("us-east /app.js   -> " + cdn.get("us-east", "/app.js"));

        System.out.println("\n=== Warm cache: hits served from edge ===");
        System.out.println("us-east /logo.png -> " + cdn.get("us-east", "/logo.png"));

        System.out.println("\n=== Different region has its own edge (miss) ===");
        System.out.println("eu-west /logo.png -> " + cdn.get("eu-west", "/logo.png"));

        System.out.println("\n=== LRU eviction (capacity 2) ===");
        cdn.get("us-east", "/style.css"); // evicts LRU (/app.js was touched least recently)
        System.out.println("us-east /app.js (evicted?) -> " + cdn.get("us-east", "/app.js"));

        System.out.println("\n=== TTL expiry ===");
        System.out.println("clock advancing past TTL...");
        clock.advance(150);
        System.out.println("us-east /logo.png (expired -> refetch) -> " + cdn.get("us-east", "/logo.png"));

        System.out.println("\n=== Origin update is invisible until edge refetches ===");
        origin.put("/logo.png", "PNG-BYTES-V2");
        System.out.println("us-east /logo.png (still cached) -> " + cdn.get("us-east", "/logo.png"));
        clock.advance(150);
        System.out.println("us-east /logo.png (after expiry)  -> " + cdn.get("us-east", "/logo.png"));

        System.out.println("\n=== Edge stats ===");
        cdn.printStats();
    }
}

class ManualClock {
    private long t;
    ManualClock(long start) { this.t = start; }
    long now() { return t; }
    void advance(long delta) { t += delta; }
}

class OriginServer {
    private final Map<String, String> store = new HashMap<>();
    private int fetches = 0;
    void put(String key, String content) { store.put(key, content); }
    String fetch(String key) {
        fetches++;
        return store.get(key);
    }
    int fetchCount() { return fetches; }
}

class CacheEntry {
    final String content;
    final long expiresAt;
    CacheEntry(String content, long expiresAt) { this.content = content; this.expiresAt = expiresAt; }
}

class EdgeCache {
    final String region;
    private final int capacity;
    private final long ttl;
    private final ManualClock clock;
    private long hits = 0, misses = 0, evictions = 0;

    // access-order LinkedHashMap gives us LRU eviction for free.
    private final LinkedHashMap<String, CacheEntry> cache;

    EdgeCache(String region, int capacity, long ttl, ManualClock clock) {
        this.region = region; this.capacity = capacity; this.ttl = ttl; this.clock = clock;
        this.cache = new LinkedHashMap<String, CacheEntry>(16, 0.75f, true) {
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                boolean evict = size() > EdgeCache.this.capacity;
                if (evict) { evictions++; System.out.println("    [" + region + " evict LRU] " + eldest.getKey()); }
                return evict;
            }
        };
    }

    String get(String key, OriginServer origin) {
        CacheEntry e = cache.get(key);
        if (e != null && clock.now() < e.expiresAt) {
            hits++;
            return e.content + " (edge hit)";
        }
        if (e != null) System.out.println("    [" + region + " expired] " + key);
        misses++;
        String content = origin.fetch(key);
        cache.put(key, new CacheEntry(content, clock.now() + ttl));
        return content + " (origin fetch)";
    }

    String stats() {
        return String.format("%-8s size=%d hits=%d misses=%d evictions=%d",
                region, cache.size(), hits, misses, evictions);
    }
}

class CDN {
    private final OriginServer origin;
    private final ManualClock clock;
    private final Map<String, EdgeCache> edges = new LinkedHashMap<>();

    CDN(OriginServer origin, ManualClock clock) { this.origin = origin; this.clock = clock; }

    void addEdge(String region, int capacity, long ttl) {
        edges.put(region, new EdgeCache(region, capacity, ttl, clock));
        System.out.println("edge added: " + region + " (cap=" + capacity + ", ttl=" + ttl + ")");
    }

    String get(String region, String key) {
        EdgeCache edge = edges.get(region);
        if (edge == null) throw new RuntimeException("no edge for region: " + region);
        return edge.get(key, origin);
    }

    void printStats() {
        for (EdgeCache e : edges.values()) System.out.println("  " + e.stats());
        System.out.println("  origin fetches total: " + origin.fetchCount());
    }
}
