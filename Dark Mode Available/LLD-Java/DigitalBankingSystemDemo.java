/*
 * Digital Banking System (LLD)
 * ----------------------------
 * Savings and checking accounts supporting deposit, withdraw and transfer with
 * overdraft rules, a pluggable interest-accrual strategy, and a printed statement.
 *
 * Key classes / abstractions:
 *   - Account (abstract)     : balance + transaction ledger; defines withdraw policy
 *   - SavingsAccount         : no overdraft; earns interest
 *   - CheckingAccount        : allows overdraft up to a limit; no interest
 *   - InterestStrategy       : Strategy for interest accrual (simple vs none)
 *   - Bank                   : facade for accounts, transfers, statements, accrual
 *   - Transaction            : immutable ledger entry
 *
 * Patterns used: Strategy (interest), Template Method (withdraw policy),
 *                Factory (account creation), Facade (Bank).
 */
import java.util.*;

public class DigitalBankingSystemDemo {
    public static void main(String[] args) {
        Bank bank = new Bank();
        Account sav = bank.openAccount(AccountType.SAVINGS, "alice", 1000);
        Account chk = bank.openAccount(AccountType.CHECKING, "bob", 200);

        System.out.println("=== Deposits / Withdrawals ===");
        bank.deposit(sav.id, 500);
        bank.withdraw(sav.id, 300);
        bank.withdraw(chk.id, 350); // uses overdraft (limit 500)
        System.out.println(sav.id + " balance: " + sav.balance());
        System.out.println(chk.id + " balance: " + chk.balance() + " (overdraft allowed)");

        System.out.println("\n=== Withdrawal rules ===");
        try { bank.withdraw(sav.id, 5000); }
        catch (RuntimeException e) { System.out.println("savings rejected: " + e.getMessage()); }
        try { bank.withdraw(chk.id, 1000); }
        catch (RuntimeException e) { System.out.println("checking rejected: " + e.getMessage()); }

        System.out.println("\n=== Transfer ===");
        bank.transfer(sav.id, chk.id, 200);
        System.out.println(sav.id + " -> " + sav.balance() + " | " + chk.id + " -> " + chk.balance());

        System.out.println("\n=== Interest accrual (monthly) ===");
        bank.accrueInterest();
        System.out.println(sav.id + " after interest: " + sav.balance());
        System.out.println(chk.id + " after interest: " + chk.balance());

        System.out.println("\n=== Statement: " + sav.id + " ===");
        bank.printStatement(sav.id);
    }
}

enum AccountType { SAVINGS, CHECKING }

class Transaction {
    final String type;
    final long amount;
    final long balanceAfter;
    Transaction(String type, long amount, long balanceAfter) {
        this.type = type; this.amount = amount; this.balanceAfter = balanceAfter;
    }
    public String toString() {
        return String.format("%-10s %+8d  -> balance %d", type, amount, balanceAfter);
    }
}

interface InterestStrategy {
    long computeInterest(long balance);
    String name();
}

class SimpleInterestStrategy implements InterestStrategy {
    private final int annualBps; // basis points per year
    SimpleInterestStrategy(int annualBps) { this.annualBps = annualBps; }
    public long computeInterest(long balance) {
        if (balance <= 0) return 0;
        return balance * annualBps / 10_000 / 12; // monthly accrual
    }
    public String name() { return annualBps + "bps/yr"; }
}

class NoInterestStrategy implements InterestStrategy {
    public long computeInterest(long balance) { return 0; }
    public String name() { return "none"; }
}

abstract class Account {
    final String id;
    final String owner;
    private long balance;
    final List<Transaction> ledger = new ArrayList<>();
    final InterestStrategy interest;

    Account(String id, String owner, long opening, InterestStrategy interest) {
        this.id = id; this.owner = owner; this.balance = opening; this.interest = interest;
        ledger.add(new Transaction("OPEN", opening, balance));
    }

    long balance() { return balance; }

    void deposit(long amount) {
        if (amount <= 0) throw new RuntimeException("deposit must be positive");
        balance += amount;
        ledger.add(new Transaction("DEPOSIT", amount, balance));
    }

    void withdraw(long amount) {
        if (amount <= 0) throw new RuntimeException("withdraw must be positive");
        if (!canWithdraw(amount))
            throw new RuntimeException("insufficient funds for " + amount + " (balance " + balance + ")");
        balance -= amount;
        ledger.add(new Transaction("WITHDRAW", -amount, balance));
    }

    void applyInterest() {
        long i = interest.computeInterest(balance);
        if (i > 0) { balance += i; ledger.add(new Transaction("INTEREST", i, balance)); }
    }

    abstract boolean canWithdraw(long amount); // Template Method hook
    abstract String typeName();
}

class SavingsAccount extends Account {
    SavingsAccount(String id, String owner, long opening) {
        super(id, owner, opening, new SimpleInterestStrategy(600)); // 6%/yr
    }
    boolean canWithdraw(long amount) { return balance() - amount >= 0; }
    String typeName() { return "SAVINGS"; }
}

class CheckingAccount extends Account {
    private final long overdraftLimit;
    CheckingAccount(String id, String owner, long opening, long overdraftLimit) {
        super(id, owner, opening, new NoInterestStrategy());
        this.overdraftLimit = overdraftLimit;
    }
    boolean canWithdraw(long amount) { return balance() - amount >= -overdraftLimit; }
    String typeName() { return "CHECKING(od=" + overdraftLimit + ")"; }
}

class Bank {
    private final Map<String, Account> accounts = new LinkedHashMap<>();
    private int seq = 0;

    Account openAccount(AccountType type, String owner, long opening) {
        String id = "AC" + (1000 + (++seq));
        Account a = (type == AccountType.SAVINGS)
                ? new SavingsAccount(id, owner, opening)
                : new CheckingAccount(id, owner, opening, 500);
        accounts.put(id, a);
        System.out.println("opened " + id + " [" + a.typeName() + "] for " + owner + " opening=" + opening);
        return a;
    }

    private Account require(String id) {
        Account a = accounts.get(id);
        if (a == null) throw new RuntimeException("no such account: " + id);
        return a;
    }

    void deposit(String id, long amount) { require(id).deposit(amount); }
    void withdraw(String id, long amount) { require(id).withdraw(amount); }

    void transfer(String from, String to, long amount) {
        Account src = require(from), dst = require(to);
        src.withdraw(amount);
        dst.deposit(amount);
        System.out.println("transferred " + amount + " from " + from + " to " + to);
    }

    void accrueInterest() {
        for (Account a : accounts.values()) a.applyInterest();
    }

    void printStatement(String id) {
        Account a = require(id);
        System.out.println("Account " + a.id + " (" + a.typeName() + ") owner=" + a.owner
                + " interest=" + a.interest.name());
        for (Transaction t : a.ledger) System.out.println("  " + t);
    }
}
