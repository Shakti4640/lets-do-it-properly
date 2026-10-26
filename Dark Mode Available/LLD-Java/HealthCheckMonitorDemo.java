/*
 * Health Check Monitor (LLD)
 * --------------------------
 * Monitors services using pluggable health checks. On each simulated tick it
 * runs checks, applies consecutive success/failure thresholds to transition
 * UP/DOWN, and notifies registered observers (alerting) on state changes.
 *
 * Key classes / abstractions:
 *   - HealthStatus           : UP / DOWN / UNKNOWN
 *   - HealthCheck            : Strategy returning healthy/unhealthy for a tick
 *   - ScriptedHealthCheck    : deterministic check driven by a boolean script
 *   - MonitoredService       : tracks streaks and current status
 *   - HealthObserver         : Observer notified on status transitions
 *   - HealthCheckMonitor     : runs ticks, applies thresholds, fires events
 *
 * Patterns used: Strategy (HealthCheck), Observer (alerts), State (UP/DOWN).
 */
import java.util.*;

public class HealthCheckMonitorDemo {
    public static void main(String[] args) {
        HealthCheckMonitor monitor = new HealthCheckMonitor(2, 2); // 2 to go DOWN, 2 to go UP
        monitor.addObserver(new LoggingAlertObserver());

        // Scripted results per tick: true = healthy, false = unhealthy.
        monitor.register("api", new ScriptedHealthCheck(
                Arrays.asList(true, true, false, false, false, true, true)));
        monitor.register("db", new ScriptedHealthCheck(
                Arrays.asList(true, false, true, false, true, false, true)));

        for (int tick = 1; tick <= 7; tick++) {
            System.out.println("=== Tick " + tick + " ===");
            monitor.runTick(tick);
        }

        System.out.println("\n=== Final statuses ===");
        System.out.println(monitor.snapshot());
    }
}

enum HealthStatus { UP, DOWN, UNKNOWN }

interface HealthCheck {
    boolean isHealthy(int tick);
}

class ScriptedHealthCheck implements HealthCheck {
    private final List<Boolean> script;
    ScriptedHealthCheck(List<Boolean> script) { this.script = script; }
    public boolean isHealthy(int tick) {
        int idx = tick - 1;
        if (idx < 0 || idx >= script.size()) return false;
        return script.get(idx);
    }
}

class MonitoredService {
    final String name;
    final HealthCheck check;
    HealthStatus status = HealthStatus.UNKNOWN;
    int successStreak = 0;
    int failureStreak = 0;
    MonitoredService(String name, HealthCheck check) { this.name = name; this.check = check; }
}

interface HealthObserver {
    void onStatusChange(String service, HealthStatus from, HealthStatus to, int tick);
}

class LoggingAlertObserver implements HealthObserver {
    public void onStatusChange(String service, HealthStatus from, HealthStatus to, int tick) {
        String severity = (to == HealthStatus.DOWN) ? "ALERT" : "RECOVERED";
        System.out.println("  [" + severity + "] " + service + ": " + from + " -> " + to + " (tick " + tick + ")");
    }
}

class HealthCheckMonitor {
    private final Map<String, MonitoredService> services = new LinkedHashMap<>();
    private final List<HealthObserver> observers = new ArrayList<>();
    private final int failureThreshold;
    private final int successThreshold;

    HealthCheckMonitor(int failureThreshold, int successThreshold) {
        this.failureThreshold = failureThreshold;
        this.successThreshold = successThreshold;
    }

    void register(String name, HealthCheck check) {
        services.put(name, new MonitoredService(name, check));
    }
    void addObserver(HealthObserver o) { observers.add(o); }

    void runTick(int tick) {
        for (MonitoredService svc : services.values()) {
            boolean healthy = svc.check.isHealthy(tick);
            if (healthy) {
                svc.successStreak++;
                svc.failureStreak = 0;
            } else {
                svc.failureStreak++;
                svc.successStreak = 0;
            }
            System.out.println("  " + svc.name + ": " + (healthy ? "healthy" : "unhealthy")
                    + " (ok=" + svc.successStreak + ", fail=" + svc.failureStreak + ", status=" + svc.status + ")");

            HealthStatus newStatus = svc.status;
            if (svc.failureStreak >= failureThreshold) newStatus = HealthStatus.DOWN;
            else if (svc.successStreak >= successThreshold) newStatus = HealthStatus.UP;

            if (newStatus != svc.status) {
                HealthStatus old = svc.status;
                svc.status = newStatus;
                for (HealthObserver o : observers) o.onStatusChange(svc.name, old, newStatus, tick);
            }
        }
    }

    String snapshot() {
        StringBuilder sb = new StringBuilder();
        for (MonitoredService svc : services.values()) {
            sb.append(svc.name).append("=").append(svc.status).append("  ");
        }
        return sb.toString().trim();
    }
}
