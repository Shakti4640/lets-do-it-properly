/*
 * Metrics and Monitoring System - Low Level Design
 * ------------------------------------------------
 * Collects metrics (counters, gauges, histograms), supports tagging, and
 * evaluates alert rules against aggregated values (Observer for alerts).
 *
 * Key classes:
 *   - Metric (abstract) + Counter, Gauge, Histogram
 *   - MetricRegistry
 *   - AlertRule + AlertObserver
 *   - MonitoringSystem (facade)
 *
 * Patterns used: Strategy (metric types), Observer (alerts), Facade.
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MetricsMonitoringDemo {
    public static void main(String[] args) {
        MonitoringSystem monitoring = new MonitoringSystem();
        monitoring.addAlertObserver(new ConsoleAlert());

        System.out.println("=== Metrics & Monitoring Demo ===");
        Counter requests = monitoring.counter("http_requests");
        Gauge cpu = monitoring.gauge("cpu_usage");
        Histogram latency = monitoring.histogram("request_latency_ms");

        requests.inc(); requests.inc(); requests.inc();
        cpu.set(72.5);
        latency.record(120); latency.record(85); latency.record(200);

        // Alert if cpu > 70
        monitoring.addAlert(new AlertRule("high_cpu", "cpu_usage", 70.0));
        // Alert if avg latency > 150
        monitoring.addAlert(new AlertRule("slow_requests", "request_latency_ms", 150.0));

        monitoring.evaluateAlerts();
        monitoring.report();
    }
}

abstract class Metric {
    final String name;
    Metric(String name) { this.name = name; }
    abstract double value();
}

class Counter extends Metric {
    private long count = 0;
    Counter(String name) { super(name); }
    void inc() { count++; }
    void inc(long n) { count += n; }
    double value() { return count; }
}

class Gauge extends Metric {
    private double current = 0;
    Gauge(String name) { super(name); }
    void set(double v) { current = v; }
    double value() { return current; }
}

class Histogram extends Metric {
    private final List<Double> samples = new ArrayList<Double>();
    Histogram(String name) { super(name); }
    void record(double v) { samples.add(v); }
    double value() { // returns average
        if (samples.isEmpty()) return 0;
        double sum = 0; for (double s : samples) sum += s;
        return sum / samples.size();
    }
    double percentile(double p) {
        if (samples.isEmpty()) return 0;
        List<Double> sorted = new ArrayList<Double>(samples);
        java.util.Collections.sort(sorted);
        int idx = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
    }
}

class AlertRule {
    final String name; final String metricName; final double threshold;
    AlertRule(String name, String metricName, double threshold) {
        this.name = name; this.metricName = metricName; this.threshold = threshold;
    }
}

interface AlertObserver {
    void onAlert(AlertRule rule, double actual);
}

class ConsoleAlert implements AlertObserver {
    public void onAlert(AlertRule rule, double actual) {
        System.out.println("  [ALERT] " + rule.name + ": " + rule.metricName + "=" + actual
                + " exceeded threshold " + rule.threshold);
    }
}

class MetricRegistry {
    final Map<String, Metric> metrics = new HashMap<String, Metric>();
    <T extends Metric> T register(T metric) { metrics.put(metric.name, metric); return metric; }
    Metric get(String name) { return metrics.get(name); }
}

class MonitoringSystem {
    private final MetricRegistry registry = new MetricRegistry();
    private final List<AlertRule> rules = new ArrayList<AlertRule>();
    private final List<AlertObserver> observers = new ArrayList<AlertObserver>();

    Counter counter(String name) { return registry.register(new Counter(name)); }
    Gauge gauge(String name) { return registry.register(new Gauge(name)); }
    Histogram histogram(String name) { return registry.register(new Histogram(name)); }

    void addAlert(AlertRule rule) { rules.add(rule); }
    void addAlertObserver(AlertObserver o) { observers.add(o); }

    void evaluateAlerts() {
        for (AlertRule rule : rules) {
            Metric m = registry.get(rule.metricName);
            if (m != null && m.value() > rule.threshold) {
                for (AlertObserver o : observers) o.onAlert(rule, m.value());
            }
        }
    }

    void report() {
        System.out.println("--- Metrics ---");
        for (Metric m : registry.metrics.values()) {
            System.out.println("  " + m.name + " = " + m.value());
        }
    }
}
