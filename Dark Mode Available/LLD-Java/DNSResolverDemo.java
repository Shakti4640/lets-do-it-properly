/*
 * DNS Resolver (LLD)
 * ------------------
 * Simulates iterative DNS resolution walking root -> TLD -> authoritative
 * name servers, supports record types (A, CNAME, NS), CNAME chasing, and a
 * resolver cache with per-record TTL backed by an injected clock.
 *
 * Key classes / abstractions:
 *   - RecordType                : enum of supported DNS record types
 *   - DnsRecord                 : a single resource record (name, type, value, ttl)
 *   - NameServer                : holds a zone's records; answers or refers (NS)
 *   - ResolverCache             : TTL cache keyed by (name,type) using a Clock
 *   - Clock                     : injected time source for deterministic TTLs
 *   - DnsResolver               : orchestrates iterative resolution + CNAME chasing
 *
 * Patterns used: Strategy-ish referral (NameServer), Facade (DnsResolver), DI (Clock).
 */
import java.util.*;

public class DNSResolverDemo {
    public static void main(String[] args) {
        MutableClock clock = new MutableClock(1000L);

        // Authoritative server for example.com
        NameServer authExample = new NameServer("ns1.example.com");
        authExample.add(new DnsRecord("www.example.com", RecordType.CNAME, "web.example.com", 60));
        authExample.add(new DnsRecord("web.example.com", RecordType.A, "93.184.216.34", 120));
        authExample.add(new DnsRecord("example.com", RecordType.A, "93.184.216.35", 120));

        // TLD server for .com -> refers to authoritative
        NameServer tldCom = new NameServer("a.gtld-servers.net");
        tldCom.addReferral("example.com", authExample);

        // Root server -> refers to TLD
        NameServer root = new NameServer("a.root-servers.net");
        root.addReferral("com", tldCom);

        DnsResolver resolver = new DnsResolver(root, new ResolverCache(clock), clock);

        System.out.println("=== Resolve www.example.com (A) — cold cache, CNAME chase ===");
        ResolveResult r1 = resolver.resolve("www.example.com", RecordType.A);
        System.out.println("Result: " + r1);

        System.out.println("\n=== Resolve www.example.com (A) again — warm cache ===");
        ResolveResult r2 = resolver.resolve("www.example.com", RecordType.A);
        System.out.println("Result: " + r2);

        System.out.println("\n=== Advance clock past CNAME TTL (60s) and resolve again ===");
        clock.advance(65);
        ResolveResult r3 = resolver.resolve("www.example.com", RecordType.A);
        System.out.println("Result: " + r3);

        System.out.println("\n=== Resolve nonexistent host ===");
        ResolveResult r4 = resolver.resolve("ghost.example.com", RecordType.A);
        System.out.println("Result: " + r4);
    }
}

enum RecordType { A, CNAME, NS }

class Clock {
    long now() { return 0L; }
}

class MutableClock extends Clock {
    private long seconds;
    MutableClock(long start) { this.seconds = start; }
    long now() { return seconds; }
    void advance(long delta) { seconds += delta; System.out.println("  [clock] +" + delta + "s -> " + seconds); }
}

class DnsRecord {
    final String name;
    final RecordType type;
    final String value;
    final long ttl;
    DnsRecord(String name, RecordType type, String value, long ttl) {
        this.name = name; this.type = type; this.value = value; this.ttl = ttl;
    }
    public String toString() { return name + " " + type + " -> " + value + " (ttl=" + ttl + ")"; }
}

class NameServer {
    final String hostname;
    private final List<DnsRecord> records = new ArrayList<>();
    private final Map<String, NameServer> referrals = new HashMap<>();

    NameServer(String hostname) { this.hostname = hostname; }
    void add(DnsRecord r) { records.add(r); }
    void addReferral(String zone, NameServer ns) { referrals.put(zone, ns); }

