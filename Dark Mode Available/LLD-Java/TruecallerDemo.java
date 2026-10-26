/*
 * Truecaller - Low Level Design
 * -----------------------------
 * Caller identification + spam detection. Maintains a global phone->name
 * directory built from user contacts, supports reverse lookup, and flags
 * spam based on crowd-sourced reports.
 *
 * Key classes:
 *   - Contact / User
 *   - PhoneRecord (name votes + spam reports)
 *   - TruecallerService (facade: register contacts, lookup, reportSpam)
 *
 * Patterns used: Facade, crowd-sourced aggregation.
 */

import java.util.HashMap;
import java.util.Map;

public class TruecallerDemo {
    public static void main(String[] args) {
        TruecallerService service = new TruecallerService();
        System.out.println("=== Truecaller Demo ===");

        // Users save contacts -> builds global directory
        service.saveContact("alice", "9990001111", "Bob Kumar");
        service.saveContact("carol", "9990001111", "Bob K");
        service.saveContact("dave", "9990001111", "Bob Kumar");
        service.saveContact("alice", "8001234567", "Pizza Hut");

        // Lookup unknown number
        System.out.println("Lookup 9990001111: " + service.lookup("9990001111"));
        System.out.println("Lookup 7000000000: " + service.lookup("7000000000"));

        // Spam reporting
        service.reportSpam("alice", "8001234567");
        service.reportSpam("bob", "8001234567");
        service.reportSpam("carol", "8001234567");
        System.out.println("Is 8001234567 spam? " + service.isSpam("8001234567"));
        System.out.println("Lookup 8001234567: " + service.lookup("8001234567"));
    }
}

class PhoneRecord {
    final String number;
    final Map<String, Integer> nameVotes = new HashMap<String, Integer>();
    int spamReports = 0;
    PhoneRecord(String number) { this.number = number; }

    void addName(String name) { nameVotes.merge(name, 1, Integer::sum); }

    String bestName() {
        String best = null; int max = -1;
        for (Map.Entry<String, Integer> e : nameVotes.entrySet()) {
            if (e.getValue() > max) { max = e.getValue(); best = e.getKey(); }
        }
        return best;
    }
}

class TruecallerService {
    private final Map<String, PhoneRecord> directory = new HashMap<String, PhoneRecord>();
    private final int spamThreshold = 3;

    void saveContact(String ownerUserId, String number, String name) {
        PhoneRecord rec = directory.computeIfAbsent(number, PhoneRecord::new);
        rec.addName(name);
        System.out.println(ownerUserId + " saved " + number + " as '" + name + "'");
    }

    String lookup(String number) {
        PhoneRecord rec = directory.get(number);
        if (rec == null) return "Unknown";
        String tag = isSpam(number) ? " [SPAM]" : "";
        return rec.bestName() + tag;
    }

    void reportSpam(String reporterId, String number) {
        PhoneRecord rec = directory.computeIfAbsent(number, PhoneRecord::new);
        rec.spamReports++;
        System.out.println(reporterId + " reported " + number + " as spam (" + rec.spamReports + ")");
    }

    boolean isSpam(String number) {
        PhoneRecord rec = directory.get(number);
        return rec != null && rec.spamReports >= spamThreshold;
    }
}
