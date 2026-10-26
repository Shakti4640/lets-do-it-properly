/*
 * Amazon Order Management - Low Level Design
 * --------------------------------------------------------------
 * Models the lifecycle of an e-commerce order.
 *
 * Key classes:
 *   - Product / Inventory : catalog and stock management.
 *   - Order / OrderItem   : an order and its line items.
 *   - OrderState (+impls) : state machine for order lifecycle.
 *   - PaymentService      : strategy-based payment processing.
 *   - ShipmentService     : creates shipments and tracks them.
 *   - OrderManager        : facade orchestrating order flow.
 *
 * Patterns used:
 *   - State    (OrderState: Created -> Paid -> Shipped -> Delivered / Cancelled)
 *   - Strategy (PaymentMethod: Card / UPI)
 *   - Singleton (Inventory)
 */

import java.util.*;

public class OrderManagementDemo {
    public static void main(String[] args) {
        Inventory inv = Inventory.getInstance();
        inv.addProduct(new Product("P1", "Wireless Mouse", 25.0), 10);
        inv.addProduct(new Product("P2", "Mechanical Keyboard", 80.0), 5);

        OrderManager manager = new OrderManager();

        Order order = manager.createOrder("cust-101");
        manager.addItem(order, "P1", 2);
        manager.addItem(order, "P2", 1);
        System.out.println("Created order " + order.getId() + " total=$" + order.getTotal());
        System.out.println("State: " + order.getState().name());

        System.out.println("\n-- Paying --");
        manager.pay(order, new CardPayment("4111-1111"));
        System.out.println("State: " + order.getState().name());

        System.out.println("\n-- Shipping --");
        manager.ship(order);
        System.out.println("State: " + order.getState().name() + " tracking=" + order.getShipment().getTrackingId());

        System.out.println("\n-- Delivering --");
        manager.deliver(order);
        System.out.println("State: " + order.getState().name());

        System.out.println("\n-- Trying to cancel a delivered order --");
        manager.cancel(order);

        System.out.println("\n-- New order, cancel before payment --");
        Order o2 = manager.createOrder("cust-102");
        manager.addItem(o2, "P1", 1);
        manager.cancel(o2);
        System.out.println("State: " + o2.getState().name());

        System.out.println("\nRemaining stock P1=" + inv.getStock("P1") + " P2=" + inv.getStock("P2"));
    }
}

class Product {
    private final String id; private final String name; private final double price;
    public Product(String id, String name, double price) { this.id = id; this.name = name; this.price = price; }
    public String getId() { return id; } public String getName() { return name; } public double getPrice() { return price; }
}

class Inventory {
    private static Inventory instance;
    private final Map<String, Product> products = new HashMap<>();
    private final Map<String, Integer> stock = new HashMap<>();
    private Inventory() {}
    public static synchronized Inventory getInstance() {
        if (instance == null) instance = new Inventory();
        return instance;
    }
    public void addProduct(Product p, int qty) { products.put(p.getId(), p); stock.merge(p.getId(), qty, Integer::sum); }
    public Product getProduct(String id) { return products.get(id); }
    public int getStock(String id) { return stock.getOrDefault(id, 0); }
    public boolean reserve(String id, int qty) {
        if (getStock(id) < qty) return false;
        stock.merge(id, -qty, Integer::sum); return true;
    }
    public void release(String id, int qty) { stock.merge(id, qty, Integer::sum); }
}

class OrderItem {
    private final Product product; private final int qty;
    public OrderItem(Product product, int qty) { this.product = product; this.qty = qty; }
    public Product getProduct() { return product; } public int getQty() { return qty; }
    public double subtotal() { return product.getPrice() * qty; }
}

class Shipment {
    private final String trackingId; private String status = "DISPATCHED";
    public Shipment(String trackingId) { this.trackingId = trackingId; }
    public String getTrackingId() { return trackingId; }
    public void markDelivered() { status = "DELIVERED"; }
    public String getStatus() { return status; }
}

interface PaymentMethod {
    boolean pay(double amount);
    String name();
}
class CardPayment implements PaymentMethod {
    private final String cardNo;
    public CardPayment(String cardNo) { this.cardNo = cardNo; }
    public boolean pay(double amount) { System.out.println("  Charging $" + amount + " to card " + cardNo); return true; }
    public String name() { return "CARD"; }
}
class UpiPayment implements PaymentMethod {
    private final String vpa;
    public UpiPayment(String vpa) { this.vpa = vpa; }
    public boolean pay(double amount) { System.out.println("  Collecting $" + amount + " via UPI " + vpa); return true; }
    public String name() { return "UPI"; }
}

