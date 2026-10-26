/*
 * Restaurant Management System - Low Level Design
 * -----------------------------------------------
 * Manages tables, orders placed against tables, kitchen ticket flow and
 * bill generation.
 *
 * Key classes:
 *   - Table / MenuItem / Menu
 *   - Order (+ OrderLine) with status
 *   - Bill
 *   - RestaurantManager (facade)
 *
 * Patterns used: Facade, Enum status, Encapsulation.
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RestaurantManagementDemo {
    public static void main(String[] args) {
        RestaurantManager manager = new RestaurantManager();
        manager.addTable(new Table(1, 4));
        manager.addTable(new Table(2, 2));
        manager.getMenu().add(new MenuItem("Pasta", 250));
        manager.getMenu().add(new MenuItem("Pizza", 400));
        manager.getMenu().add(new MenuItem("Juice", 100));

        System.out.println("=== Restaurant Management Demo ===");
        Table t = manager.assignTable(3); // party of 3 -> table 1
        if (t != null) {
            Order order = manager.createOrder(t);
            manager.addItem(order, "Pizza", 1);
            manager.addItem(order, "Pasta", 2);
            manager.addItem(order, "Juice", 3);
            manager.sendToKitchen(order);
            manager.markServed(order);
            Bill bill = manager.generateBill(order);
            bill.print();
            manager.freeTable(t);
        }
    }
}

class Table {
    final int id; final int capacity; boolean occupied = false;
    Table(int id, int capacity) { this.id = id; this.capacity = capacity; }
}

class MenuItem {
    final String name; final double price;
    MenuItem(String name, double price) { this.name = name; this.price = price; }
}

class Menu {
    private final Map<String, MenuItem> items = new HashMap<String, MenuItem>();
    void add(MenuItem item) { items.put(item.name, item); }
    MenuItem get(String name) { return items.get(name); }
}

class OrderLine {
    final MenuItem item; final int qty;
    OrderLine(MenuItem item, int qty) { this.item = item; this.qty = qty; }
    double amount() { return item.price * qty; }
}

enum OrderState { NEW, IN_KITCHEN, SERVED, BILLED }

class Order {
    final String id; final Table table;
    final List<OrderLine> lines = new ArrayList<OrderLine>();
    OrderState state = OrderState.NEW;
    Order(String id, Table table) { this.id = id; this.table = table; }
    double total() { double t = 0; for (OrderLine l : lines) t += l.amount(); return t; }
}

class Bill {
    private final Order order; private final double tax;
    Bill(Order order, double taxRate) { this.order = order; this.tax = order.total() * taxRate; }
    void print() {
        System.out.println("----- BILL (Order " + order.id + ", Table " + order.table.id + ") -----");
        for (OrderLine l : order.lines)
            System.out.println("  " + l.qty + " x " + l.item.name + " = Rs " + l.amount());
        System.out.println("  Subtotal: Rs " + order.total());
        System.out.println("  Tax:      Rs " + tax);
        System.out.println("  TOTAL:    Rs " + (order.total() + tax));
    }
}

class RestaurantManager {
    private final List<Table> tables = new ArrayList<Table>();
    private final Menu menu = new Menu();
    private int orderSeq = 0;

    void addTable(Table t) { tables.add(t); }
    Menu getMenu() { return menu; }

    Table assignTable(int partySize) {
        for (Table t : tables) {
            if (!t.occupied && t.capacity >= partySize) {
                t.occupied = true;
                System.out.println("Assigned table " + t.id + " for party of " + partySize);
                return t;
            }
        }
        System.out.println("No table for party of " + partySize);
        return null;
    }

    Order createOrder(Table t) {
        Order o = new Order("ORD" + (++orderSeq), t);
        System.out.println("Created " + o.id + " for table " + t.id);
        return o;
    }

    void addItem(Order order, String name, int qty) {
        MenuItem item = menu.get(name);
        if (item == null) { System.out.println("No item: " + name); return; }
        order.lines.add(new OrderLine(item, qty));
        System.out.println("  + " + qty + " x " + name);
    }

    void sendToKitchen(Order order) { order.state = OrderState.IN_KITCHEN; System.out.println(order.id + " -> KITCHEN"); }
    void markServed(Order order) { order.state = OrderState.SERVED; System.out.println(order.id + " -> SERVED"); }

    Bill generateBill(Order order) {
        order.state = OrderState.BILLED;
        return new Bill(order, 0.05);
    }

    void freeTable(Table t) { t.occupied = false; System.out.println("Table " + t.id + " freed"); }
}
