/*
 * Service Discovery (LLD)
 * -----------------------
 * A service registry where instances register with heartbeats. Stale instances
 * (no heartbeat within TTL) are evicted using an injected clock. Lookups return
 * only healthy instances, and a pluggable load balancer picks one.
 *
 * Key classes / abstractions:
 *   - ServiceInstance        : an addressable instance with last-heartbeat time
 *   - ServiceRegistry        : register / deregister / heartbeat / evict / lookup
 *   - LoadBalancer           : Strategy for choosing among healthy instances
 *   - RoundRobinLoadBalancer : deterministic rotation
 *   - Clock                  : injected time source for deterministic TTLs
 *
 * Patterns used: Strategy (load balancing), Registry, DI (Clock).
 */
import java.util.*;

public class ServiceDiscoveryDemo {
    public static void main(String[] args) {
        MutableClock clock = new MutableClock(0L);
        ServiceRegistry registry = new ServiceRegistry(clock, 10); // TTL = 10s

        registry.register(new ServiceInstance("payments", "i-1", "10.0.0.1", 8080));
        registry.register(new ServiceInstance("payments", "i-2", "10.0.0.2", 8080));
        registry.register(new ServiceInstance("payments", "i-3", "10.0.0.3", 8080));

        LoadBalancer lb = new RoundRobinLoadBalancer();

        System.out.println("=== Healthy lookup (all fresh) ===");
        System.out.println("healthy: " + registry.healthy("payments"));
        for (int i = 0; i < 4; i++) {
            System.out.println("pick -> " + lb.choose(registry.healthy("payments")));
        }

        System.out.println("\n=== Advance 6s, heartbeat i-1 and i-2 only ===");
        clock.advance(6);
        registry.heartbeat("payments", "i-1");
        registry.heartbeat("payments", "i-2");

        System.out.println("\n=== Advance another 6s (i-3 now stale, TTL=10s) ===");
        clock.advance(6);
        registry.evictStale();
        System.out.println("healthy: " + registry.healthy("payments"));

        System.out.println("\n=== Deregister i-2 ===");
        registry.deregister("payments", "i-2");
        System.out.println("healthy: " + registry.healthy("payments"));

        System.out.println("\n=== Pick from remaining ===");
        System.out.println("pick -> " + lb.choose(registry.healthy("payments")));
        System.out.println("pick -> " + lb.choose(registry.healthy("payments")));
    }
}

class Clock {
    long now() { return 0L; }
}

class MutableClock extends Clock {
    private long seconds;
    MutableClock(long start) { this.seconds = start; }
    long now() { return seconds; }
    void advance(long delta) { seconds += delta; System.out.println("  [clock] now=" + seconds); }
}

class ServiceInstance {
    final String serviceName;
    final String instanceId;
    final String host;
    final int port;
    long lastHeartbeat;
    ServiceInstance(String serviceName, String instanceId, String host, int port) {
        this.serviceName = serviceName; this.instanceId = instanceId;
        this.host = host; this.port = port;
    }
    public String toString() { return instanceId + "@" + host + ":" + port; }
}

interface LoadBalancer {
    ServiceInstance choose(List<ServiceInstance> instances);
}

class RoundRobinLoadBalancer implements LoadBalancer {
    private int counter = 0;
    public ServiceInstance choose(List<ServiceInstance> instances) {
        if (instances == null || instances.isEmpty()) return null;
        ServiceInstance picked = instances.get(counter % instances.size());
        counter++;
        return picked;
    }
}

class ServiceRegistry {
    // serviceName -> (instanceId -> instance)
    private final Map<String, Map<String, ServiceInstance>> services = new HashMap<>();
    private final Clock clock;
    private final long ttlSeconds;

    ServiceRegistry(Clock clock, long ttlSeconds) {
        this.clock = clock; this.ttlSeconds = ttlSeconds;
    }

    void register(ServiceInstance instance) {
        instance.lastHeartbeat = clock.now();
        services.computeIfAbsent(instance.serviceName, k -> new LinkedHashMap<>())
                .put(instance.instanceId, instance);
        System.out.println("  [register] " + instance.serviceName + " " + instance);
    }

    void deregister(String serviceName, String instanceId) {
        Map<String, ServiceInstance> m = services.get(serviceName);
        if (m != null && m.remove(instanceId) != null) {
            System.out.println("  [deregister] " + serviceName + " " + instanceId);
        }
    }

    void heartbeat(String serviceName, String instanceId) {
        Map<String, ServiceInstance> m = services.get(serviceName);
        if (m != null && m.containsKey(instanceId)) {
            m.get(instanceId).lastHeartbeat = clock.now();
            System.out.println("  [heartbeat] " + serviceName + " " + instanceId + " @ " + clock.now());
        }
    }

    void evictStale() {
        long cutoff = clock.now() - ttlSeconds;
        for (Map.Entry<String, Map<String, ServiceInstance>> svc : services.entrySet()) {
            Iterator<Map.Entry<String, ServiceInstance>> it = svc.getValue().entrySet().iterator();
            while (it.hasNext()) {
                ServiceInstance inst = it.next().getValue();
                if (inst.lastHeartbeat < cutoff) {
                    System.out.println("  [evict-stale] " + svc.getKey() + " " + inst.instanceId
                            + " (last=" + inst.lastHeartbeat + ", cutoff=" + cutoff + ")");
                    it.remove();
                }
            }
        }
    }

    List<ServiceInstance> healthy(String serviceName) {
        long cutoff = clock.now() - ttlSeconds;
        List<ServiceInstance> result = new ArrayList<>();
        Map<String, ServiceInstance> m = services.get(serviceName);
        if (m == null) return result;
        for (ServiceInstance inst : m.values()) {
            if (inst.lastHeartbeat >= cutoff) result.add(inst);
        }
        return result;
    }
}
