/*
 * Coupon / Discount Engine - Low Level Design
 * -------------------------------------------
 * Supports several coupon types (percentage, flat, threshold, BOGO) via a
 * Strategy interface. Each coupon has a validity window and a "stackable"
 * flag. The engine validates coupons against an order at a given time, then
 * either combines all stackable coupons or picks the single best one.
 *
 * Key classes / abstractions:
 *   - Coupon (+ Percentage/Flat/Threshold/Bogo) : Strategy
 *   - Order / OrderLine
 *   - CouponEngine : validity check, stacking, best-discount selection
 *
 * Patterns used: Strategy (coupon math), Facade (CouponEngine).
 */

import java.util.ArrayList;
import java.util.List;

public class CouponDiscountEngineDemo {
    public static void main(String[] args) {
        // Logical "now" = day 10 (deterministic, no real clock).
        int now = 10;

        Order order = new Order();
        order.addLine("Shirt", 2500, 2);   // $25.00 x2
        order.addLine("Jeans", 6000, 1);   // $60.00
        order.addLine("Socks", 800, 3);    // $8.00 x3

        System.out.println("=== Coupon / Discount Engine Demo ===");
        System.out.println("Order subtotal: " + money(order.subtotal()));

        List<Coupon> coupons = new ArrayList<Coupon>();
        coupons.add(new PercentageCoupon("SAVE10", 10, true, 1, 30));
        coupons.add(new FlatCoupon("FLAT5", 500, true, 1, 30));
        coupons.add(new ThresholdCoupon("OVER100", 10000, 1500, false, 1, 30));
        coupons.add(new BogoCoupon("BOGOSOCKS", "Socks", true, 1, 30));
        coupons.add(new FlatCoupon("EXPIRED", 2000, true, 1, 5)); // expired by day 10

        CouponEngine engine = new CouponEngine();

        System.out.println("\n--- Validity check @ day " + now + " ---");
        List<Coupon> valid = engine.filterValid(coupons, order, now);
        for (Coupon c : valid) System.out.println("  valid: " + c.code());

        System.out.println("\n--- Best single coupon ---");
        Coupon best = engine.bestSingle(valid, order);
        if (best != null) {
            long off = best.discountCents(order);
            System.out.println("  " + best.code() + " -> -" + money(off)
                    + " (total " + money(order.subtotal() - off) + ")");
        }

        System.out.println("\n--- Combined stackable coupons ---");
        long stacked = engine.applyStackable(valid, order);
        System.out.println("  total discount -" + money(stacked)
                + " (total " + money(order.subtotal() - stacked) + ")");
    }

    static String money(long cents) {
        return String.format("$%,.2f", cents / 100.0);
    }
}

class OrderLine {
    final String name;
    final long unitPriceCents;
    final int quantity;
    OrderLine(String name, long unitPriceCents, int quantity) {
        this.name = name;
        this.unitPriceCents = unitPriceCents;
        this.quantity = quantity;
    }
    long lineTotal() { return unitPriceCents * quantity; }
}

class Order {
    final List<OrderLine> lines = new ArrayList<OrderLine>();
    void addLine(String name, long unitPriceCents, int quantity) {
        lines.add(new OrderLine(name, unitPriceCents, quantity));
    }
    long subtotal() {
        long total = 0;
        for (OrderLine l : lines) total += l.lineTotal();
        return total;
    }
}

abstract class Coupon {
    private final String code;
    private final boolean stackable;
    private final int validFromDay;
    private final int validToDay;

    Coupon(String code, boolean stackable, int validFromDay, int validToDay) {
        this.code = code;
        this.stackable = stackable;
        this.validFromDay = validFromDay;
        this.validToDay = validToDay;
    }

    String code() { return code; }
    boolean isStackable() { return stackable; }

    boolean isValidOn(int day, Order order) {
        return day >= validFromDay && day <= validToDay && isApplicable(order);
    }

    /** Override when a coupon needs the order to qualify (e.g. threshold/BOGO). */
    boolean isApplicable(Order order) { return true; }

    abstract long discountCents(Order order);
}

class PercentageCoupon extends Coupon {
    private final int percent;
    PercentageCoupon(String code, int percent, boolean stackable, int from, int to) {
        super(code, stackable, from, to);
        this.percent = percent;
    }
    long discountCents(Order order) { return order.subtotal() * percent / 100; }
}

class FlatCoupon extends Coupon {
    private final long flatOffCents;
    FlatCoupon(String code, long flatOffCents, boolean stackable, int from, int to) {
        super(code, stackable, from, to);
        this.flatOffCents = flatOffCents;
    }
    long discountCents(Order order) { return Math.min(flatOffCents, order.subtotal()); }
}

class ThresholdCoupon extends Coupon {
    private final long thresholdCents;
    private final long flatOffCents;
    ThresholdCoupon(String code, long thresholdCents, long flatOffCents,
                    boolean stackable, int from, int to) {
        super(code, stackable, from, to);
        this.thresholdCents = thresholdCents;
        this.flatOffCents = flatOffCents;
    }
    boolean isApplicable(Order order) { return order.subtotal() >= thresholdCents; }
    long discountCents(Order order) { return isApplicable(order) ? flatOffCents : 0; }
}

class BogoCoupon extends Coupon {
    private final String productName;
    BogoCoupon(String code, String productName, boolean stackable, int from, int to) {
        super(code, stackable, from, to);
        this.productName = productName;
    }
    boolean isApplicable(Order order) {
        for (OrderLine l : order.lines) {
            if (l.name.equals(productName) && l.quantity >= 2) return true;
        }
        return false;
    }
    long discountCents(Order order) {
        for (OrderLine l : order.lines) {
            if (l.name.equals(productName)) {
                int freeUnits = l.quantity / 2; // buy one, get one free
                return l.unitPriceCents * freeUnits;
            }
        }
        return 0;
    }
}

class CouponEngine {
    List<Coupon> filterValid(List<Coupon> coupons, Order order, int day) {
        List<Coupon> valid = new ArrayList<Coupon>();
        for (Coupon c : coupons) {
            if (c.isValidOn(day, order)) valid.add(c);
        }
        return valid;
    }

    Coupon bestSingle(List<Coupon> coupons, Order order) {
        Coupon best = null;
        long bestOff = -1;
        for (Coupon c : coupons) {
            long off = c.discountCents(order);
            if (off > bestOff) {
                bestOff = off;
                best = c;
            }
        }
        return best;
    }

    long applyStackable(List<Coupon> coupons, Order order) {
        long total = 0;
        for (Coupon c : coupons) {
            if (c.isStackable()) total += c.discountCents(order);
        }
        return Math.min(total, order.subtotal());
    }
}
