/*
 * Vending Machine - Low Level Design
 * ----------------------------------
 * Models a vending machine using the State design pattern.
 *
 * Key classes:
 *   - Coin / Note          : accepted denominations.
 *   - Item / Inventory     : products and stock tracking by slot code.
 *   - VendingMachineState  : State interface (Idle, HasMoney, Dispense).
 *   - VendingMachine       : context holding current state + balance.
 *
 * Patterns used: State (machine lifecycle), Enum, Encapsulation.
 */

import java.util.HashMap;
import java.util.Map;

public class VendingMachineDemo {
    public static void main(String[] args) {
        VendingMachine machine = new VendingMachine();
        machine.getInventory().addItem("A1", new Item("Coke", 25), 3);
        machine.getInventory().addItem("A2", new Item("Chips", 35), 2);
        machine.getInventory().addItem("A3", new Item("Water", 20), 0); // out of stock

        System.out.println("=== Vending Machine Demo ===");
        // Successful purchase
        machine.selectItem("A1");
        machine.insertMoney(10);
        machine.insertMoney(20);
        machine.dispense();

        System.out.println("---");
        // Out of stock
        machine.selectItem("A3");

        System.out.println("---");
        // Insufficient then refund
        machine.selectItem("A2");
        machine.insertMoney(10);
        machine.refund();
    }
}

class Item {
    private final String name;
    private final int price; // in cents
    Item(String name, int price) { this.name = name; this.price = price; }
    String getName() { return name; }
    int getPrice() { return price; }
}

class Inventory {
    private final Map<String, Item> items = new HashMap<String, Item>();
    private final Map<String, Integer> stock = new HashMap<String, Integer>();

    void addItem(String code, Item item, int qty) {
        items.put(code, item);
        stock.put(code, qty);
    }
    Item getItem(String code) { return items.get(code); }
    int getStock(String code) { return stock.getOrDefault(code, 0); }
    void reduceStock(String code) { stock.put(code, stock.get(code) - 1); }
}

interface VendingMachineState {
    void selectItem(String code);
    void insertMoney(int amount);
    void dispense();
    void refund();
}

class VendingMachine {
    private final Inventory inventory = new Inventory();
    private final VendingMachineState idle = new IdleState(this);
    private final VendingMachineState hasMoney = new HasMoneyState(this);
    private final VendingMachineState dispenseState = new DispenseState(this);

    private VendingMachineState current = idle;
    private String selectedCode;
    private int balance = 0;

    Inventory getInventory() { return inventory; }
    int getBalance() { return balance; }
    void addBalance(int amt) { balance += amt; }
    void resetBalance() { balance = 0; }
    String getSelectedCode() { return selectedCode; }
    void setSelectedCode(String c) { selectedCode = c; }

    void setState(VendingMachineState s) { current = s; }
    VendingMachineState idle() { return idle; }
    VendingMachineState hasMoney() { return hasMoney; }
    VendingMachineState dispense() { return dispenseState; }

    void selectItem(String code) { current.selectItem(code); }
    void insertMoney(int amount) { current.insertMoney(amount); }
    void dispense() { current.dispense(); }
    void refund() { current.refund(); }
}

class IdleState implements VendingMachineState {
    private final VendingMachine m;
    IdleState(VendingMachine m) { this.m = m; }
    public void selectItem(String code) {
        Item item = m.getInventory().getItem(code);
        if (item == null) { System.out.println("Invalid code: " + code); return; }
        if (m.getInventory().getStock(code) <= 0) { System.out.println(item.getName() + " is OUT OF STOCK"); return; }
        m.setSelectedCode(code);
        System.out.println("Selected " + item.getName() + " (" + item.getPrice() + "c). Insert money.");
        m.setState(m.hasMoney());
    }
    public void insertMoney(int amount) { System.out.println("Select an item first."); }
    public void dispense() { System.out.println("Select an item first."); }
    public void refund() { System.out.println("Nothing to refund."); }
}

class HasMoneyState implements VendingMachineState {
    private final VendingMachine m;
    HasMoneyState(VendingMachine m) { this.m = m; }
    public void selectItem(String code) { System.out.println("Already selecting. Insert money or refund."); }
    public void insertMoney(int amount) {
        m.addBalance(amount);
        Item item = m.getInventory().getItem(m.getSelectedCode());
        System.out.println("Inserted " + amount + "c. Balance=" + m.getBalance() + "c / Price=" + item.getPrice() + "c");
        if (m.getBalance() >= item.getPrice()) {
            System.out.println("Sufficient funds. Press dispense.");
            m.setState(m.dispense());
        }
    }
    public void dispense() { System.out.println("Insufficient balance."); }
    public void refund() {
        System.out.println("Refunded " + m.getBalance() + "c");
        m.resetBalance();
        m.setSelectedCode(null);
        m.setState(m.idle());
    }
}

class DispenseState implements VendingMachineState {
    private final VendingMachine m;
    DispenseState(VendingMachine m) { this.m = m; }
    public void selectItem(String code) { System.out.println("Dispensing in progress."); }
    public void insertMoney(int amount) { System.out.println("Dispensing in progress."); }
    public void dispense() {
        String code = m.getSelectedCode();
        Item item = m.getInventory().getItem(code);
        m.getInventory().reduceStock(code);
        int change = m.getBalance() - item.getPrice();
        System.out.println("Dispensed " + item.getName() + ". Change returned: " + change + "c");
        m.resetBalance();
        m.setSelectedCode(null);
        m.setState(m.idle());
    }
    public void refund() {
        System.out.println("Refunded " + m.getBalance() + "c");
        m.resetBalance();
        m.setSelectedCode(null);
        m.setState(m.idle());
    }
}
