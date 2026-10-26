/*
 * Distributed Tracing System (LLD)
 * --------------------------------
 * Models a trace composed of spans with parent/child relationships. Spans are
 * started and finished against an injected clock, then assembled into a span
 * tree and printed as an indented timeline with durations.
 *
 * Key classes / abstractions:
 *   - Span                   : unit of work (id, parentId, name, start/end)
 *   - Trace                  : collection of spans sharing a traceId; builds tree
 *   - Tracer                 : starts/finishes spans, assigns ids, uses Clock
 *   - Clock                  : injected time source for deterministic durations
 *
 * Patterns used: Builder-ish span creation, Composite (span tree), DI (Clock).
 */
import java.util.*;

public class DistributedTracingSystemDemo {
    public static void main(String[] args) {
        MutableClock clock = new MutableClock(0L);
        Tracer tracer = new Tracer("trace-1", clock);

        // Simulate a request: HTTP handler -> (auth -> db query) -> render.
        Span root = tracer.startSpan("GET /checkout", null);
        clock.advance(2);

        Span auth = tracer.startSpan("auth.verify", root.spanId);
        clock.advance(5);
        tracer.finishSpan(auth);

        Span db = tracer.startSpan("db.query", root.spanId);
        clock.advance(10);
        Span dbChild = tracer.startSpan("db.fetchRows", db.spanId);
        clock.advance(7);
        tracer.finishSpan(dbChild);
        tracer.finishSpan(db);

        Span render = tracer.startSpan("render.template", root.spanId);
        clock.advance(3);
        tracer.finishSpan(render);

        tracer.finishSpan(root);

        System.out.println("=== Trace timeline ===");
        tracer.trace().printTimeline();

        System.out.println("\n=== Summary ===");
        System.out.println("total spans: " + tracer.trace().spanCount());
        System.out.println("root duration: " + root.duration() + "ms");
    }
}

class Clock {
    long now() { return 0L; }
}

class MutableClock extends Clock {
    private long millis;
    MutableClock(long start) { this.millis = start; }
    long now() { return millis; }
    void advance(long delta) { millis += delta; }
}

class Span {
    final String spanId;
    final String parentId; // null for root
    final String name;
    final long startTime;
    long endTime = -1;
    Span(String spanId, String parentId, String name, long startTime) {
        this.spanId = spanId; this.parentId = parentId; this.name = name; this.startTime = startTime;
    }
    void finish(long endTime) { this.endTime = endTime; }
    long duration() { return endTime < 0 ? -1 : endTime - startTime; }
    public String toString() { return name + " [" + startTime + "->" + endTime + "] " + duration() + "ms"; }
}

class Trace {
    final String traceId;
    private final List<Span> spans = new ArrayList<>();
    Trace(String traceId) { this.traceId = traceId; }

    void add(Span span) { spans.add(span); }
    int spanCount() { return spans.size(); }

    private List<Span> childrenOf(String parentId) {
        List<Span> kids = new ArrayList<>();
        for (Span s : spans) {
            if (Objects.equals(s.parentId, parentId)) kids.add(s);
        }
        // Stable ordering by start time for a readable timeline.
        kids.sort(Comparator.comparingLong(a -> a.startTime));
        return kids;
    }

    void printTimeline() {
        for (Span root : childrenOf(null)) {
            printSpan(root, 0);
        }
    }

    private void printSpan(Span span, int depth) {
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < depth; i++) indent.append("  ");
        System.out.println(indent + "+ " + span.name + " (" + span.duration() + "ms, "
                + "start=" + span.startTime + ")");
        for (Span child : childrenOf(span.spanId)) {
            printSpan(child, depth + 1);
        }
    }
}

class Tracer {
    private final Trace trace;
    private final Clock clock;
    private int counter = 0;

    Tracer(String traceId, Clock clock) {
        this.trace = new Trace(traceId);
        this.clock = clock;
    }

    Span startSpan(String name, String parentId) {
        String spanId = "s" + (++counter);
        Span span = new Span(spanId, parentId, name, clock.now());
        trace.add(span);
        return span;
    }

    void finishSpan(Span span) { span.finish(clock.now()); }

    Trace trace() { return trace; }
}
