/*
 * Digital Wallet Payment App - Low Level Design
 * ---------------------------------------------
 * Wallets hold a balance in cents. Users can top-up, and transfer money to a
 * peer. Transfers are atomic: the debit and credit either both succeed or the
 * whole operation is rejected (e.g. insufficient funds). Every successful
 * movement is recorded in a transaction ledger.
 *
 * Key classes / abstractions:
 *   - Wallet              : owner + balance
 *   - Transaction         : immutable ledger entry (type, amounts, parties)
 *   - WalletService       : top-up, transfer, history, balance queries
 *
 * Patterns used: Facade (WalletService), immutable value objects (Transaction).
 */

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DigitalWalletPaymentAppDemo {
    public static void main(String[] args) {
        WalletService bank = new WalletService();
        bank.openWallet("alice");
        bank.openWallet("bob");
        bank.openWallet("carol");

        System.out.println("=== Digital Wallet Payment App Demo ===");

        bank.topUp("alice", 10000); // $100
        bank.topUp("bob", 2500);    // $25

        System.out.println("\n--- Transfers ---");
        bank.transfer("alice", "bob", 3000);   // ok
        bank.transfer("bob", "carol", 5000);   // ok ($25 + $30 = $55 available)
        bank.transfer("carol", "alice", 99999); // insufficient funds

        System.out.println("\n--- Balances ---");
        bank.printBalance("alice");
        bank.printBalance("bob");
        bank.printBalance("carol");

        System.out.println("\n--- Ledger ---");
        bank.printLedger();

        System.out.println("\n--- Bob's history ---");
        bank.printHistory("bob");
    }

    static String money(long cents) { return String.format("$%,.2f", cents / 100.0); }
}

enum TxType { TOPUP, TRANSFER }

class Transaction {
    final long id;
    final TxType type;
    final String from;   // null for top-up
    final String to;
    final long amountCents;

    Transaction(long id, TxType type, String from, String to, long amountCents) {
        this.id = id;
        this.type = type;
        this.from = from;
        this.to = to;
        this.amountCents = amountCents;
    }

    public String toString() {
        if (type == TxType.TOPUP) {
            return "#" + id + " TOPUP    " + to + " +" + DigitalWalletPaymentAppDemo.money(amountCents);
        }
        return "#" + id + " TRANSFER " + from + " -> " + to + " "
                + DigitalWalletPaymentAppDemo.money(amountCents);
    }
}

class Wallet {
    final String owner;
    private long balanceCents = 0;
    Wallet(String owner) { this.owner = owner; }

    long getBalance() { return balanceCents; }
    void credit(long amount) { balanceCents += amount; }

    boolean debit(long amount) {
        if (amount > balanceCents) return false;
        balanceCents -= amount;
        return true;
    }
}

class WalletService {
    private final Map<String, Wallet> wallets = new LinkedHashMap<String, Wallet>();
    private final List<Transaction> ledger = new ArrayList<Transaction>();
    private long nextTxId = 1L;

    void openWallet(String owner) {
        wallets.put(owner, new Wallet(owner));
        System.out.println("Opened wallet for " + owner);
    }

    private Wallet require(String owner) {
        Wallet w = wallets.get(owner);
        if (w == null) throw new IllegalArgumentException("No such wallet: " + owner);
        return w;
    }

    void topUp(String owner, long amountCents) {
        if (amountCents <= 0) {
            System.out.println("Top-up amount must be positive");
            return;
        }
        Wallet w = require(owner);
        w.credit(amountCents);
        ledger.add(new Transaction(nextTxId++, TxType.TOPUP, null, owner, amountCents));
        System.out.println(owner + " topped up " + DigitalWalletPaymentAppDemo.money(amountCents)
                + " (balance " + DigitalWalletPaymentAppDemo.money(w.getBalance()) + ")");
    }

    void transfer(String from, String to, long amountCents) {
        if (from.equals(to)) {
            System.out.println("REJECTED: cannot transfer to self");
            return;
        }
        if (amountCents <= 0) {
            System.out.println("REJECTED: amount must be positive");
            return;
        }
        Wallet src = require(from);
        Wallet dst = require(to);

        // Atomic: only credit if the debit succeeds.
        if (!src.debit(amountCents)) {
            System.out.println("REJECTED: " + from + " has insufficient funds for "
                    + DigitalWalletPaymentAppDemo.money(amountCents)
                    + " (balance " + DigitalWalletPaymentAppDemo.money(src.getBalance()) + ")");
            return;
        }
        dst.credit(amountCents);
        ledger.add(new Transaction(nextTxId++, TxType.TRANSFER, from, to, amountCents));
        System.out.println(from + " sent " + DigitalWalletPaymentAppDemo.money(amountCents)
                + " to " + to);
    }

    void printBalance(String owner) {
        Wallet w = require(owner);
        System.out.println(owner + ": " + DigitalWalletPaymentAppDemo.money(w.getBalance()));
    }

    void printLedger() {
        if (ledger.isEmpty()) {
            System.out.println("(no transactions)");
            return;
        }
        for (Transaction t : ledger) System.out.println(t);
    }

    void printHistory(String owner) {
        for (Transaction t : ledger) {
            if (owner.equals(t.from) || owner.equals(t.to)) {
                System.out.println(t);
            }
        }
    }
}
