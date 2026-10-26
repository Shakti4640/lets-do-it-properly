/*
 * Food Delivery Application - Low Level Design
 * --------------------------------------------
 * Customers browse restaurants, place orders, and the system assigns a
 * delivery agent. Tracks order lifecycle and bill computation.
 *
 * Key classes:
 *   - Restaurant / MenuItem
 *   - Customer / DeliveryAgent
 *   - Order (+ OrderItem) with status lifecycle
 *   - FoodDeliveryService (facade + agent assignment)
 *
 * Patterns used: Facade, State-ish order status, Strategy (agent pick).
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FoodDeliveryDemo {
    public static void main(String[] args) {
        FoodDeliveryService service = new FoodDeliveryService();
        Restaurant r = new Restaurant("RES1", "Tasty Bites");
        r.addItem(new MenuItem("Burger", 120));
        r.addItem(new MenuItem("Fries", 60));
        r.addItem(new MenuItem("Coke", 40));
        service.addRestaurant(r);
        service.addAgent(new DeliveryAgent("AG1", "Ravi"));
        service.addAgent(new DeliveryAgent("AG2", "Meena"));

        Customer alice = new Customer("C1", "Alice");

        System.out.println("=== Food Delivery Demo ===");
        Order order = service.createOrder(alice, "RES1");
        service.addToOrder(order, "Burger", 2);
        service.addToOrder(order, "Coke", 1);
        service.placeOrder(order);
        service.assignAgent(order);
        service.advance(order); // PREPARING
        service.advance(order); // OUT_FOR_DELIVERY
        service.advance(order); // DELIVERED
        System.out.println("Total bill: Rs " + order.total());
    }
}

class MenuItem {
    final String name; final double price;
    MenuItem(String name, double price) { this.name = name; this.price = price; }
}

class Restaurant {
    final String id; final String name;
    private final Map<String, MenuItem> menu = new HashMap<String, MenuItem>();
    Restaurant(String id, String name) { this.id = id; this.name = name; }
    void addItem(MenuItem item) { menu.put(item.name, item); }
    MenuItem getItem(String name) { return menu.get(name); }
}

class Customer { final String id; final String name; Customer(String id, String name) { this.id = id; this.name = name; } }

class DeliveryAgent {
    final String id; final String name; boolean available = true;
    DeliveryAgent(String id, String name) { this.id = id; this.name = name; }
}

class OrderItem {
    final MenuItem item; final int qty;
    OrderItem(MenuItem item, int qty) { this.item = item; this.qty = qty; }
    double subtotal() { return item.price * qty; }
}

enum OrderStatus { CREATED, PLACED, PREPARING, OUT_FOR_DELIVERY, DELIVERED }

class Order {
    final String id; final Customer customer; final Restaurant restaurant;
    final List<OrderItem> items = new ArrayList<OrderItem>();
    OrderStatus status = OrderStatus.CREATED;
    DeliveryAgent agent;
    Order(String id, Customer customer, Restaurant restaurant) {
        this.id = id; this.customer = customer; this.restaurant = restaurant;
    }
    double total() { double t = 0; for (OrderItem oi : items) t += oi.subtotal(); return t; }
}

class FoodDeliveryService {
    private final Map<String, Restaurant> restaurants = new HashMap<String, Restaurant>();
    private final List<DeliveryAgent> agents = new ArrayList<DeliveryAgent>();
    private int orderSeq = 0;

    void addRestaurant(Restaurant r) { restaurants.put(r.id, r); }
    void addAgent(DeliveryAgent a) { agents.add(a); }

    Order createOrder(Customer c, String restaurantId) {
        Restaurant r = restaurants.get(restaurantId);
        Order order = new Order("ORD" + (++orderSeq), c, r);
        System.out.println(c.name + " started order " + order.id + " at " + r.name);
        return order;
    }

    void addToOrder(Order order, String itemName, int qty) {
        MenuItem item = order.restaurant.getItem(itemName);
        if (item == null) { System.out.println("No item: " + itemName); return; }
        order.items.add(new OrderItem(item, qty));
        System.out.println("  + " + qty + " x " + itemName);
    }

    void placeOrder(Order order) {
        order.status = OrderStatus.PLACED;
        System.out.println("Order " + order.id + " PLACED (Rs " + order.total() + ")");
    }

    void assignAgent(Order order) {
        for (DeliveryAgent a : agents) {
            if (a.available) {
                a.available = false;
                order.agent = a;
                System.out.println("Agent " + a.name + " assigned to " + order.id);
                return;
            }
        }
        System.out.println("No agents free.");
    }

    void advance(Order order) {
        switch (order.status) {
            case PLACED: order.status = OrderStatus.PREPARING; break;
            case PREPARING: order.status = OrderStatus.OUT_FOR_DELIVERY; break;
            case OUT_FOR_DELIVERY:
                order.status = OrderStatus.DELIVERED;
                if (order.agent != null) order.agent.available = true;
                break;
            default: break;
        }
        System.out.println("Order " + order.id + " -> " + order.status);
    }
}
