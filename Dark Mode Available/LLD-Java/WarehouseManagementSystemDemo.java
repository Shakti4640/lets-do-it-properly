/*
 * Warehouse Management System - Low Level Design
 * --------------------------------------------------------------
 * Tracks SKU inventory across physical bins/locations. Supports inbound
 * putaway, outbound picking (FIFO across bins), live stock levels, and
 * low-stock alerts published to subscribed listeners.
 *
 * Key classes / abstractions:
 *   - Sku            : a stock-keeping unit definition.
 *   - Bin            : a physical location holding qty of a single SKU.
 *   - StockAlert     : observer notified when a SKU dips below reorder point.
 *   - Warehouse      : facade for putaway, pick, and stock queries.
 *
 * Patterns used:
 *   - Observer (low-stock alerts)
 *   - Facade   (Warehouse orchestrates bins + SKUs)
 */

import java.util.*;

public class WarehouseManagementSystemDemo {
    public static void main(String[] args) {
        Warehouse wh = new Warehouse();
        wh.subscribe(new ConsoleStockAlert());

        wh.defineSku(new Sku("SKU-APPLE", "Red Apple", 20));
        wh.defineSku(new Sku("SKU-MILK", "Milk 1L", 10));

        System.out.println("=== Inbound Putaway ===");
        wh.putaway("SKU-APPLE", "A1-01", 30);
        wh.putaway("SKU-APPLE", "A1-02", 25);
        wh.putaway("SKU-MILK", "C3-09", 15);

        System.out.println("\n=== Stock Levels ===");
        wh.printStock();

        System.out.println("\n=== Outbound Pick (FIFO across bins) ===");
        wh.pick("SKU-APPLE", 40); // drains A1-01 fully then part of A1-02
        wh.pick("SKU-MILK", 8);   // triggers low-stock alert (7 < 10)

        System.out.println("\n=== Stock Levels After Picking ===");
        wh.printStock();

        System.out.println("\n=== Over-pick Attempt ===");
        wh.pick("SKU-MILK", 50);  // insufficient

        System.out.println("\n=== Bin Layout ===");
        wh.printBins();
    }
}

class Sku {
    private final String code;
    private final String name;
    private final int reorderPoint;

    public Sku(String code, String name, int reorderPoint) {
        this.code = code;
        this.name = name;
        this.reorderPoint = reorderPoint;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public int getReorderPoint() { return reorderPoint; }
}

class Bin {
    private final String location;
    private final String skuCode;
    private int qty;

    public Bin(String location, String skuCode, int qty) {
        this.location = location;
        this.skuCode = skuCode;
        this.qty = qty;
    }

    public String getLocation() { return location; }
    public String getSkuCode() { return skuCode; }
    public int getQty() { return qty; }

    public int removeUpTo(int wanted) {
        int taken = Math.min(wanted, qty);
        qty -= taken;
        return taken;
    }
}

interface StockAlert {
    void onLowStock(Sku sku, int currentQty);
}

class ConsoleStockAlert implements StockAlert {
    public void onLowStock(Sku sku, int currentQty) {
        System.out.println("  [ALERT] " + sku.getCode() + " (" + sku.getName() + ") low: "
                + currentQty + " <= reorder point " + sku.getReorderPoint());
    }
}

class Warehouse {
    private final Map<String, Sku> skus = new HashMap<>();
    private final Map<String, List<Bin>> binsBySku = new LinkedHashMap<>();
    private final List<StockAlert> listeners = new ArrayList<>();

    public void subscribe(StockAlert listener) { listeners.add(listener); }

    public void defineSku(Sku sku) {
        skus.put(sku.getCode(), sku);
        binsBySku.putIfAbsent(sku.getCode(), new ArrayList<Bin>());
    }

    public void putaway(String skuCode, String location, int qty) {
        if (!skus.containsKey(skuCode)) {
            System.out.println("  Unknown SKU " + skuCode);
            return;
        }
        binsBySku.get(skuCode).add(new Bin(location, skuCode, qty));
        System.out.println("  Putaway " + qty + " of " + skuCode + " into bin " + location);
    }

    public int stockOf(String skuCode) {
        int total = 0;
        for (Bin b : binsBySku.getOrDefault(skuCode, Collections.<Bin>emptyList())) {
            total += b.getQty();
        }
        return total;
    }

    public void pick(String skuCode, int qty) {
        if (!skus.containsKey(skuCode)) {
            System.out.println("  Unknown SKU " + skuCode);
            return;
        }
        if (stockOf(skuCode) < qty) {
            System.out.println("  INSUFFICIENT stock to pick " + qty + " of " + skuCode
                    + " (have " + stockOf(skuCode) + ")");
            return;
        }
        int remaining = qty;
        for (Bin b : binsBySku.get(skuCode)) {
            if (remaining == 0) break;
            int taken = b.removeUpTo(remaining);
            if (taken > 0) {
                System.out.println("  Picked " + taken + " of " + skuCode + " from bin " + b.getLocation());
                remaining -= taken;
            }
        }
        int now = stockOf(skuCode);
        Sku sku = skus.get(skuCode);
        if (now <= sku.getReorderPoint()) {
            for (StockAlert l : listeners) l.onLowStock(sku, now);
        }
    }

    public void printStock() {
        for (String code : binsBySku.keySet()) {
            System.out.println("  " + code + " = " + stockOf(code) + " units");
        }
    }

    public void printBins() {
        for (Map.Entry<String, List<Bin>> e : binsBySku.entrySet()) {
            for (Bin b : e.getValue()) {
                System.out.println("  " + b.getLocation() + " : " + b.getSkuCode() + " x" + b.getQty());
            }
        }
    }
}
