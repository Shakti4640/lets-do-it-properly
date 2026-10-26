/*
 * Pub/Sub Message Broker (LLD)
 * ----------------------------
 * Topics with publishers and subscribers. Publishing a message fans it out to
 * every current subscriber (Observer). Subscribers can subscribe/unsubscribe at
 * runtime, and each topic keeps a message log.
 *
 * Key classes / abstractions:
 *   - Message            : immutable payload + sequence number
 *   - Subscriber         : Observer interface (onMessage callback)
 *   - LoggingSubscriber  : concrete subscriber that records what it received
 *   - Topic              : maintains subscriber set + append-only message log
 *   - Broker             : Facade to create topics, subscribe, and publish
 *
 * Patterns used: Observer (fan-out), Facade (Broker).
 */
import java.util.*;

public class PubSubMessageBrokerDemo {
    public static void main(String[] args) {
        Broker broker = new Broker();
        broker.createTopic("orders");
        broker.createTopic("payments");

        LoggingSubscriber inventory = new LoggingSubscriber("inventory-svc");
        LoggingSubscriber email = new LoggingSubscriber("email-svc");
        LoggingSubscriber ledger = new LoggingSubscriber("ledger-svc");

        System.out.println("=== Subscribe ===");
        broker.subscribe("orders", inventory);
        broker.subscribe("orders", email);
        broker.subscribe("payments", ledger);

        System.out.println("\n=== Publish to 'orders' (2 subscribers) ===");
        broker.publish("orders", "order#1 created");
        broker.publish("orders", "order#2 created");

        System.out.println("\n=== Publish to 'payments' (1 subscriber) ===");
        broker.publish("payments", "payment#1 captured");

        System.out.println("\n=== Unsubscribe email from 'orders', publish again ===");
        broker.unsubscribe("orders", email);
        broker.publish("orders", "order#3 created");

        System.out.println("\n=== Subscriber inboxes ===");
        for (LoggingSubscriber s : Arrays.asList(inventory, email, ledger))
            System.out.println("  " + s.id() + " received " + s.received().size() + ": " + s.received());

        System.out.println("\n=== Topic logs ===");
        broker.printLog("orders");
        broker.printLog("payments");
    }
}

class Message {
    final long seq;
    final String topic;
    final String payload;
    Message(long seq, String topic, String payload) {
        this.seq = seq; this.topic = topic; this.payload = payload;
    }
    public String toString() { return "[" + seq + "] " + payload; }
}

interface Subscriber {
    String id();
    void onMessage(Message m);
}

class LoggingSubscriber implements Subscriber {
    private final String id;
    private final List<String> received = new ArrayList<>();
    LoggingSubscriber(String id) { this.id = id; }
    public String id() { return id; }
    public void onMessage(Message m) {
        received.add(m.payload);
        System.out.println("  -> " + id + " got " + m);
    }
    List<String> received() { return received; }
}

class Topic {
    final String name;
    private final Set<Subscriber> subscribers = new LinkedHashSet<>();
    private final List<Message> log = new ArrayList<>();
    Topic(String name) { this.name = name; }

    void subscribe(Subscriber s) { subscribers.add(s); }
    void unsubscribe(Subscriber s) { subscribers.remove(s); }

    void deliver(Message m) {
        log.add(m);
        // Snapshot to keep delivery stable even if subscribers change mid-publish.
        for (Subscriber s : new ArrayList<>(subscribers)) s.onMessage(m);
    }

    List<Message> log() { return log; }
    int subscriberCount() { return subscribers.size(); }
}

class Broker {
    private final Map<String, Topic> topics = new LinkedHashMap<>();
    private long seq = 0;

    void createTopic(String name) {
        topics.putIfAbsent(name, new Topic(name));
        System.out.println("topic created: " + name);
    }

    private Topic require(String name) {
        Topic t = topics.get(name);
        if (t == null) throw new RuntimeException("no such topic: " + name);
        return t;
    }

    void subscribe(String topic, Subscriber s) {
        require(topic).subscribe(s);
        System.out.println(s.id() + " subscribed to " + topic);
    }

    void unsubscribe(String topic, Subscriber s) {
        require(topic).unsubscribe(s);
        System.out.println(s.id() + " unsubscribed from " + topic);
    }

    void publish(String topic, String payload) {
        Topic t = require(topic);
        Message m = new Message(++seq, topic, payload);
        System.out.println("publish to " + topic + " (" + t.subscriberCount() + " subs): " + payload);
        t.deliver(m);
    }

    void printLog(String topic) {
        Topic t = require(topic);
        System.out.println("log[" + topic + "]:");
        for (Message m : t.log()) System.out.println("  " + m);
    }
}
