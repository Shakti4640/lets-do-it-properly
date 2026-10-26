/*
 * Grocery Delivery App - Low Level Design
 * --------------------------------------------------------------
 * Customers browse a store catalog, add items to a cart, place an order
 * against live inventory, and get assigned an available delivery slot. The
 * order then moves through a simple status lifecycle.
 *
 * Key classes / abstractions:
 *   - Store / CatalogItem : a store and the items it stocks (price + qty).
 *   - Cart                : line items chosen by a customer.
 *   - DeliverySlot        : a bookable time window with capacity.
 *   - Order / OrderStatus : placed order and its lifecycle.
 *   - GroceryService      : facade for browse, checkout, slot assignment.
 *
 * Patterns used:
 *   - Builder (Cart accumulation)
 *   - Facade  (GroceryService)
 *   - State   (OrderStatus lifecycle)
 */

import java.util.*;

public class GroceryDeliveryAppDemo {
    public static void main(String[] args) {
        Store store = new Store("BigBasket-Koramangala");
        store.stock(new CatalogItem("Tomato 1kg", 40.0), 50);
        store.stock(new CatalogItem("Milk 1L", 56.0), 20);
        store.stock(new CatalogItem("Bread", 35.0), 8);

        GroceryService service = new GroceryService(store);
        service.addSlot(new DeliverySlot("SLOT-9to11", "09:00-11:00", 2));
        service.addSlot(new DeliverySlot("SLOT-11to13", "11:00-13:00", 1));

        System.out.println("=== Catalog ===");
        store.printCatalog();

        System.out.println("\n=== Customer builds a cart ===");
        Cart cart = new Cart("cust-1")
                .add("Tomato 1kg", 2)
                .add("Milk 1L", 3)
                .add("Bread", 1);
        cart.print();

        System.out.println("\n=== Place order, assign first available slot ===");
        Order order = service.placeOrder(cart);
        if (order != null) {
            System.out.println("  Order " + order.getId() + " total=Rs" + order.getTotal()
                    + " slot=" + order.getSlot().getWindow() + " status=" + order.getStatus());
        }

        System.out.println("\n=== Advance order status ===");
        service.advance(order); // PLACED -> PACKED
        service.advance(order); // PACKED -> OUT_FOR_DELIVERY
        service.advance(order); // OUT_FOR_DELIVERY -> DELIVERED

        System.out.println("\n=== Inventory after order ===");
        store.printCatalog();

        System.out.println("\n=== Over-order (not enough bread) ===");
        Cart c2 = new Cart("cust-2").add("Bread", 99);
        service.placeOrder(c2);
    }
}

class CatalogItem {
    private final String name;
    private final double price;
    public CatalogItem(String name, double price) { this.name = name; this.price = price; }
    public String getName() { return name; }
    public double getPrice() { return price; }
}

class Store {
    private final String name;
    private final Map<String, CatalogItem> items = new LinkedHashMap<>();
    private final Map<String, Integer> stock = new HashMap<>();

    public Store(String name) { this.name = name; }

    public void stock(CatalogItem item, int qty) {
        items.put(item.getName(), item);
        stock.merge(item.getName(), qty, Integer::sum);
    }

    public CatalogItem get(String name) { return items.get(name); }
    public int available(String name) { return stock.getOrDefault(name, 0); }

    public boolean deduct(String name, int qty) {
        if (available(name) < qty) return false;
        stock.merge(name, -qty, Integer::sum);
        return true;
    }

    public void printCatalog() {
        for (CatalogItem i : items.values()) {
            System.out.println("  " + i.getName() + " - Rs" + i.getPrice() + " (stock " + available(i.getName()) + ")");
        }
    }
}

class CartLine {
    final String itemName;
    final int qty;
    CartLine(String itemName, int qty) { this.itemName = itemName; this.qty = qty; }
}

class Cart {
    private final String customerId;
    private final List<CartLine> lines = new ArrayList<>();

    public Cart(String customerId) { this.customerId = customerId; }

    public Cart add(String itemName, int qty) {
        lines.add(new CartLine(itemName, qty));
        return this;
    }

    public String getCustomerId() { return customerId; }
    public List<CartLine> getLines() { return lines; }

    public void print() {
        for (CartLine l : lines) System.out.println("  " + l.qty + " x " + l.itemName);
    }
}

class DeliverySlot {
    private final String id;
    private final String window;
    private int capacity;

    public DeliverySlot(String id, String window, int capacity) {
        this.id = id;
        this.window = window;
        this.capacity = capacity;
    }

    public String getWindow() { return window; }
    public boolean hasCapacity() { return capacity > 0; }
    public void book() { capacity--; }
}

enum OrderStatus { PLACED, PACKED, OUT_FOR_DELIVERY, DELIVERED }

class Order {
    private final String id;
    private final double total;
    private final DeliverySlot slot;
    private OrderStatus status = OrderStatus.PLACED;

    public Order(String id, double total, DeliverySlot slot) {
        this.id = id;
        this.total = total;
        this.slot = slot;
    }

    public String getId() { return id; }
    public double getTotal() { return total; }
    public DeliverySlot getSlot() { return slot; }
    public OrderStatus getStatus() { return status; }

    public void advance() {
        OrderStatus[] all = OrderStatus.values();
        if (status.ordinal() < all.length - 1) {
            status = all[status.ordinal() + 1];
        }
    }
}

class GroceryService {
    private final Store store;
    private final List<DeliverySlot> slots = new ArrayList<>();
    private int orderCounter = 0;

    public GroceryService(Store store) { this.store = store; }

    public void addSlot(DeliverySlot slot) { slots.add(slot); }

    private DeliverySlot nextAvailableSlot() {
        for (DeliverySlot s : slots) {
            if (s.hasCapacity()) return s;
        }
        return null;
    }

    public Order placeOrder(Cart cart) {
        // validate stock
        for (CartLine l : cart.getLines()) {
            if (store.get(l.itemName) == null) {
                System.out.println("  Unknown item " + l.itemName);
                return null;
            }
            if (store.available(l.itemName) < l.qty) {
                System.out.println("  Insufficient stock for " + l.itemName
                        + " (need " + l.qty + ", have " + store.available(l.itemName) + ")");
                return null;
            }
        }
        DeliverySlot slot = nextAvailableSlot();
        if (slot == null) {
            System.out.println("  No delivery slots available");
            return null;
        }
        double total = 0;
        for (CartLine l : cart.getLines()) {
            store.deduct(l.itemName, l.qty);
            total += store.get(l.itemName).getPrice() * l.qty;
        }
        slot.book();
        Order order = new Order("GORD-" + (++orderCounter), total, slot);
        System.out.println("  Placed " + order.getId());
        return order;
    }

    public void advance(Order order) {
        if (order == null) return;
        order.advance();
        System.out.println("  " + order.getId() + " -> " + order.getStatus());
    }
}