    // Returns a direct answer record if this server is authoritative for it.
    DnsRecord answer(String name, RecordType type) {
        for (DnsRecord r : records) {
            if (r.name.equalsIgnoreCase(name) && r.type == type) return r;
        }
        return null;
    }
    DnsRecord cname(String name) {
        for (DnsRecord r : records) {
            if (r.name.equalsIgnoreCase(name) && r.type == RecordType.CNAME) return r;
        }
        return null;
    }
    // Find a referral by matching the longest label suffix of the query name.
    NameServer referralFor(String name) {
        for (Map.Entry<String, NameServer> e : referrals.entrySet()) {
            String zone = e.getKey();
            if (name.equalsIgnoreCase(zone) || name.toLowerCase().endsWith("." + zone.toLowerCase())) {
                return e.getValue();
            }
        }
        return null;
    }
}

class ResolverCache {
    private static class Entry { DnsRecord record; long expiresAt; }
    private final Map<String, Entry> map = new HashMap<>();
    private final Clock clock;
    ResolverCache(Clock clock) { this.clock = clock; }

    private String key(String name, RecordType type) { return name.toLowerCase() + "|" + type; }

    DnsRecord get(String name, RecordType type) {
        Entry e = map.get(key(name, type));
        if (e == null) return null;
        if (clock.now() >= e.expiresAt) { map.remove(key(name, type)); return null; }
        return e.record;
    }
    void put(DnsRecord r) {
        Entry e = new Entry();
        e.record = r;
        e.expiresAt = clock.now() + r.ttl;
        map.put(key(r.name, r.type), e);
    }
}

class ResolveResult {
    final String queryName;
    final String ipAddress;
    final boolean found;
    final List<String> trace;
    ResolveResult(String queryName, String ipAddress, boolean found, List<String> trace) {
        this.queryName = queryName; this.ipAddress = ipAddress; this.found = found; this.trace = trace;
    }
    public String toString() {
        return (found ? (queryName + " = " + ipAddress) : (queryName + " = NXDOMAIN"))
                + " | trace=" + trace;
    }
}

class DnsResolver {
    private final NameServer root;
    private final ResolverCache cache;
    private final Clock clock;

    DnsResolver(NameServer root, ResolverCache cache, Clock clock) {
        this.root = root; this.cache = cache; this.clock = clock;
    }

    ResolveResult resolve(String name, RecordType type) {
        List<String> trace = new ArrayList<>();
        String current = name;
        int guard = 0;
        while (guard++ < 10) {
            DnsRecord cached = cache.get(current, type);
            if (cached != null) {
                trace.add("cache-hit " + current);
                return new ResolveResult(name, cached.value, true, trace);
            }
            DnsRecord cachedCname = cache.get(current, RecordType.CNAME);
            if (cachedCname != null) {
                trace.add("cache-hit CNAME " + current + " -> " + cachedCname.value);
                current = cachedCname.value;
                continue;
            }
            // Iterative walk from root.
            DnsRecord found = iterativeLookup(current, type, trace);
            if (found == null) {
                return new ResolveResult(name, null, false, trace);
            }
            cache.put(found);
            if (found.type == RecordType.CNAME) {
                trace.add("chase CNAME " + current + " -> " + found.value);
                current = found.value;
                continue;
            }
            return new ResolveResult(name, found.value, true, trace);
        }
        trace.add("loop-guard tripped");
        return new ResolveResult(name, null, false, trace);
    }

    private DnsRecord iterativeLookup(String name, RecordType type, List<String> trace) {
        NameServer server = root;
        int hops = 0;
        while (server != null && hops++ < 10) {
            trace.add("ask " + server.hostname + " for " + name + "/" + type);
            DnsRecord direct = server.answer(name, type);
            if (direct != null) { trace.add("answer " + direct.value); return direct; }
            DnsRecord cn = server.cname(name);
            if (cn != null) { trace.add("answer CNAME " + cn.value); return cn; }
            NameServer next = server.referralFor(name);
            if (next == null) { trace.add("no referral from " + server.hostname); return null; }
            server = next;
        }
        return null;
    }
}
