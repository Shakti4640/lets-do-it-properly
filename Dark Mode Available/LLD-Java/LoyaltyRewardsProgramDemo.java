/*
 * Loyalty Rewards Program - Low Level Design
 * ------------------------------------------
 * Members earn points when they spend. Tier (Silver/Gold/Platinum) is derived
 * from lifetime points and applies an earn multiplier. Members can redeem
 * points (e.g. 100 points = $1). Tier upgrades happen automatically as
 * lifetime points cross thresholds.
 *
 * Key classes / abstractions:
 *   - Tier               : enum with threshold + multiplier
 *   - Member             : balance, lifetime points, current tier
 *   - LoyaltyProgram     : earn, redeem, tier recomputation
 *
 * Patterns used: Strategy-via-enum (Tier multipliers), Facade (LoyaltyProgram).
 */

import java.util.LinkedHashMap;
import java.util.Map;

public class LoyaltyRewardsProgramDemo {
    public static void main(String[] args) {
        LoyaltyProgram program = new LoyaltyProgram();
        program.enroll("alice");
        program.enroll("bob");

        System.out.println("=== Loyalty Rewards Program Demo ===");

        // 1 point per dollar (price in cents), times tier multiplier.
        program.recordSpend("alice", 5000);   // $50
        program.recordSpend("alice", 30000);  // $300  -> crosses Gold
        program.recordSpend("bob", 12000);    // $120

        System.out.println("\n--- Member status ---");
        program.printMember("alice");
        program.printMember("bob");

        System.out.println("\n--- Big spend pushes Alice to Platinum ---");
        program.recordSpend("alice", 800000); // $8000
        program.printMember("alice");

        System.out.println("\n--- Alice redeems 200 points ---");
        program.redeem("alice", 200);
        program.printMember("alice");

        System.out.println("\n--- Bob tries to over-redeem ---");
        program.redeem("bob", 99999);
        program.printMember("bob");
    }
}

enum Tier {
    SILVER(0, 1.0),
    GOLD(300, 1.5),
    PLATINUM(5000, 2.0);

    final int minLifetimePoints;
    final double multiplier;

    Tier(int minLifetimePoints, double multiplier) {
        this.minLifetimePoints = minLifetimePoints;
        this.multiplier = multiplier;
    }

    static Tier forLifetimePoints(int lifetimePoints) {
        Tier result = SILVER;
        for (Tier t : values()) {
            if (lifetimePoints >= t.minLifetimePoints) result = t;
        }
        return result;
    }
}

class Member {
    final String name;
    int pointBalance = 0;
    int lifetimePoints = 0;
    Tier tier = Tier.SILVER;
    Member(String name) { this.name = name; }
}

class LoyaltyProgram {
    static final int CENTS_PER_BASE_POINT = 100; // $1 spend = 1 base point
    static final int POINTS_PER_DOLLAR_REDEEM = 100; // 100 points = $1

    private final Map<String, Member> members = new LinkedHashMap<String, Member>();

    void enroll(String name) {
        members.put(name, new Member(name));
        System.out.println("Enrolled " + name + " at " + Tier.SILVER);
    }

    private Member require(String name) {
        Member m = members.get(name);
        if (m == null) throw new IllegalArgumentException("No such member: " + name);
        return m;
    }

    void recordSpend(String name, long amountCents) {
        Member m = require(name);
        int basePoints = (int) (amountCents / CENTS_PER_BASE_POINT);
        int earned = (int) Math.floor(basePoints * m.tier.multiplier);
        m.pointBalance += earned;
        m.lifetimePoints += earned;
        System.out.println(name + " spent $" + (amountCents / 100)
                + " -> earned " + earned + " points (x" + m.tier.multiplier
                + " at " + m.tier + ")");
        recomputeTier(m);
    }

    private void recomputeTier(Member m) {
        Tier newTier = Tier.forLifetimePoints(m.lifetimePoints);
        if (newTier != m.tier) {
            System.out.println("  ** " + m.name + " upgraded " + m.tier + " -> " + newTier + " **");
            m.tier = newTier;
        }
    }

    void redeem(String name, int points) {
        Member m = require(name);
        if (points <= 0) {
            System.out.println(name + " redeem amount must be positive");
            return;
        }
        if (points > m.pointBalance) {
            System.out.println(name + " cannot redeem " + points
                    + " points (balance " + m.pointBalance + ")");
            return;
        }
        m.pointBalance -= points;
        long valueCents = (long) points * 100 / POINTS_PER_DOLLAR_REDEEM;
        System.out.println(name + " redeemed " + points + " points for $"
                + String.format("%.2f", valueCents / 100.0));
    }

    void printMember(String name) {
        Member m = require(name);
        System.out.println(m.name + " | tier=" + m.tier
                + " | balance=" + m.pointBalance
                + " | lifetime=" + m.lifetimePoints);
    }
}
