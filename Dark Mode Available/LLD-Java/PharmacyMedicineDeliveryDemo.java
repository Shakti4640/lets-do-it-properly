/*
 * Pharmacy Medicine Delivery - Low Level Design
 * --------------------------------------------------------------
 * An online pharmacy where some medicines require a valid prescription.
 * Orders enforce an Rx check: any prescription-only item must be backed by
 * a valid prescription for the customer before checkout succeeds. Stock is
 * decremented only on a fully validated order.
 *
 * Key classes / abstractions:
 *   - Medicine            : drug with price, stock, and Rx-required flag.
 *   - Prescription        : a doctor's Rx authorizing specific medicines.
 *   - Cart                : selected medicines + quantities.
 *   - RxValidator         : enforces prescription rules.
 *   - PharmacyService     : facade for ordering with Rx enforcement.
 *
 * Patterns used:
 *   - Chain-of-responsibility-style validation (RxValidator)
 *   - Facade (PharmacyService)
 */

import java.util.*;

public class PharmacyMedicineDeliveryDemo {
    public static void main(String[] args) {
        PharmacyService pharmacy = new PharmacyService();
        pharmacy.addMedicine(new Medicine("Paracetamol", 30.0, 100, false));
        pharmacy.addMedicine(new Medicine("Amoxicillin", 120.0, 40, true));
        pharmacy.addMedicine(new Medicine("Insulin", 450.0, 15, true));

        // Customer "Asha" has an Rx covering Amoxicillin only.
        Prescription rx = new Prescription("RX-1", "Asha", Arrays.asList("Amoxicillin"));
        pharmacy.registerPrescription(rx);

        System.out.println("=== Catalog ===");
        pharmacy.printCatalog();

        System.out.println("\n=== Order 1: OTC + Rx item that IS covered ===");
        Cart c1 = new Cart("Asha").add("Paracetamol", 2).add("Amoxicillin", 1);
        pharmacy.placeOrder(c1);

        System.out.println("\n=== Order 2: Rx item NOT covered (Insulin) ===");
        Cart c2 = new Cart("Asha").add("Insulin", 1);
        pharmacy.placeOrder(c2);

        System.out.println("\n=== Order 3: OTC only, no Rx needed ===");
        Cart c3 = new Cart("Bob").add("Paracetamol", 5);
        pharmacy.placeOrder(c3);

        System.out.println("\n=== Order 4: Rx item but customer has no prescription ===");
        Cart c4 = new Cart("Bob").add("Amoxicillin", 1);
        pharmacy.placeOrder(c4);

        System.out.println("\n=== Catalog after orders ===");
        pharmacy.printCatalog();
    }
}

class Medicine {
    private final String name;
    private final double price;
    private int stock;
    private final boolean rxRequired;

    public Medicine(String name, double price, int stock, boolean rxRequired) {
        this.name = name;
        this.price = price;
        this.stock = stock;
        this.rxRequired = rxRequired;
    }

    public String getName() { return name; }
    public double getPrice() { return price; }
    public int getStock() { return stock; }
    public boolean isRxRequired() { return rxRequired; }

    public boolean deduct(int qty) {
        if (stock < qty) return false;
        stock -= qty;
        return true;
    }
}

class Prescription {
    private final String id;
    private final String patient;
    private final Set<String> medicines;

    public Prescription(String id, String patient, List<String> medicines) {
        this.id = id;
        this.patient = patient;
        this.medicines = new HashSet<>(medicines);
    }

    public String getId() { return id; }
    public String getPatient() { return patient; }
    public boolean authorizes(String medicine) { return medicines.contains(medicine); }
}

class CartLine {
    final String medicine;
    final int qty;
    CartLine(String medicine, int qty) { this.medicine = medicine; this.qty = qty; }
}

class Cart {
    private final String customer;
    private final List<CartLine> lines = new ArrayList<>();

    public Cart(String customer) { this.customer = customer; }

    public Cart add(String medicine, int qty) {
        lines.add(new CartLine(medicine, qty));
        return this;
    }

    public String getCustomer() { return customer; }
    public List<CartLine> getLines() { return lines; }
}

class RxValidator {
    /** Returns null if valid, otherwise a human-readable reason for rejection. */
    public String validate(Cart cart, Map<String, Medicine> catalog, List<Prescription> prescriptions) {
        for (CartLine line : cart.getLines()) {
            Medicine med = catalog.get(line.medicine);
            if (med == null) return "unknown medicine " + line.medicine;
            if (med.isRxRequired() && !hasValidRx(cart.getCustomer(), line.medicine, prescriptions)) {
                return "no valid prescription for " + line.medicine;
            }
        }
        return null;
    }

    private boolean hasValidRx(String customer, String medicine, List<Prescription> prescriptions) {
        for (Prescription rx : prescriptions) {
            if (rx.getPatient().equals(customer) && rx.authorizes(medicine)) return true;
        }
        return false;
    }
}

class PharmacyService {
    private final Map<String, Medicine> catalog = new LinkedHashMap<>();
    private final List<Prescription> prescriptions = new ArrayList<>();
    private final RxValidator validator = new RxValidator();
    private int orderCounter = 0;

    public void addMedicine(Medicine m) { catalog.put(m.getName(), m); }
    public void registerPrescription(Prescription rx) {
        prescriptions.add(rx);
        System.out.println("Registered " + rx.getId() + " for " + rx.getPatient());
    }

    public void placeOrder(Cart cart) {
        String error = validator.validate(cart, catalog, prescriptions);
        if (error != null) {
            System.out.println("  REJECTED order for " + cart.getCustomer() + ": " + error);
            return;
        }
        // verify stock before committing
        for (CartLine line : cart.getLines()) {
            if (catalog.get(line.medicine).getStock() < line.qty) {
                System.out.println("  REJECTED: insufficient stock for " + line.medicine);
                return;
            }
        }
        double total = 0;
        for (CartLine line : cart.getLines()) {
            Medicine med = catalog.get(line.medicine);
            med.deduct(line.qty);
            total += med.getPrice() * line.qty;
        }
        System.out.println("  ACCEPTED order RXO-" + (++orderCounter)
                + " for " + cart.getCustomer() + " total=Rs" + total);
    }

    public void printCatalog() {
        for (Medicine m : catalog.values()) {
            System.out.println("  " + m.getName() + " - Rs" + m.getPrice()
                    + " stock=" + m.getStock() + (m.isRxRequired() ? " [Rx]" : " [OTC]"));
        }
    }
}
