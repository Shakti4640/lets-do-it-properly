/*
 * Message Queue (Kafka-lite) (LLD)
 * --------------------------------
 * A topic split into partitions with append-only offsets. Producers publish
 * records, partitioned by key hash. Consumer groups poll records and commit
 * offsets per partition so each group tracks its own progress independently.
 *
 * Key classes / abstractions:
 *   - Record             : key, value, assigned offset
 *   - Partition          : append-only log of records
 *   - Topic              : fixed set of partitions + key->partition routing
 *   - ConsumerGroup      : committed offset per partition; poll advances cursor
 *   - Broker             : Facade for create/produce/subscribe/poll/commit
 *
 * Patterns used: Strategy-style partitioner (key hashing), Facade (Broker).
 */
import java.util.*;

public class MessageQueueKafkaLiteDemo {
    public static void main(String[] args) {
        Broker broker = new Broker();
        broker.createTopic("events", 3);

        System.out.println("=== Produce (partition by key) ===");
        String[][] data = {
            {"user-1", "login"}, {"user-2", "login"}, {"user-1", "click"},
            {"user-3", "login"}, {"user-2", "purchase"}, {"user-1", "logout"}
        };
        for (String[] kv : data) {
            int p = broker.produce("events", kv[0], kv[1]);
            System.out.println("  produce key=" + kv[0] + " val=" + kv[1] + " -> partition " + p);
        }
        broker.printTopic("events");

        System.out.println("\n=== Consumer group 'analytics' polls all partitions ===");
        broker.subscribe("events", "analytics");
        for (int p = 0; p < 3; p++) {
            List<Record> batch = broker.poll("events", "analytics", p, 10);
            System.out.println("  partition " + p + " -> " + batch);
            broker.commit("events", "analytics", p, batch);
        }

        System.out.println("\n=== Re-poll after commit (should be empty) ===");
        for (int p = 0; p < 3; p++)
            System.out.println("  partition " + p + " -> " + broker.poll("events", "analytics", p, 10));

        System.out.println("\n=== Second group 'audit' reads independently from offset 0 ===");
        broker.subscribe("events", "audit");
        for (int p = 0; p < 3; p++)
            System.out.println("  partition " + p + " -> " + broker.poll("events", "audit", p, 10));

        System.out.println("\n=== Committed offsets ===");
        broker.printOffsets("events");
    }
}

class Record {
    final String key;
    final String value;
    final long offset;
    Record(String key, String value, long offset) {
        this.key = key; this.value = value; this.offset = offset;
    }
    public String toString() { return "@" + offset + ":" + key + "=" + value; }
}

class Partition {
    final int id;
    final List<Record> log = new ArrayList<>();
    Partition(int id) { this.id = id; }
    long append(String key, String value) {
        long offset = log.size();
        log.add(new Record(key, value, offset));
        return offset;
    }
}

class Topic {
    final String name;
    final List<Partition> partitions = new ArrayList<>();
    // group -> (partition id -> next offset to read / committed cursor)
    final Map<String, Map<Integer, Long>> groupOffsets = new HashMap<>();

    Topic(String name, int partitionCount) {
        this.name = name;
        for (int i = 0; i < partitionCount; i++) partitions.add(new Partition(i));
    }

    int partitionFor(String key) {
        return Math.floorMod(key.hashCode(), partitions.size());
    }
}

class Broker {
    private final Map<String, Topic> topics = new LinkedHashMap<>();

    void createTopic(String name, int partitions) {
        topics.put(name, new Topic(name, partitions));
        System.out.println("topic '" + name + "' created with " + partitions + " partitions");
    }

    private Topic require(String name) {
        Topic t = topics.get(name);
        if (t == null) throw new RuntimeException("no such topic: " + name);
        return t;
    }

    int produce(String topic, String key, String value) {
        Topic t = require(topic);
        int p = t.partitionFor(key);
        t.partitions.get(p).append(key, value);
        return p;
    }

    void subscribe(String topic, String group) {
        Topic t = require(topic);
        t.groupOffsets.computeIfAbsent(group, g -> new HashMap<>());
        System.out.println("group '" + group + "' subscribed to " + topic);
    }

    List<Record> poll(String topic, String group, int partition, int maxRecords) {
        Topic t = require(topic);
        Map<Integer, Long> offsets = t.groupOffsets.computeIfAbsent(group, g -> new HashMap<>());
        long from = offsets.getOrDefault(partition, 0L);
        List<Record> log = t.partitions.get(partition).log;
        List<Record> batch = new ArrayList<>();
        for (long i = from; i < log.size() && batch.size() < maxRecords; i++)
            batch.add(log.get((int) i));
        return batch;
    }

    void commit(String topic, String group, int partition, List<Record> consumed) {
        if (consumed.isEmpty()) return;
        Topic t = require(topic);
        long lastOffset = consumed.get(consumed.size() - 1).offset;
        t.groupOffsets.get(group).put(partition, lastOffset + 1);
    }

    void printTopic(String topic) {
        Topic t = require(topic);
        System.out.println("topic '" + topic + "':");
        for (Partition p : t.partitions)
            System.out.println("  partition " + p.id + " (" + p.log.size() + " records): " + p.log);
    }

    void printOffsets(String topic) {
        Topic t = require(topic);
        for (Map.Entry<String, Map<Integer, Long>> g : t.groupOffsets.entrySet())
            System.out.println("  group " + g.getKey() + " committed offsets " + new TreeMap<>(g.getValue()));
    }
}
