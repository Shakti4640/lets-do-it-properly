/*
 * Shopping Cart (E-Commerce) - Low Level Design
 * ---------------------------------------------
 * Customers add products to a cart, update quantities, and remove items.
 * The cart computes line totals and subtotal, applies a pluggable discount
 * strategy, and produces a checkout summary.
 *
 * Key classes / abstractions:
 *   - Product            : catalog item (id, name, unit price in cents)
 *   - CartItem           : product + quantity
 *   - DiscountStrategy   : Strategy for computing a discount on the subtotal
 *   - ShoppingCart       : add/remove/update, totals, checkout
 *
 * Patterns used: Strategy (discount), Facade (cart/checkout).
 */

import java.util.LinkedHashMap;
import java.util.Map;

public class ShoppingCartECommerceDemo {
    public static void main(String[] args) {
        Product laptop = new Product("P1", "Laptop", 120000);   // $1200.00
        Product mouse  = new Product("P2", "Mouse", 2500);      // $25.00
        Product cable  = new Product("P3", "USB-C Cable", 1200); // $12.00

        ShoppingCart cart = new ShoppingCart("alice");

        System.out.println("=== Shopping Cart E-Commerce Demo ===");
        cart.addItem(laptop, 1);
        cart.addItem(mouse, 2);
        cart.addItem(cable, 3);

        System.out.println("\n--- Cart after adds ---");
        cart.printCart();

        cart.updateQuantity("P3", 1);
        cart.removeItem("P2");

        System.out.println("\n--- Cart after update/remove ---");
        cart.printCart();

        System.out.println("\n--- Checkout with no discount ---");
        cart.checkout(new NoDiscount());

        System.out.println("\n--- Checkout with 10% off ---");
        cart.checkout(new PercentageDiscount(10));

        System.out.println("\n--- Checkout with $50 off over $1000 ---");
        cart.checkout(new ThresholdDiscount(100000, 5000));
    }

    static String money(long cents) {
        return String.format("$%,.2f", cents / 100.0);
    }
}

class Product {
    final String id;
    final String name;
    final long unitPriceCents;
    Product(String id, String name, long unitPriceCents) {
        this.id = id;
        this.name = name;
        this.unitPriceCents = unitPriceCents;
    }
}

class CartItem {
    final Product product;
    int quantity;
    CartItem(Product product, int quantity) {
        this.product = product;
        this.quantity = quantity;
    }
    long lineTotal() { return product.unitPriceCents * quantity; }
}

interface DiscountStrategy {
    long discountCents(long subtotalCents);
    String describe();
}

class NoDiscount implements DiscountStrategy {
    public long discountCents(long subtotalCents) { return 0; }
    public String describe() { return "No discount"; }
}

class PercentageDiscount implements DiscountStrategy {
    private final int percent;
    PercentageDiscount(int percent) { this.percent = percent; }
    public long discountCents(long subtotalCents) { return subtotalCents * percent / 100; }
    public String describe() { return percent + "% off"; }
}

class ThresholdDiscount implements DiscountStrategy {
    private final long thresholdCents;
    private final long flatOffCents;
    ThresholdDiscount(long thresholdCents, long flatOffCents) {
        this.thresholdCents = thresholdCents;
        this.flatOffCents = flatOffCents;
    }
    public long discountCents(long subtotalCents) {
        return subtotalCents >= thresholdCents ? flatOffCents : 0;
    }
    public String describe() {
        return ShoppingCartECommerceDemo.money(flatOffCents) + " off over "
                + ShoppingCartECommerceDemo.money(thresholdCents);
    }
}

class ShoppingCart {
    private final String owner;
    private final Map<String, CartItem> items = new LinkedHashMap<String, CartItem>();

    ShoppingCart(String owner) { this.owner = owner; }

    void addItem(Product product, int quantity) {
        if (quantity <= 0) return;
        CartItem existing = items.get(product.id);
        if (existing == null) {
            items.put(product.id, new CartItem(product, quantity));
        } else {
            existing.quantity += quantity;
        }
        System.out.println("Added " + quantity + " x " + product.name);
    }

    void updateQuantity(String productId, int quantity) {
        CartItem item = items.get(productId);
        if (item == null) {
            System.out.println("No such item: " + productId);
            return;
        }
        if (quantity <= 0) {
            removeItem(productId);
            return;
        }
        item.quantity = quantity;
        System.out.println("Set " + item.product.name + " quantity to " + quantity);
    }

    void removeItem(String productId) {
        CartItem removed = items.remove(productId);
        if (removed != null) {
            System.out.println("Removed " + removed.product.name);
        }
    }

    long subtotal() {
        long total = 0;
        for (CartItem item : items.values()) total += item.lineTotal();
        return total;
    }

    void printCart() {
        if (items.isEmpty()) {
            System.out.println("(cart is empty)");
            return;
        }
        for (CartItem item : items.values()) {
            System.out.println("  " + item.product.name + " x" + item.quantity
                    + " @ " + ShoppingCartECommerceDemo.money(item.product.unitPriceCents)
                    + " = " + ShoppingCartECommerceDemo.money(item.lineTotal()));
        }
        System.out.println("  Subtotal: " + ShoppingCartECommerceDemo.money(subtotal()));
    }

    void checkout(DiscountStrategy discount) {
        long subtotal = subtotal();
        long off = discount.discountCents(subtotal);
        long total = subtotal - off;
        System.out.println("Customer: " + owner);
        System.out.println("  Subtotal: " + ShoppingCartECommerceDemo.money(subtotal));
        System.out.println("  Discount (" + discount.describe() + "): -"
                + ShoppingCartECommerceDemo.money(off));
        System.out.println("  Total:    " + ShoppingCartECommerceDemo.money(total));
    }
}
