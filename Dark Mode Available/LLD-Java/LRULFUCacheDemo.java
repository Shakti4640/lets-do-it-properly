/*
 * LRU / LFU Cache Implementation (LLD)
 * ------------------------------------
 * A common Cache<K,V> interface with two capacity-bounded implementations:
 *   - LRUCache : doubly-linked list + map, O(1) get/put, evicts least-recently-used
 *   - LFUCache : frequency buckets + map, O(1) get/put, evicts least-frequently-used
 *
 * Key classes / abstractions:
 *   - Cache<K,V>   : interface (get/put/keys)
 *   - LRUCache<K,V>: recency-ordered eviction via intrusive linked list
 *   - LFUCache<K,V>: frequency-ordered eviction with LRU tie-break within a freq
 *
 * Patterns used: Strategy (interchangeable Cache impls), intrusive linked list.
 */
import java.util.*;

public class LRULFUCacheDemo {
    public static void main(String[] args) {
        System.out.println("=== LRU Cache (capacity 3) ===");
        Cache<String, Integer> lru = new LRUCache<>(3);
        lru.put("a", 1);
        lru.put("b", 2);
        lru.put("c", 3);
        System.out.println("get a -> " + lru.get("a")); // a becomes most-recent
        lru.put("d", 4);                                  // evicts b (least recent)
        System.out.println("get b -> " + lru.get("b"));   // null
        System.out.println("keys  -> " + lru.keys());

        System.out.println("\n=== LFU Cache (capacity 3) ===");
        Cache<String, Integer> lfu = new LFUCache<>(3);
        lfu.put("x", 10);
        lfu.put("y", 20);
        lfu.put("z", 30);
        lfu.get("x"); lfu.get("x"); // freq(x)=3
        lfu.get("y");               // freq(y)=2
        lfu.put("w", 40);           // evicts z (freq 1)
        System.out.println("get z -> " + lfu.get("z")); // null
        System.out.println("keys  -> " + lfu.keys());

        System.out.println("\n=== LFU tie-break uses LRU among equal frequencies ===");
        Cache<String, Integer> lfu2 = new LFUCache<>(2);
        lfu2.put("p", 1); // freq 1
        lfu2.put("q", 1); // freq 1
        lfu2.get("p");    // freq(p)=2, freq(q)=1
        lfu2.put("r", 1); // evicts q (lowest freq)
        System.out.println("get q -> " + lfu2.get("q")); // null
        System.out.println("keys  -> " + lfu2.keys());
    }
}

interface Cache<K, V> {
    V get(K key);
    void put(K key, V value);
    Set<K> keys();
}

class LRUCache<K, V> implements Cache<K, V> {
    private static class Node<K, V> {
        K key; V value; Node<K, V> prev, next;
        Node(K key, V value) { this.key = key; this.value = value; }
    }
    private final int capacity;
    private final Map<K, Node<K, V>> map = new HashMap<>();
    private final Node<K, V> head = new Node<>(null, null); // most-recent side
    private final Node<K, V> tail = new Node<>(null, null); // least-recent side

    LRUCache(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        this.capacity = capacity;
        head.next = tail; tail.prev = head;
    }

    public V get(K key) {
        Node<K, V> n = map.get(key);
        if (n == null) return null;
        moveToFront(n);
        return n.value;
    }

    public void put(K key, V value) {
        Node<K, V> n = map.get(key);
        if (n != null) { n.value = value; moveToFront(n); return; }
        if (map.size() >= capacity) {
            Node<K, V> lru = tail.prev;
            remove(lru);
            map.remove(lru.key);
            System.out.println("  [LRU evict] " + lru.key);
        }
        Node<K, V> fresh = new Node<>(key, value);
        map.put(key, fresh);
        addFront(fresh);
    }

    public Set<K> keys() {
        Set<K> out = new LinkedHashSet<>();
        for (Node<K, V> cur = head.next; cur != tail; cur = cur.next) out.add(cur.key);
        return out;
    }

    private void addFront(Node<K, V> n) {
        n.prev = head; n.next = head.next;
        head.next.prev = n; head.next = n;
    }
    private void remove(Node<K, V> n) {
        n.prev.next = n.next; n.next.prev = n.prev;
    }
    private void moveToFront(Node<K, V> n) { remove(n); addFront(n); }
}

class LFUCache<K, V> implements Cache<K, V> {
    private final int capacity;
    private final Map<K, V> values = new HashMap<>();
    private final Map<K, Integer> freqs = new HashMap<>();
    // frequency -> keys at that frequency in access order (front = LRU within freq)
    private final Map<Integer, LinkedHashSet<K>> buckets = new HashMap<>();
    private int minFreq = 0;

    LFUCache(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        this.capacity = capacity;
    }

    public V get(K key) {
        if (!values.containsKey(key)) return null;
        touch(key);
        return values.get(key);
    }

    public void put(K key, V value) {
        if (values.containsKey(key)) { values.put(key, value); touch(key); return; }
        if (values.size() >= capacity) evict();
        values.put(key, value);
        freqs.put(key, 1);
        buckets.computeIfAbsent(1, k -> new LinkedHashSet<>()).add(key);
        minFreq = 1;
    }

    public Set<K> keys() { return new LinkedHashSet<>(values.keySet()); }

    private void touch(K key) {
        int f = freqs.get(key);
        LinkedHashSet<K> bucket = buckets.get(f);
        bucket.remove(key);
        if (bucket.isEmpty()) {
            buckets.remove(f);
            if (minFreq == f) minFreq = f + 1;
        }
        int nf = f + 1;
        freqs.put(key, nf);
        buckets.computeIfAbsent(nf, k -> new LinkedHashSet<>()).add(key);
    }

    private void evict() {
        LinkedHashSet<K> bucket = buckets.get(minFreq);
        K victim = bucket.iterator().next(); // least-recently-used within lowest freq
        bucket.remove(victim);
        if (bucket.isEmpty()) buckets.remove(minFreq);
        values.remove(victim);
        freqs.remove(victim);
        System.out.println("  [LFU evict] " + victim);
    }
}
