/*
 * Workflow / BPM Engine (LLD)
 * ---------------------------
 * A workflow is a DAG of tasks with dependencies. The engine validates the DAG,
 * computes a topological execution order, runs each task (passing a shared
 * context), and supports conditional branching that skips downstream tasks.
 *
 * Key classes / abstractions:
 *   - WorkflowTask           : a unit of work with an action + optional condition
 *   - WorkflowContext        : shared mutable state passed between tasks
 *   - Workflow               : builder for tasks + dependencies (the DAG)
 *   - WorkflowEngine         : topological sort, cycle detection, execution
 *
 * Patterns used: Builder (Workflow), Command (task actions), topological scheduling.
 */
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

public class WorkflowBPMEngineDemo {
    public static void main(String[] args) {
        Workflow wf = new Workflow("order-fulfillment");

        wf.task("validate", ctx -> {
            ctx.put("valid", true);
            return "order validated";
        });
        wf.task("charge", ctx -> {
            ctx.put("amount", 250);
            return "charged $" + ctx.get("amount");
        });
        // Conditional: only run express shipping if amount >= 200.
        wf.taskIf("express-ship",
                ctx -> ((Integer) ctx.getOrDefault("amount", 0)) >= 200,
                ctx -> "express shipping booked");
        wf.taskIf("standard-ship",
                ctx -> ((Integer) ctx.getOrDefault("amount", 0)) < 200,
                ctx -> "standard shipping booked");
        wf.task("notify", ctx -> "customer notified");

        wf.dependsOn("charge", "validate");
        wf.dependsOn("express-ship", "charge");
        wf.dependsOn("standard-ship", "charge");
        wf.dependsOn("notify", "express-ship");
        wf.dependsOn("notify", "standard-ship");

        WorkflowEngine engine = new WorkflowEngine();

        System.out.println("=== Execution order (topological) ===");
        System.out.println(engine.order(wf));

        System.out.println("\n=== Run workflow ===");
        WorkflowContext ctx = engine.run(wf);

        System.out.println("\n=== Results ===");
        for (Map.Entry<String, String> e : ctx.results().entrySet()) {
            System.out.println("  " + e.getKey() + ": " + e.getValue());
        }

        System.out.println("\n=== Cycle detection on a bad DAG ===");
        Workflow bad = new Workflow("bad");
        bad.task("a", ctx2 -> "a");
        bad.task("b", ctx2 -> "b");
        bad.dependsOn("a", "b");
        bad.dependsOn("b", "a");
        try {
            engine.order(bad);
        } catch (IllegalStateException ex) {
            System.out.println("caught: " + ex.getMessage());
        }
    }
}

class WorkflowContext {
    private final Map<String, Object> state = new HashMap<>();
    private final Map<String, String> results = new LinkedHashMap<>();
    void put(String key, Object value) { state.put(key, value); }
    Object get(String key) { return state.get(key); }
    Object getOrDefault(String key, Object def) { return state.getOrDefault(key, def); }
    void recordResult(String task, String result) { results.put(task, result); }
    Map<String, String> results() { return results; }
}

class WorkflowTask {
    final String name;
    final Predicate<WorkflowContext> condition; // null => always run
    final Function<WorkflowContext, String> action;
    WorkflowTask(String name, Predicate<WorkflowContext> condition,
                 Function<WorkflowContext, String> action) {
        this.name = name; this.condition = condition; this.action = action;
    }
}

class Workflow {
    final String name;
    final Map<String, WorkflowTask> tasks = new LinkedHashMap<>();
    final Map<String, Set<String>> dependencies = new HashMap<>(); // task -> its prerequisites

    Workflow(String name) { this.name = name; }

    Workflow task(String name, Function<WorkflowContext, String> action) {
        return taskIf(name, null, action);
    }

    Workflow taskIf(String name, Predicate<WorkflowContext> condition,
                    Function<WorkflowContext, String> action) {
        tasks.put(name, new WorkflowTask(name, condition, action));
        dependencies.computeIfAbsent(name, k -> new LinkedHashSet<>());
        return this;
    }

    Workflow dependsOn(String task, String prerequisite) {
        dependencies.computeIfAbsent(task, k -> new LinkedHashSet<>()).add(prerequisite);
        return this;
    }
}

class WorkflowEngine {
    // Kahn's algorithm for a deterministic topological order (with cycle detection).
    List<String> order(Workflow wf) {
        Map<String, Integer> indegree = new LinkedHashMap<>();
        Map<String, List<String>> adj = new HashMap<>();
        for (String t : wf.tasks.keySet()) {
            indegree.put(t, 0);
            adj.put(t, new ArrayList<>());
        }
        for (Map.Entry<String, Set<String>> e : wf.dependencies.entrySet()) {
            String task = e.getKey();
            for (String prereq : e.getValue()) {
                adj.get(prereq).add(task);
                indegree.put(task, indegree.get(task) + 1);
            }
        }
        // Use insertion order of tasks for stable tie-breaking.
        Deque<String> ready = new ArrayDeque<>();
        for (String t : wf.tasks.keySet()) {
            if (indegree.get(t) == 0) ready.addLast(t);
        }
        List<String> orderList = new ArrayList<>();
        while (!ready.isEmpty()) {
            String t = ready.pollFirst();
            orderList.add(t);
            for (String next : adj.get(t)) {
                indegree.put(next, indegree.get(next) - 1);
                if (indegree.get(next) == 0) ready.addLast(next);
            }
        }
        if (orderList.size() != wf.tasks.size()) {
            throw new IllegalStateException("workflow '" + wf.name + "' contains a cycle");
        }
        return orderList;
    }

    WorkflowContext run(Workflow wf) {
        WorkflowContext ctx = new WorkflowContext();
        Set<String> skipped = new HashSet<>();
        for (String taskName : order(wf)) {
            WorkflowTask task = wf.tasks.get(taskName);

            // Branch pruning: skip only if the task has prerequisites and ALL of
            // them were skipped. A join after a branch still runs if any branch ran.
            Set<String> prereqs = wf.dependencies.get(taskName);
            if (!prereqs.isEmpty()) {
                boolean allSkipped = true;
                for (String prereq : prereqs) {
                    if (!skipped.contains(prereq)) { allSkipped = false; break; }
                }
                if (allSkipped) {
                    skipped.add(taskName);
                    System.out.println("  [skip] " + taskName + " (all prerequisites skipped)");
                    continue;
                }
            }
            if (task.condition != null && !task.condition.test(ctx)) {
                skipped.add(taskName);
                System.out.println("  [skip] " + taskName + " (condition false)");
                continue;
            }
            String result = task.action.apply(ctx);
            ctx.recordResult(taskName, result);
            System.out.println("  [run]  " + taskName + " -> " + result);
        }
        return ctx;
    }
}
