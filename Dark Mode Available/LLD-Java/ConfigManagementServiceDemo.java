/*
 * Config Management Service (LLD)
 * -------------------------------
 * A key-value configuration store scoped by environment + namespace. Updates
 * are versioned (every change snapshots a new version), watchers are notified
 * on change (Observer), and any prior version can be restored (rollback).
 *
 * Key classes / abstractions:
 *   - ConfigScope            : (environment, namespace) key used to isolate configs
 *   - ConfigVersion          : an immutable snapshot of key-values with a version no.
 *   - ConfigWatcher          : Observer notified when a scope's value changes
 *   - ConfigManagementService: set/get, version history, rollback, watcher mgmt
 *
 * Patterns used: Observer (watchers), Memento (versioned snapshots), Value Object.
 */
import java.util.*;

public class ConfigManagementServiceDemo {
    public static void main(String[] args) {
        ConfigManagementService cfg = new ConfigManagementService();
        ConfigScope prod = new ConfigScope("prod", "payment-service");

        cfg.watch(prod, new LoggingWatcher("audit"));

        System.out.println("=== Initial sets (each bumps version) ===");
        cfg.set(prod, "timeout.ms", "3000");
        cfg.set(prod, "retries", "2");
        cfg.set(prod, "feature.x", "off");
        System.out.println("current = " + cfg.current(prod));

        System.out.println("\n=== Update a couple of keys ===");
        cfg.set(prod, "feature.x", "on");
        cfg.set(prod, "timeout.ms", "5000");
        System.out.println("current = " + cfg.current(prod));
        System.out.println("get retries -> " + cfg.get(prod, "retries"));

        System.out.println("\n=== Version history ===");
        for (ConfigVersion v : cfg.history(prod)) {
            System.out.println("  v" + v.version + " " + v.values);
        }

        System.out.println("\n=== Rollback to v3 ===");
        cfg.rollback(prod, 3);
        System.out.println("current = " + cfg.current(prod));

        System.out.println("\n=== Isolation: staging scope is independent ===");
        ConfigScope staging = new ConfigScope("staging", "payment-service");
        cfg.set(staging, "timeout.ms", "1000");
        System.out.println("staging = " + cfg.current(staging));
        System.out.println("prod    = " + cfg.current(prod));
    }
}

class ConfigScope {
    final String environment;
    final String namespace;
    ConfigScope(String environment, String namespace) {
        this.environment = environment; this.namespace = namespace;
    }
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConfigScope)) return false;
        ConfigScope c = (ConfigScope) o;
        return environment.equals(c.environment) && namespace.equals(c.namespace);
    }
    public int hashCode() { return Objects.hash(environment, namespace); }
    public String toString() { return environment + "/" + namespace; }
}

class ConfigVersion {
    final int version;
    final Map<String, String> values;
    ConfigVersion(int version, Map<String, String> values) {
        this.version = version;
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}

interface ConfigWatcher {
    void onChange(ConfigScope scope, int newVersion, Map<String, String> values);
}

class LoggingWatcher implements ConfigWatcher {
    private final String name;
    LoggingWatcher(String name) { this.name = name; }
    public void onChange(ConfigScope scope, int newVersion, Map<String, String> values) {
        System.out.println("  [watch:" + name + "] " + scope + " -> v" + newVersion + " " + values);
    }
}

class ConfigManagementService {
    private final Map<ConfigScope, List<ConfigVersion>> history = new HashMap<>();
    private final Map<ConfigScope, List<ConfigWatcher>> watchers = new HashMap<>();

    void watch(ConfigScope scope, ConfigWatcher watcher) {
        watchers.computeIfAbsent(scope, k -> new ArrayList<>()).add(watcher);
    }

    void set(ConfigScope scope, String key, String value) {
        Map<String, String> base = new LinkedHashMap<>(currentValues(scope));
        base.put(key, value);
        commit(scope, base);
    }

    String get(ConfigScope scope, String key) { return currentValues(scope).get(key); }

    Map<String, String> current(ConfigScope scope) {
        return new LinkedHashMap<>(currentValues(scope));
    }

    List<ConfigVersion> history(ConfigScope scope) {
        return history.getOrDefault(scope, Collections.<ConfigVersion>emptyList());
    }

    void rollback(ConfigScope scope, int targetVersion) {
        for (ConfigVersion v : history(scope)) {
            if (v.version == targetVersion) {
                commit(scope, v.values); // creates a new version equal to the target snapshot
                return;
            }
        }
        throw new NoSuchElementException("No version " + targetVersion + " for " + scope);
    }

    private Map<String, String> currentValues(ConfigScope scope) {
        List<ConfigVersion> versions = history.get(scope);
        if (versions == null || versions.isEmpty()) return Collections.emptyMap();
        return versions.get(versions.size() - 1).values;
    }

    private void commit(ConfigScope scope, Map<String, String> values) {
        List<ConfigVersion> versions = history.computeIfAbsent(scope, k -> new ArrayList<>());
        int nextVersion = versions.size() + 1;
        ConfigVersion snapshot = new ConfigVersion(nextVersion, values);
        versions.add(snapshot);
        for (ConfigWatcher w : watchers.getOrDefault(scope, Collections.<ConfigWatcher>emptyList())) {
            w.onChange(scope, nextVersion, snapshot.values);
        }
    }
}
