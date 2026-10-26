/*
 * Splitwise - Low Level Design
 * ----------------------------
 * Tracks shared expenses among users and computes net balances. Supports
 * EQUAL, EXACT and PERCENT split strategies.
 *
 * Key classes:
 *   - User
 *   - Split (abstract) + EqualSplit / ExactSplit / PercentSplit
 *   - Expense
 *   - ExpenseManager (balance sheet + simplify)
 *
 * Patterns used: Strategy (split types), Facade (ExpenseManager).
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SplitwiseDemo {
    public static void main(String[] args) {
        ExpenseManager mgr = new ExpenseManager();
        User u1 = mgr.addUser("u1", "Alice");
        User u2 = mgr.addUser("u2", "Bob");
        User u3 = mgr.addUser("u3", "Carol");

        System.out.println("=== Splitwise Demo ===");
        // Alice pays 900 split equally among 3
        mgr.addExpense("u1", 900, SplitType.EQUAL, new String[]{"u1","u2","u3"}, null);
        // Bob pays 300, exact: Alice 100, Carol 200
        mgr.addExpense("u2", 300, SplitType.EXACT, new String[]{"u1","u3"}, new double[]{100, 200});
        // Carol pays 400, percent: Alice 50%, Bob 50%
        mgr.addExpense("u3", 400, SplitType.PERCENT, new String[]{"u1","u2"}, new double[]{50, 50});

        mgr.showBalances();
    }
}

enum SplitType { EQUAL, EXACT, PERCENT }

class User {
    private final String id;
    private final String name;
    User(String id, String name) { this.id = id; this.name = name; }
    String getId() { return id; }
    String getName() { return name; }
}

class Expense {
    final String paidBy;
    final double amount;
    final Map<String, Double> shares; // userId -> owed amount
    Expense(String paidBy, double amount, Map<String, Double> shares) {
        this.paidBy = paidBy; this.amount = amount; this.shares = shares;
    }
}

class ExpenseManager {
    private final Map<String, User> users = new HashMap<String, User>();
    private final List<Expense> expenses = new ArrayList<Expense>();
    // balances[a][b] = how much a owes b
    private final Map<String, Map<String, Double>> balances = new HashMap<String, Map<String, Double>>();

    User addUser(String id, String name) {
        User u = new User(id, name);
        users.put(id, u);
        balances.put(id, new HashMap<String, Double>());
        return u;
    }

    void addExpense(String paidBy, double amount, SplitType type, String[] participants, double[] values) {
        Map<String, Double> shares = new HashMap<String, Double>();
        switch (type) {
            case EQUAL:
                double each = amount / participants.length;
                for (String p : participants) shares.put(p, each);
                break;
            case EXACT:
                for (int i = 0; i < participants.length; i++) shares.put(participants[i], values[i]);
                break;
            case PERCENT:
                for (int i = 0; i < participants.length; i++) shares.put(participants[i], amount * values[i] / 100.0);
                break;
        }
        expenses.add(new Expense(paidBy, amount, shares));
        for (Map.Entry<String, Double> e : shares.entrySet()) {
            String debtor = e.getKey();
            if (debtor.equals(paidBy)) continue;
            adjust(debtor, paidBy, e.getValue());
        }
        System.out.println(users.get(paidBy).getName() + " paid " + amount + " (" + type + ")");
    }

    private void adjust(String debtor, String creditor, double amount) {
        // net against any reverse debt
        double reverse = balances.get(creditor).getOrDefault(debtor, 0.0);
        if (reverse >= amount) {
            balances.get(creditor).put(debtor, reverse - amount);
        } else {
            balances.get(creditor).put(debtor, 0.0);
            double remaining = amount - reverse;
            balances.get(debtor).merge(creditor, remaining, Double::sum);
        }
    }

    void showBalances() {
        System.out.println("--- Net Balances ---");
        for (String a : users.keySet()) {
            for (Map.Entry<String, Double> e : balances.get(a).entrySet()) {
                if (e.getValue() > 0.001) {
                    System.out.println(users.get(a).getName() + " owes " + users.get(e.getKey()).getName()
                            + ": " + String.format("%.2f", e.getValue()));
                }
            }
        }
    }
}
