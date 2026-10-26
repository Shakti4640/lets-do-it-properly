/*
 * Event-Driven Producer-Consumer System - Low Level Design
 * --------------------------------------------------------
 * A bounded blocking queue mediates between producer threads and consumer
 * threads. Producers publish events; consumers process them concurrently.
 * Demonstrates classic wait/notify backpressure on a bounded buffer.
 *
 * Key classes:
 *   - Event
 *   - BoundedEventQueue (thread-safe, blocking put/take)
 *   - Producer (Runnable) / Consumer (Runnable)
 *   - EventBus (facade wiring producers & consumers)
 *
 * Patterns used: Producer-Consumer, blocking queue, Observer-style bus.
 */

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class ProducerConsumerDemo {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Producer-Consumer Demo ===");
        BoundedEventQueue queue = new BoundedEventQueue(5);

        List<Thread> threads = new ArrayList<Thread>();
        // 2 producers, each emits 5 events
        for (int p = 1; p <= 2; p++) {
            Producer producer = new Producer("P" + p, queue, 5);
            threads.add(new Thread(producer));
        }
        // 3 consumers
        for (int c = 1; c <= 3; c++) {
            Consumer consumer = new Consumer("C" + c, queue);
            Thread t = new Thread(consumer);
            t.setDaemon(true);
            threads.add(t);
        }

        // Start consumers first
        for (Thread t : threads) if (t.isDaemon()) t.start();
        // Then producers
        for (Thread t : threads) if (!t.isDaemon()) t.start();

        // Wait for producers (non-daemon) to finish
        for (Thread t : threads) if (!t.isDaemon()) t.join();

        // Give consumers a moment to drain
        Thread.sleep(300);
        System.out.println("Done. Remaining in queue: " + queue.size());
    }
}

class Event {
    final String id; final String payload;
    Event(String id, String payload) { this.id = id; this.payload = payload; }
    public String toString() { return id + "(" + payload + ")"; }
}

class BoundedEventQueue {
    private final Queue<Event> buffer = new LinkedList<Event>();
    private final int capacity;
    BoundedEventQueue(int capacity) { this.capacity = capacity; }

    synchronized void put(Event event) throws InterruptedException {
        while (buffer.size() >= capacity) wait();
        buffer.offer(event);
        notifyAll();
    }

    synchronized Event take() throws InterruptedException {
        while (buffer.isEmpty()) wait();
        Event e = buffer.poll();
        notifyAll();
        return e;
    }

    synchronized int size() { return buffer.size(); }
}

class Producer implements Runnable {
    private final String name; private final BoundedEventQueue queue; private final int count;
    Producer(String name, BoundedEventQueue queue, int count) { this.name = name; this.queue = queue; this.count = count; }
    public void run() {
        for (int i = 1; i <= count; i++) {
            Event e = new Event(name + "-e" + i, "data" + i);
            try {
                queue.put(e);
                System.out.println("[" + name + "] produced " + e);
                Thread.sleep(20);
            } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
        }
    }
}

class Consumer implements Runnable {
    private final String name; private final BoundedEventQueue queue;
    Consumer(String name, BoundedEventQueue queue) { this.name = name; this.queue = queue; }
    public void run() {
        while (true) {
            try {
                Event e = queue.take();
                System.out.println("   [" + name + "] consumed " + e);
                Thread.sleep(40);
            } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
        }
    }
}
