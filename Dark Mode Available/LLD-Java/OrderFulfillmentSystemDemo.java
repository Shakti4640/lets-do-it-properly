/*
 * Order Fulfillment System - Low Level Design
 * --------------------------------------------------------------
 * Drives an order through its fulfillment lifecycle using an explicit
 * state machine: PLACED -> PICKED -> PACKED -> SHIPPED -> DELIVERED,
 * with CANCELLED reachable from the early states. Every transition is
 * validated and recorded in an append-only event log.
 *
 * Key classes / abstractions:
 *   - FulfillmentStatus      : enum of lifecycle states.
 *   - TransitionTable        : declares legal status transitions.
 *   - FulfillmentEvent       : an immutable log entry (id injected).
 *   - Order                  : the fulfilled entity holding current state + log.
 *   - FulfillmentService     : validates and applies transitions.
 *
 * Patterns used:
 *   - State (explicit status enum + transition table)
 *   - Command-ish event log (Observer-flavored audit trail)
 */

import java.util.*;

public class OrderFulfillmentSystemDemo {
    public static void main(String[] args) {
        FulfillmentService service = new FulfillmentService();

        System.out.println("=== Happy Path Fulfillment ===");
        Order o1 = service.placeOrder("ORD-1", "Alice");
        service.advance(o1, FulfillmentStatus.PICKED, "picker-7");
        service.advance(o1, FulfillmentStatus.PACKED, "packer-3");
        service.advance(o1, FulfillmentStatus.SHIPPED, "carrier-DHL");
        service.advance(o1, FulfillmentStatus.DELIVERED, "driver-22");

        System.out.println("\n=== Illegal Transition Attempt ===");
        Order o2 = service.placeOrder("ORD-2", "Bob");
        service.advance(o2, FulfillmentStatus.SHIPPED, "carrier-DHL"); // cannot skip stages

        System.out.println("\n=== Cancellation Flow ===");
        Order o3 = service.placeOrder("ORD-3", "Carol");
        service.advance(o3, FulfillmentStatus.PICKED, "picker-1");
        service.cancel(o3, "out of stock");
        service.advance(o3, FulfillmentStatus.PACKED, "packer-9"); // rejected, terminal

        System.out.println("\n=== Cancel After Shipping (not allowed) ===");
        Order o4 = service.placeOrder("ORD-4", "Dan");
        service.advance(o4, FulfillmentStatus.PICKED, "picker-2");
        service.advance(o4, FulfillmentStatus.PACKED, "packer-2");
        service.advance(o4, FulfillmentStatus.SHIPPED, "carrier-UPS");
        service.cancel(o4, "customer changed mind");

        System.out.println("\n=== Event Log for ORD-1 ===");
        for (FulfillmentEvent e : o1.getLog()) {
            System.out.println("  " + e);
        }
    }
}

enum FulfillmentStatus {
    PLACED, PICKED, PACKED, SHIPPED, DELIVERED, CANCELLED
}

class TransitionTable {
    private final Map<FulfillmentStatus, Set<FulfillmentStatus>> allowed = new EnumMap<>(FulfillmentStatus.class);

    public TransitionTable() {
        allowed.put(FulfillmentStatus.PLACED, EnumSet.of(FulfillmentStatus.PICKED, FulfillmentStatus.CANCELLED));
        allowed.put(FulfillmentStatus.PICKED, EnumSet.of(FulfillmentStatus.PACKED, FulfillmentStatus.CANCELLED));
        allowed.put(FulfillmentStatus.PACKED, EnumSet.of(FulfillmentStatus.SHIPPED, FulfillmentStatus.CANCELLED));
        allowed.put(FulfillmentStatus.SHIPPED, EnumSet.of(FulfillmentStatus.DELIVERED));
        allowed.put(FulfillmentStatus.DELIVERED, EnumSet.noneOf(FulfillmentStatus.class));
        allowed.put(FulfillmentStatus.CANCELLED, EnumSet.noneOf(FulfillmentStatus.class));
    }

    public boolean canTransition(FulfillmentStatus from, FulfillmentStatus to) {
        return allowed.getOrDefault(from, EnumSet.noneOf(FulfillmentStatus.class)).contains(to);
    }
}

class FulfillmentEvent {
    private final int seq;
    private final FulfillmentStatus from;
    private final FulfillmentStatus to;
    private final String actor;
    private final String note;

    public FulfillmentEvent(int seq, FulfillmentStatus from, FulfillmentStatus to, String actor, String note) {
        this.seq = seq;
        this.from = from;
        this.to = to;
        this.actor = actor;
        this.note = note;
    }

    @Override
    public String toString() {
        String arrow = (from == null ? "<new>" : from.name()) + " -> " + to.name();
        return "#" + seq + " " + arrow + " by " + actor + (note.isEmpty() ? "" : " (" + note + ")");
    }
}

class Order {
    private final String id;
    private final String customer;
    private FulfillmentStatus status;
    private final List<FulfillmentEvent> log = new ArrayList<>();
    private int seq = 0;

    public Order(String id, String customer) {
        this.id = id;
        this.customer = customer;
        this.status = FulfillmentStatus.PLACED;
        log.add(new FulfillmentEvent(++seq, null, FulfillmentStatus.PLACED, customer, "order placed"));
    }

    public String getId() { return id; }
    public FulfillmentStatus getStatus() { return status; }
    public List<FulfillmentEvent> getLog() { return Collections.unmodifiableList(log); }

    public void apply(FulfillmentStatus to, String actor, String note) {
        log.add(new FulfillmentEvent(++seq, status, to, actor, note));
        status = to;
    }
}

class FulfillmentService {
    private final TransitionTable table = new TransitionTable();

    public Order placeOrder(String id, String customer) {
        Order order = new Order(id, customer);
        System.out.println(id + " placed for " + customer + " [" + order.getStatus() + "]");
        return order;
    }

    public void advance(Order order, FulfillmentStatus to, String actor) {
        FulfillmentStatus from = order.getStatus();
        if (!table.canTransition(from, to)) {
            System.out.println("  REJECTED " + order.getId() + ": " + from + " -> " + to + " is not allowed");
            return;
        }
        order.apply(to, actor, "");
        System.out.println("  " + order.getId() + ": " + from + " -> " + to + " by " + actor);
    }

    public void cancel(Order order, String reason) {
        FulfillmentStatus from = order.getStatus();
        if (!table.canTransition(from, FulfillmentStatus.CANCELLED)) {
            System.out.println("  REJECTED " + order.getId() + ": cannot cancel from " + from);
            return;
        }
        order.apply(FulfillmentStatus.CANCELLED, "system", reason);
        System.out.println("  " + order.getId() + ": " + from + " -> CANCELLED (" + reason + ")");
    }
}
