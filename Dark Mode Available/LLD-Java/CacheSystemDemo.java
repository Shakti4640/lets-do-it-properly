/*
 * Cache System (LLD)
 * ------------------
 * A generic, capacity-bounded cache with pluggable eviction strategies.
 *
 * Key classes / abstractions:
 *   - Cache<K,V>                : core cache; delegates eviction decisions to a strategy
 *   - EvictionStrategy<K>       : Strategy pattern interface (which key to evict / track access)
 *   - LruEvictionStrategy<K>    : Least-Recently-Used eviction
 *   - LfuEvictionStrategy<K>    : Least-Frequently-Used eviction
 *   - EvictionStrategyFactory   : Factory pattern to build strategies by type
 *
 * Patterns used: Strategy (eviction), Factory (strategy creation).
 */
import java.util.*;

public class CacheSystemDemo {
    public static void main(String[] args) {
        System.out.println("=== LRU Cache (capacity 3) ===");
        Cache<String, Integer> lru = new Cache<>(3, EvictionStrategyFactory.create(EvictionType.LRU));
        lru.put("a", 1);
        lru.put("b", 2);
        lru.put("c", 3);
        System.out.println("get a -> " + lru.get("a")); // touch a
        lru.put("d", 4);                                  // evicts b (least recently used)
        System.out.println("get b -> " + lru.get("b"));   // null
        System.out.println("keys  -> " + lru.keys());

        System.out.println("\n=== LFU Cache (capacity 3) ===");
        Cache<String, Integer> lfu = new Cache<>(3, EvictionStrategyFactory.create(EvictionType.LFU));
        lfu.put("x", 10);
        lfu.put("y", 20);
        lfu.put("z", 30);
        lfu.get("x"); lfu.get("x"); // freq(x)=3
        lfu.get("y");               // freq(y)=2
        lfu.put("w", 40);           // evicts z (freq 1)
        System.out.println("get z -> " + lfu.get("z")); // null
        System.out.println("keys  -> " + lfu.keys());
        System.out.println("\nStats: " + lfu.stats());
    }
}

enum EvictionType { LRU, LFU }

interface EvictionStrategy<K> {
    void recordAccess(K key);   // called on get/put of an existing key
    void recordInsert(K key);   // called when a new key is inserted
    void remove(K key);         // called when a key is removed
    K evictionCandidate();      // which key should be evicted next
    String name();
}

class LruEvictionStrategy<K> implements EvictionStrategy<K> {
    // Access order: head = least recent, tail = most recent.
    private final LinkedHashSet<K> order = new LinkedHashSet<>();

    public void recordAccess(K key) { order.remove(key); order.add(key); }
    public void recordInsert(K key) { order.add(key); }
    public void remove(K key)       { order.remove(key); }
    public K evictionCandidate()    { return order.isEmpty() ? null : order.iterator().next(); }
    public String name()            { return "LRU"; }
}

class LfuEvictionStrategy<K> implements EvictionStrategy<K> {
    private final Map<K, Integer> freq = new HashMap<>();
    private final Map<K, Long> tieBreak = new HashMap<>(); // older insert wins on tie
    private long seq = 0;

    public void recordAccess(K key) { freq.merge(key, 1, Integer::sum); }
    public void recordInsert(K key) { freq.put(key, 1); tieBreak.put(key, seq++); }
    public void remove(K key)       { freq.remove(key); tieBreak.remove(key); }

    public K evictionCandidate() {
        K best = null;
        int bestFreq = Integer.MAX_VALUE;
        long bestTie = Long.MAX_VALUE;
        for (Map.Entry<K, Integer> e : freq.entrySet()) {
            int f = e.getValue();
            long t = tieBreak.getOrDefault(e.getKey(), 0L);
            if (f < bestFreq || (f == bestFreq && t < bestTie)) {
                best = e.getKey(); bestFreq = f; bestTie = t;
            }
        }
        return best;
    }
    public String name() { return "LFU"; }
}

class EvictionStrategyFactory {
    static <K> EvictionStrategy<K> create(EvictionType type) {
        switch (type) {
            case LRU: return new LruEvictionStrategy<>();
            case LFU: return new LfuEvictionStrategy<>();
            default:  throw new IllegalArgumentException("Unknown eviction type: " + type);
        }
    }
}

class Cache<K, V> {
    private final int capacity;
    private final Map<K, V> store = new HashMap<>();
    private final EvictionStrategy<K> strategy;
    private long hits = 0, misses = 0, evictions = 0;

    Cache(int capacity, EvictionStrategy<K> strategy) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        this.capacity = capacity;
        this.strategy = strategy;
    }

    V get(K key) {
        if (store.containsKey(key)) {
            hits++;
            strategy.recordAccess(key);
            return store.get(key);
        }
        misses++;
        return null;
    }

    void put(K key, V value) {
        if (store.containsKey(key)) {
            store.put(key, value);
            strategy.recordAccess(key);
            return;
        }
        if (store.size() >= capacity) {
            K victim = strategy.evictionCandidate();
            if (victim != null) {
                store.remove(victim);
                strategy.remove(victim);
                evictions++;
                System.out.println("  [evict] " + victim);
            }
        }
        store.put(key, value);
        strategy.recordInsert(key);
    }

    Set<K> keys() { return new HashSet<>(store.keySet()); }

    String stats() {
        return String.format("policy=%s size=%d/%d hits=%d misses=%d evictions=%d",
                strategy.name(), store.size(), capacity, hits, misses, evictions);
    }
}
