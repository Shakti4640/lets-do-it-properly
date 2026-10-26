/*
 * Insurance Claim Processing System (LLD)
 * ---------------------------------------
 * Policies and claims driven by a state machine:
 *   SUBMITTED -> UNDER_REVIEW -> APPROVED -> PAID
 *                            \-> REJECTED
 * Validation rules gate the transitions; approved claims produce a payout.
 *
 * Key classes / abstractions:
 *   - Policy             : coverage limit, deductible, active flag
 *   - Claim              : amount + current ClaimState + audit history
 *   - ClaimState (enum)  : explicit state machine with allowed transitions
 *   - ValidationRule     : Strategy for claim validation (chain of rules)
 *   - ClaimProcessor     : Facade orchestrating review/approve/reject/pay
 *
 * Patterns used: State (claim lifecycle), Strategy + Chain (validation rules),
 *                Facade (ClaimProcessor).
 */
import java.util.*;

public class InsuranceClaimProcessingSystemDemo {
    public static void main(String[] args) {
        ClaimProcessor processor = new ClaimProcessor();
        Policy p = processor.addPolicy("POL-1", "alice", 10_000, 500, true);
        Policy expired = processor.addPolicy("POL-2", "bob", 5_000, 200, false);

        System.out.println("=== Valid claim flow ===");
        Claim c1 = processor.fileClaim("POL-1", 4_000, "fender bender");
        processor.review(c1.id);
        processor.approve(c1.id);
        processor.pay(c1.id);
        processor.printClaim(c1.id);

        System.out.println("\n=== Claim rejected by rules (over coverage) ===");
        Claim c2 = processor.fileClaim("POL-1", 50_000, "total loss exaggerated");
        processor.review(c2.id);
        processor.printClaim(c2.id);

        System.out.println("\n=== Claim on inactive policy ===");
        try {
            processor.fileClaim("POL-2", 1_000, "hail damage");
        } catch (RuntimeException e) {
            System.out.println("rejected at filing: " + e.getMessage());
        }

        System.out.println("\n=== Illegal transition guard ===");
        Claim c3 = processor.fileClaim("POL-1", 800, "windshield");
        try { processor.pay(c3.id); } // cannot pay before approve
        catch (RuntimeException e) { System.out.println("guard: " + e.getMessage()); }

        System.out.println("\n=== Summary ===");
        System.out.println("Total paid out: " + processor.totalPaid());
    }
}

enum ClaimState {
    SUBMITTED, UNDER_REVIEW, APPROVED, REJECTED, PAID;

    boolean canTransitionTo(ClaimState next) {
        switch (this) {
            case SUBMITTED:    return next == UNDER_REVIEW;
            case UNDER_REVIEW: return next == APPROVED || next == REJECTED;
            case APPROVED:     return next == PAID;
            default:           return false; // REJECTED, PAID are terminal
        }
    }
}

class Policy {
    final String id;
    final String holder;
    final long coverageLimit;
    final long deductible;
    final boolean active;
    Policy(String id, String holder, long coverageLimit, long deductible, boolean active) {
        this.id = id; this.holder = holder; this.coverageLimit = coverageLimit;
        this.deductible = deductible; this.active = active;
    }
}

class Claim {
    final String id;
    final Policy policy;
    final long amount;
    final String description;
    ClaimState state = ClaimState.SUBMITTED;
    long payout = 0;
    final List<String> history = new ArrayList<>();
    Claim(String id, Policy policy, long amount, String description) {
        this.id = id; this.policy = policy; this.amount = amount; this.description = description;
        history.add("SUBMITTED amount=" + amount);
    }
    void moveTo(ClaimState next, String note) {
        if (!state.canTransitionTo(next))
            throw new RuntimeException("illegal transition " + state + " -> " + next);
        state = next;
        history.add(next + (note.isEmpty() ? "" : " (" + note + ")"));
    }
}

interface ValidationRule {
    String validate(Claim c); // returns null if OK, else failure reason
}

class ActivePolicyRule implements ValidationRule {
    public String validate(Claim c) {
        return c.policy.active ? null : "policy " + c.policy.id + " is inactive";
    }
}

class CoverageLimitRule implements ValidationRule {
    public String validate(Claim c) {
        return c.amount <= c.policy.coverageLimit ? null
                : "amount " + c.amount + " exceeds coverage " + c.policy.coverageLimit;
    }
}

class PositiveAmountRule implements ValidationRule {
    public String validate(Claim c) {
        return c.amount > c.policy.deductible ? null
                : "amount " + c.amount + " below deductible " + c.policy.deductible;
    }
}

class ClaimProcessor {
    private final Map<String, Policy> policies = new HashMap<>();
    private final Map<String, Claim> claims = new LinkedHashMap<>();
    private final List<ValidationRule> filingRules = Arrays.asList(new ActivePolicyRule());
    private final List<ValidationRule> reviewRules =
            Arrays.asList(new CoverageLimitRule(), new PositiveAmountRule());
    private int seq = 0;
    private long totalPaid = 0;

    Policy addPolicy(String id, String holder, long limit, long deductible, boolean active) {
        Policy p = new Policy(id, holder, limit, deductible, active);
        policies.put(id, p);
        return p;
    }

    Claim fileClaim(String policyId, long amount, String desc) {
        Policy p = policies.get(policyId);
        if (p == null) throw new RuntimeException("no such policy: " + policyId);
        Claim c = new Claim("CLM-" + (++seq), p, amount, desc);
        for (ValidationRule r : filingRules) {
            String err = r.validate(c);
            if (err != null) throw new RuntimeException(err);
        }
        claims.put(c.id, c);
        System.out.println("filed " + c.id + " amount=" + amount + " (" + desc + ")");
        return c;
    }

    void review(String claimId) {
        Claim c = require(claimId);
        c.moveTo(ClaimState.UNDER_REVIEW, "");
        for (ValidationRule r : reviewRules) {
            String err = r.validate(c);
            if (err != null) { c.moveTo(ClaimState.REJECTED, err); System.out.println("  rejected: " + err); return; }
        }
        System.out.println("  " + c.id + " passed review");
    }

    void approve(String claimId) {
        Claim c = require(claimId);
        long payout = Math.max(0, c.amount - c.policy.deductible);
        c.payout = payout;
        c.moveTo(ClaimState.APPROVED, "payout=" + payout);
        System.out.println("  approved " + c.id + " payout=" + payout);
    }

    void pay(String claimId) {
        Claim c = require(claimId);
        c.moveTo(ClaimState.PAID, "");
        totalPaid += c.payout;
        System.out.println("  paid " + c.id + " amount=" + c.payout);
    }

    private Claim require(String id) {
        Claim c = claims.get(id);
        if (c == null) throw new RuntimeException("no such claim: " + id);
        return c;
    }

    long totalPaid() { return totalPaid; }

    void printClaim(String id) {
        Claim c = require(id);
        System.out.println("Claim " + c.id + " [" + c.state + "] payout=" + c.payout);
        for (String h : c.history) System.out.println("  - " + h);
    }
}
