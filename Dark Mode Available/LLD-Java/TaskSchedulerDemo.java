/*
 * Task Scheduler - Low Level Design
 * ---------------------------------
 * Schedules tasks by priority and execution time. A min-heap (PriorityQueue)
 * orders tasks; a simulated clock executes due tasks. Supports one-off and
 * recurring tasks.
 *
 * Key classes:
 *   - Task (Runnable command, priority, runAt, interval)
 *   - TaskScheduler (priority queue + run loop)
 *
 * Patterns used: Command (task action), Priority scheduling.
 */

import java.util.PriorityQueue;

public class TaskSchedulerDemo {
    public static void main(String[] args) {
        TaskScheduler scheduler = new TaskScheduler();
        System.out.println("=== Task Scheduler Demo ===");

        scheduler.schedule(new Task("backup", 5, () -> System.out.println("  running backup"), 0));
        scheduler.schedule(new Task("email", 2, () -> System.out.println("  sending email"), 0));
        scheduler.schedule(new Task("heartbeat", 3, () -> System.out.println("  heartbeat"), 3)); // recurring every 3 ticks

        // Run simulated clock for 10 ticks
        scheduler.run(10);
    }
}

class Task implements Comparable<Task> {
    final String name;
    final int priority;       // lower = higher priority
    final Runnable action;
    long runAt;               // tick at which to run
    final long interval;      // 0 = one-off, else recurring

    Task(String name, int priority, Runnable action, long interval) {
        this.name = name; this.priority = priority; this.action = action; this.interval = interval;
    }
    boolean isRecurring() { return interval > 0; }

    public int compareTo(Task o) {
        if (runAt != o.runAt) return Long.compare(runAt, o.runAt);
        return Integer.compare(priority, o.priority);
    }
}

class TaskScheduler {
    private final PriorityQueue<Task> queue = new PriorityQueue<Task>();

    void schedule(Task task) {
        queue.offer(task);
        System.out.println("Scheduled '" + task.name + "' at tick " + task.runAt
                + (task.isRecurring() ? " (every " + task.interval + ")" : ""));
    }

    void run(long maxTicks) {
        for (long tick = 0; tick <= maxTicks; tick++) {
            while (!queue.isEmpty() && queue.peek().runAt <= tick) {
                Task task = queue.poll();
                System.out.println("[tick " + tick + "] executing '" + task.name + "' (prio " + task.priority + ")");
                task.action.run();
                if (task.isRecurring()) {
                    task.runAt = tick + task.interval;
                    queue.offer(task);
                }
            }
        }
        System.out.println("Scheduler finished after " + maxTicks + " ticks.");
    }
}
