/*
 * ATM - Low Level Design
 * -----------------------
 * Models an ATM using the State pattern for the transaction flow and
 * a Chain-of-Responsibility for cash dispensing across denominations.
 *
 * Key classes:
 *   - Card / Account / Bank : domain + simple in-memory bank.
 *   - ATMState              : Idle, HasCard, Authenticated states.
 *   - CashDispenser chain   : dispense using available notes greedily.
 *   - ATM                   : context.
 *
 * Patterns used: State, Chain of Responsibility, Encapsulation.
 */

import java.util.HashMap;
import java.util.Map;

public class ATMDemo {
    public static void main(String[] args) {
        Bank bank = new Bank();
        Account acc = new Account("AC1001", 1000, "1234");
        bank.addAccount(acc);

        ATM atm = new ATM(bank);
        atm.loadCash(100, 5);
        atm.loadCash(500, 5);

        System.out.println("=== ATM Demo ===");
        atm.insertCard(new Card("AC1001"));
        atm.enterPin("1234");
        atm.withdraw(700);   // 1x500 + 2x100
        atm.checkBalance();
        atm.withdraw(50);    // not multiple of 100 -> reject
        atm.ejectCard();
    }
}

class Card {
    private final String accountId;
    Card(String accountId) { this.accountId = accountId; }
    String getAccountId() { return accountId; }
}

class Account {
    private final String id;
    private double balance;
    private final String pin;
    Account(String id, double balance, String pin) { this.id = id; this.balance = balance; this.pin = pin; }
    String getId() { return id; }
    double getBalance() { return balance; }
    boolean verifyPin(String p) { return pin.equals(p); }
    void debit(double amt) { balance -= amt; }
}

class Bank {
    private final Map<String, Account> accounts = new HashMap<String, Account>();
    void addAccount(Account a) { accounts.put(a.getId(), a); }
    Account get(String id) { return accounts.get(id); }
}

abstract class CashDispenser {
    protected CashDispenser next;
    protected final int denomination;
    protected int count;
    CashDispenser(int denomination, int count) { this.denomination = denomination; this.count = count; }
    void setNext(CashDispenser n) { this.next = n; }
    void addNotes(int c) { this.count += c; }

    void dispense(int amount) {
        if (amount >= denomination && count > 0) {
            int needed = amount / denomination;
            int give = Math.min(needed, count);
            if (give > 0) {
                System.out.println("Dispensing " + give + " x " + denomination);
                count -= give;
                amount -= give * denomination;
            }
        }
        if (amount > 0 && next != null) next.dispense(amount);
        else if (amount > 0) System.out.println("Unable to dispense remaining: " + amount);
    }
    boolean canDispense(int amount) { return amount % 100 == 0; }
}

class HundredDispenser extends CashDispenser { HundredDispenser(int c) { super(100, c); } }
class FiveHundredDispenser extends CashDispenser { FiveHundredDispenser(int c) { super(500, c); } }

interface ATMState {
    void insertCard(Card card);
    void enterPin(String pin);
    void withdraw(int amount);
    void checkBalance();
    void ejectCard();
}

class ATM {
    private final Bank bank;
    private final FiveHundredDispenser fiveHundred = new FiveHundredDispenser(0);
    private final HundredDispenser hundred = new HundredDispenser(0);

    private final ATMState idle = new IdleATMState(this);
    private final ATMState hasCard = new HasCardState(this);
    private final ATMState authenticated = new AuthenticatedState(this);

    private ATMState current = idle;
    private Card currentCard;
    private Account currentAccount;

    ATM(Bank bank) {
        this.bank = bank;
        fiveHundred.setNext(hundred);
    }
    void loadCash(int denomination, int count) {
        if (denomination == 500) fiveHundred.addNotes(count);
        else if (denomination == 100) hundred.addNotes(count);
    }
    Bank getBank() { return bank; }
    CashDispenser dispenserChain() { return fiveHundred; }

    void setState(ATMState s) { current = s; }
    ATMState idle() { return idle; }
    ATMState hasCard() { return hasCard; }
    ATMState authenticated() { return authenticated; }

    Card getCurrentCard() { return currentCard; }
    void setCurrentCard(Card c) { currentCard = c; }
    Account getCurrentAccount() { return currentAccount; }
    void setCurrentAccount(Account a) { currentAccount = a; }

    void insertCard(Card card) { current.insertCard(card); }
    void enterPin(String pin) { current.enterPin(pin); }
    void withdraw(int amount) { current.withdraw(amount); }
    void checkBalance() { current.checkBalance(); }
    void ejectCard() { current.ejectCard(); }
}

class IdleATMState implements ATMState {
    private final ATM atm;
    IdleATMState(ATM atm) { this.atm = atm; }
    public void insertCard(Card card) {
        Account acc = atm.getBank().get(card.getAccountId());
        if (acc == null) { System.out.println("Invalid card."); return; }
        atm.setCurrentCard(card);
        atm.setCurrentAccount(acc);
        System.out.println("Card accepted. Enter PIN.");
        atm.setState(atm.hasCard());
    }
    public void enterPin(String pin) { System.out.println("Insert card first."); }
    public void withdraw(int amount) { System.out.println("Insert card first."); }
    public void checkBalance() { System.out.println("Insert card first."); }
    public void ejectCard() { System.out.println("No card to eject."); }
}

class HasCardState implements ATMState {
    private final ATM atm;
    HasCardState(ATM atm) { this.atm = atm; }
    public void insertCard(Card card) { System.out.println("Card already inserted."); }
    public void enterPin(String pin) {
        if (atm.getCurrentAccount().verifyPin(pin)) {
            System.out.println("PIN OK.");
            atm.setState(atm.authenticated());
        } else {
            System.out.println("Wrong PIN. Ejecting card.");
            reset();
        }
    }
    public void withdraw(int amount) { System.out.println("Authenticate first."); }
    public void checkBalance() { System.out.println("Authenticate first."); }
    public void ejectCard() { System.out.println("Card ejected."); reset(); }
    private void reset() { atm.setCurrentCard(null); atm.setCurrentAccount(null); atm.setState(atm.idle()); }
}

class AuthenticatedState implements ATMState {
    private final ATM atm;
    AuthenticatedState(ATM atm) { this.atm = atm; }
    public void insertCard(Card card) { System.out.println("Transaction in progress."); }
    public void enterPin(String pin) { System.out.println("Already authenticated."); }
    public void withdraw(int amount) {
        Account acc = atm.getCurrentAccount();
        if (amount % 100 != 0) { System.out.println("Amount must be multiple of 100."); return; }
        if (amount > acc.getBalance()) { System.out.println("Insufficient balance."); return; }
        acc.debit(amount);
        atm.dispenserChain().dispense(amount);
        System.out.println("Withdrawal complete. Remaining balance: " + acc.getBalance());
    }
    public void checkBalance() { System.out.println("Balance: " + atm.getCurrentAccount().getBalance()); }
    public void ejectCard() {
        System.out.println("Card ejected. Thank you.");
        atm.setCurrentCard(null); atm.setCurrentAccount(null); atm.setState(atm.idle());
    }
}