interface OrderState {
    String name();
    void pay(Order order, PaymentMethod method);
    void ship(Order order);
    void deliver(Order order);
    void cancel(Order order);
}

class CreatedState implements OrderState {
    public String name() { return "CREATED"; }
    public void pay(Order o, PaymentMethod m) {
        if (m.pay(o.getTotal())) { o.setState(new PaidState()); System.out.println("  Payment success via " + m.name()); }
    }
    public void ship(Order o) { System.out.println("  Cannot ship unpaid order"); }
    public void deliver(Order o) { System.out.println("  Cannot deliver unpaid order"); }
    public void cancel(Order o) { o.releaseStock(); o.setState(new CancelledState()); System.out.println("  Order cancelled, stock released"); }
}

class PaidState implements OrderState {
    public String name() { return "PAID"; }
    public void pay(Order o, PaymentMethod m) { System.out.println("  Already paid"); }
    public void ship(Order o) {
        o.setShipment(new Shipment("TRK-" + o.getId()));
        o.setState(new ShippedState());
        System.out.println("  Shipment created");
    }
    public void deliver(Order o) { System.out.println("  Cannot deliver before shipping"); }
    public void cancel(Order o) { o.releaseStock(); o.setState(new CancelledState()); System.out.println("  Order cancelled & refunded"); }
}

class ShippedState implements OrderState {
    public String name() { return "SHIPPED"; }
    public void pay(Order o, PaymentMethod m) { System.out.println("  Already paid"); }
    public void ship(Order o) { System.out.println("  Already shipped"); }
    public void deliver(Order o) { o.getShipment().markDelivered(); o.setState(new DeliveredState()); System.out.println("  Delivered"); }
    public void cancel(Order o) { System.out.println("  Cannot cancel a shipped order"); }
}

class DeliveredState implements OrderState {
    public String name() { return "DELIVERED"; }
    public void pay(Order o, PaymentMethod m) { System.out.println("  Already completed"); }
    public void ship(Order o) { System.out.println("  Already delivered"); }
    public void deliver(Order o) { System.out.println("  Already delivered"); }
    public void cancel(Order o) { System.out.println("  Cannot cancel a delivered order"); }
}

class CancelledState implements OrderState {
    public String name() { return "CANCELLED"; }
    public void pay(Order o, PaymentMethod m) { System.out.println("  Order is cancelled"); }
    public void ship(Order o) { System.out.println("  Order is cancelled"); }
    public void deliver(Order o) { System.out.println("  Order is cancelled"); }
    public void cancel(Order o) { System.out.println("  Already cancelled"); }
}

class Order {
    private final String id; private final String customerId;
    private final List<OrderItem> items = new ArrayList<>();
    private OrderState state = new CreatedState();
    private Shipment shipment;
    public Order(String id, String customerId) { this.id = id; this.customerId = customerId; }
    public String getId() { return id; }
    public String getCustomerId() { return customerId; }
    public List<OrderItem> getItems() { return items; }
    public void addItem(OrderItem i) { items.add(i); }
    public double getTotal() { return items.stream().mapToDouble(OrderItem::subtotal).sum(); }
    public OrderState getState() { return state; }
    public void setState(OrderState s) { this.state = s; }
    public Shipment getShipment() { return shipment; }
    public void setShipment(Shipment s) { this.shipment = s; }
    public void releaseStock() {
        for (OrderItem i : items) Inventory.getInstance().release(i.getProduct().getId(), i.getQty());
    }
}

class OrderManager {
    private int counter = 0;
    public Order createOrder(String customerId) { return new Order("ORD" + (++counter), customerId); }
    public void addItem(Order order, String productId, int qty) {
        Inventory inv = Inventory.getInstance();
        Product p = inv.getProduct(productId);
        if (p == null) { System.out.println("  Unknown product " + productId); return; }
        if (!inv.reserve(productId, qty)) { System.out.println("  Insufficient stock for " + productId); return; }
        order.addItem(new OrderItem(p, qty));
    }
    public void pay(Order order, PaymentMethod method) { order.getState().pay(order, method); }
    public void ship(Order order) { order.getState().ship(order); }
    public void deliver(Order order) { order.getState().deliver(order); }
    public void cancel(Order order) { order.getState().cancel(order); }
}
