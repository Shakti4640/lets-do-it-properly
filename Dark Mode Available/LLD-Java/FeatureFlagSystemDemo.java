/*
 * Feature Flag System - Low Level Design
 * --------------------------------------
 * Evaluates feature flags with multiple rollout strategies: global on/off,
 * percentage rollout, and user allow-list.
 *
 * Key classes:
 *   - RolloutStrategy (+ BooleanRollout, PercentageRollout, AllowListRollout)
 *   - FeatureFlag
 *   - FeatureFlagService (facade: register + isEnabled)
 *
 * Patterns used: Strategy (rollout), Facade.
 */

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class FeatureFlagSystemDemo {
    public static void main(String[] args) {
        FeatureFlagService service = new FeatureFlagService();
        service.register(new FeatureFlag("new-ui", new BooleanRollout(true)));
        service.register(new FeatureFlag("beta-search", new PercentageRollout(50)));
        Set<String> vips = new HashSet<String>();
        vips.add("alice"); vips.add("bob");
        service.register(new FeatureFlag("premium", new AllowListRollout(vips)));

        System.out.println("=== Feature Flag System Demo ===");
        System.out.println("new-ui for anyone: " + service.isEnabled("new-ui", "user1"));
        System.out.println("premium alice: " + service.isEnabled("premium", "alice"));
        System.out.println("premium carol: " + service.isEnabled("premium", "carol"));

        System.out.println("-- beta-search 50% rollout across users --");
        for (String u : new String[]{"u1","u2","u3","u4","u5","u6"}) {
            System.out.println("  " + u + ": " + service.isEnabled("beta-search", u));
        }
    }
}

interface RolloutStrategy {
    boolean isEnabledFor(String userId);
}

class BooleanRollout implements RolloutStrategy {
    private final boolean on;
    BooleanRollout(boolean on) { this.on = on; }
    public boolean isEnabledFor(String userId) { return on; }
}

class PercentageRollout implements RolloutStrategy {
    private final int percentage;
    PercentageRollout(int percentage) { this.percentage = percentage; }
    public boolean isEnabledFor(String userId) {
        int bucket = Math.abs(userId.hashCode()) % 100;
        return bucket < percentage;
    }
}

class AllowListRollout implements RolloutStrategy {
    private final Set<String> allowed;
    AllowListRollout(Set<String> allowed) { this.allowed = allowed; }
    public boolean isEnabledFor(String userId) { return allowed.contains(userId); }
}

class FeatureFlag {
    final String name;
    private RolloutStrategy strategy;
    FeatureFlag(String name, RolloutStrategy strategy) { this.name = name; this.strategy = strategy; }
    void setStrategy(RolloutStrategy s) { this.strategy = s; }
    boolean evaluate(String userId) { return strategy.isEnabledFor(userId); }
}

class FeatureFlagService {
    private final Map<String, FeatureFlag> flags = new HashMap<String, FeatureFlag>();
    void register(FeatureFlag flag) { flags.put(flag.name, flag); }
    boolean isEnabled(String flagName, String userId) {
        FeatureFlag flag = flags.get(flagName);
        return flag != null && flag.evaluate(userId);
    }
}
