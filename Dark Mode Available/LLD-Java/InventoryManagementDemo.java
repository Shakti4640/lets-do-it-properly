/*
 * Inventory Management System - Low Level Design
 * ----------------------------------------------
 * Tracks products and stock across warehouses, supports stock in/out,
 * low-stock alerts via the Observer pattern, and reorder thresholds.
 *
 * Key classes:
 *   - Product / Warehouse / StockItem
 *   - InventoryObserver (+ LowStockAlert) : Observer pattern
 *   - InventoryManager (facade)
 *
 * Patterns used: Observer (alerts), Facade, Encapsulation.
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InventoryManagementDemo {
    public static void main(String[] args) {
        InventoryManager manager = new InventoryManager();
        manager.addObserver(new LowStockAlert());
        manager.addProduct(new Product("SKU1", "Widget", 10)); // reorder at 10
        manager.addProduct(new Product("SKU2", "Gadget", 5));
        manager.addWarehouse(new Warehouse("WH1", "North"));

        System.out.println("=== Inventory Management Demo ===");
        manager.stockIn("WH1", "SKU1", 50);
        manager.stockIn("WH1", "SKU2", 8);

        manager.stockOut("WH1", "SKU1", 45); // drops to 5 -> below 10, alert
        manager.stockOut("WH1", "SKU2", 10); // only 8 available
        manager.report();
    }
}

class Product {
    final String sku; final String name; final int reorderLevel;
    Product(String sku, String name, int reorderLevel) { this.sku = sku; this.name = name; this.reorderLevel = reorderLevel; }
}

class StockItem {
    final Product product; int quantity = 0;
    StockItem(Product product) { this.product = product; }
}

class Warehouse {
    final String id; final String name;
    final Map<String, StockItem> stock = new HashMap<String, StockItem>();
    Warehouse(String id, String name) { this.id = id; this.name = name; }
}

interface InventoryObserver {
    void onLowStock(Warehouse wh, Product product, int currentQty);
}

class LowStockAlert implements InventoryObserver {
    public void onLowStock(Warehouse wh, Product product, int currentQty) {
        System.out.println("  [ALERT] " + product.name + " low at " + wh.name
                + " (qty=" + currentQty + ", reorder<=" + product.reorderLevel + ")");
    }
}

class InventoryManager {
    private final Map<String, Product> products = new HashMap<String, Product>();
    private final Map<String, Warehouse> warehouses = new HashMap<String, Warehouse>();
    private final List<InventoryObserver> observers = new ArrayList<InventoryObserver>();

    void addProduct(Product p) { products.put(p.sku, p); }
    void addWarehouse(Warehouse w) { warehouses.put(w.id, w); }
    void addObserver(InventoryObserver o) { observers.add(o); }

    void stockIn(String warehouseId, String sku, int qty) {
        Warehouse wh = warehouses.get(warehouseId);
        Product p = products.get(sku);
        StockItem item = wh.stock.computeIfAbsent(sku, k -> new StockItem(p));
        item.quantity += qty;
        System.out.println("Stock IN  " + qty + " x " + p.name + " @ " + wh.name + " (now " + item.quantity + ")");
    }

    void stockOut(String warehouseId, String sku, int qty) {
        Warehouse wh = warehouses.get(warehouseId);
        Product p = products.get(sku);
        StockItem item = wh.stock.get(sku);
        if (item == null || item.quantity < qty) {
            System.out.println("Stock OUT FAILED " + qty + " x " + p.name + " (have " + (item == null ? 0 : item.quantity) + ")");
            return;
        }
        item.quantity -= qty;
        System.out.println("Stock OUT " + qty + " x " + p.name + " @ " + wh.name + " (now " + item.quantity + ")");
        if (item.quantity <= p.reorderLevel) {
            for (InventoryObserver o : observers) o.onLowStock(wh, p, item.quantity);
        }
    }

    void report() {
        System.out.println("--- Inventory Report ---");
        for (Warehouse wh : warehouses.values()) {
            for (StockItem si : wh.stock.values()) {
                System.out.println("  " + wh.name + " | " + si.product.name + " = " + si.quantity);
            }
        }
    }
}
