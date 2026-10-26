/*
 * Payment System (LLD) - Low Level Design
 * ---------------------------------------
 * Processes payments through multiple gateways using Strategy, with a
 * State-driven transaction lifecycle and a factory for gateways.
 *
 * Key classes:
 *   - PaymentMethod / PaymentGateway (+ Card/UPI/Wallet)  : Strategy
 *   - GatewayFactory                                      : Factory
 *   - Transaction (status lifecycle)
 *   - PaymentService (facade)
 *
 * Patterns used: Strategy, Factory, State-ish status, Facade.
 */

import java.util.HashMap;
import java.util.Map;

public class PaymentSystemDemo {
    public static void main(String[] args) {
        PaymentService service = new PaymentService();
        System.out.println("=== Payment System Demo ===");

        Transaction t1 = service.pay("Alice", 1500, PaymentMethod.CARD);
        Transaction t2 = service.pay("Bob", 50, PaymentMethod.UPI);
        Transaction t3 = service.pay("Carol", 99999, PaymentMethod.WALLET); // exceeds wallet limit -> fail

        service.refund(t1);
        System.out.println("t1 status: " + t1.getStatus());
        System.out.println("t3 status: " + t3.getStatus());
    }
}

enum PaymentMethod { CARD, UPI, WALLET }
enum TxnStatus { CREATED, SUCCESS, FAILED, REFUNDED }

interface PaymentGateway {
    boolean process(double amount);
    String name();
}

class CardGateway implements PaymentGateway {
    public boolean process(double amount) { System.out.println("  Charging card Rs " + amount); return true; }
    public String name() { return "CARD"; }
}
class UpiGateway implements PaymentGateway {
    public boolean process(double amount) { System.out.println("  UPI collect Rs " + amount); return true; }
    public String name() { return "UPI"; }
}
class WalletGateway implements PaymentGateway {
    private final double limit = 5000;
    public boolean process(double amount) {
        if (amount > limit) { System.out.println("  Wallet limit exceeded for Rs " + amount); return false; }
        System.out.println("  Wallet debit Rs " + amount); return true;
    }
    public String name() { return "WALLET"; }
}

class GatewayFactory {
    static PaymentGateway create(PaymentMethod method) {
        switch (method) {
            case CARD:   return new CardGateway();
            case UPI:    return new UpiGateway();
            case WALLET: return new WalletGateway();
            default:     throw new IllegalArgumentException("Unknown method");
        }
    }
}

class Transaction {
    private final String id; private final String payer; private final double amount; private final PaymentMethod method;
    private TxnStatus status = TxnStatus.CREATED;
    Transaction(String id, String payer, double amount, PaymentMethod method) {
        this.id = id; this.payer = payer; this.amount = amount; this.method = method;
    }
    String getId() { return id; }
    double getAmount() { return amount; }
    TxnStatus getStatus() { return status; }
    void setStatus(TxnStatus s) { status = s; }
}

class PaymentService {
    private final Map<String, Transaction> transactions = new HashMap<String, Transaction>();
    private int seq = 0;

    Transaction pay(String payer, double amount, PaymentMethod method) {
        Transaction txn = new Transaction("TXN" + (++seq), payer, amount, method);
        transactions.put(txn.getId(), txn);
        System.out.println(payer + " paying Rs " + amount + " via " + method);
        PaymentGateway gateway = GatewayFactory.create(method);
        boolean ok = gateway.process(amount);
        txn.setStatus(ok ? TxnStatus.SUCCESS : TxnStatus.FAILED);
        System.out.println("  -> " + txn.getId() + " " + txn.getStatus());
        return txn;
    }

    void refund(Transaction txn) {
        if (txn.getStatus() != TxnStatus.SUCCESS) { System.out.println("Cannot refund " + txn.getId()); return; }
        txn.setStatus(TxnStatus.REFUNDED);
        System.out.println("Refunded " + txn.getId() + " Rs " + txn.getAmount());
    }
}
