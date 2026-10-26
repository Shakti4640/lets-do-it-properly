/*
 * Thread Pool Executor (LLD)
 * --------------------------
 * A simple fixed-size thread pool: worker threads pull tasks from a blocking
 * queue, run submitted Runnables, and a graceful shutdown drains the queue and
 * joins all workers so the demo output is deterministic at completion.
 *
 * Key classes / abstractions:
 *   - SimpleThreadPool       : submit / shutdown / awaitTermination
 *   - Worker                 : a thread looping on the blocking task queue
 *   - Poison pill            : sentinel Runnable that signals a worker to exit
 *
 * Patterns used: Producer-Consumer (queue), Worker thread pool, poison-pill shutdown.
 */
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadPoolExecutorDemo {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Submit 8 tasks to a pool of 3 workers ===");
        SimpleThreadPool pool = new SimpleThreadPool(3);

        final AtomicInteger completed = new AtomicInteger(0);
        final List<String> results = Collections.synchronizedList(new ArrayList<String>());

        for (int i = 1; i <= 8; i++) {
            final int taskId = i;
            pool.submit(() -> {
                // Deterministic-ish work: a tiny bounded computation, no sleeping.
                long sum = 0;
                for (int k = 0; k < taskId * 1000; k++) sum += k;
                results.add("task-" + taskId + " (sum=" + sum + ")");
                completed.incrementAndGet();
            });
        }

        System.out.println("submitted: " + pool.submittedCount() + " tasks");

        System.out.println("\n=== Graceful shutdown (drains queue, joins workers) ===");
        pool.shutdown();
        boolean done = pool.awaitTermination();
        System.out.println("terminated cleanly: " + done);
        System.out.println("tasks completed: " + completed.get());

        // Sort for deterministic display since completion order across threads varies.
        List<String> sorted = new ArrayList<>(results);
        sorted.sort(Comparator.naturalOrder());
        System.out.println("\n=== Results (sorted) ===");
        for (String r : sorted) System.out.println("  " + r);

        System.out.println("\n=== Submitting after shutdown is rejected ===");
        try {
            pool.submit(() -> System.out.println("should not run"));
        } catch (IllegalStateException e) {
            System.out.println("caught: " + e.getMessage());
        }
    }
}

class SimpleThreadPool {
    private static final Runnable POISON = () -> { };
    private final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
    private final List<Thread> workers = new ArrayList<>();
    private volatile boolean shutdown = false;
    private int submitted = 0;

    SimpleThreadPool(int size) {
        if (size <= 0) throw new IllegalArgumentException("size must be > 0");
        for (int i = 0; i < size; i++) {
            Thread t = new Thread(new Worker(), "worker-" + (i + 1));
            workers.add(t);
            t.start();
        }
    }

    synchronized void submit(Runnable task) {
        if (shutdown) throw new IllegalStateException("pool is shut down; cannot accept tasks");
        submitted++;
        queue.offer(task);
    }

    int submittedCount() { return submitted; }

    // Graceful: stop accepting work, then enqueue one poison pill per worker.
    synchronized void shutdown() {
        shutdown = true;
        for (int i = 0; i < workers.size(); i++) queue.offer(POISON);
    }

    boolean awaitTermination() throws InterruptedException {
        for (Thread t : workers) t.join();
        return true;
    }

    private class Worker implements Runnable {
        public void run() {
            try {
                while (true) {
                    Runnable task = queue.take();
                    if (task == POISON) return; // graceful exit once drained
                    try {
                        task.run();
                    } catch (RuntimeException ex) {
                        System.out.println("  [" + Thread.currentThread().getName()
                                + "] task failed: " + ex.getMessage());
                    }
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
