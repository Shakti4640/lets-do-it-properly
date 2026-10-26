/*
 * Delivery Route Planner - Low Level Design
 * --------------------------------------------------------------
 * Models a weighted graph of locations connected by roads. Computes the
 * shortest path between two points using Dijkstra's algorithm, and plans a
 * multi-stop delivery route from a depot using a nearest-neighbor heuristic.
 *
 * Key classes / abstractions:
 *   - Graph          : adjacency list of weighted edges (undirected).
 *   - Dijkstra       : shortest path + distance between two nodes.
 *   - RoutePlanner   : nearest-neighbor tour over a set of stops.
 *
 * Patterns used:
 *   - Strategy (pluggable path algorithm)
 *   - Facade   (RoutePlanner hides graph + algorithm details)
 */

import java.util.*;

public class DeliveryRoutePlannerDemo {
    public static void main(String[] args) {
        Graph g = new Graph();
        g.addEdge("Depot", "A", 4);
        g.addEdge("Depot", "B", 2);
        g.addEdge("A", "B", 1);
        g.addEdge("A", "C", 5);
        g.addEdge("B", "C", 8);
        g.addEdge("B", "D", 10);
        g.addEdge("C", "D", 2);
        g.addEdge("C", "E", 3);
        g.addEdge("D", "E", 4);

        Dijkstra dijkstra = new Dijkstra(g);

        System.out.println("=== Shortest Path: Depot -> E ===");
        PathResult pr = dijkstra.shortestPath("Depot", "E");
        System.out.println("  Path: " + String.join(" -> ", pr.getPath()));
        System.out.println("  Distance: " + pr.getDistance());

        System.out.println("\n=== Shortest Path: Depot -> D ===");
        PathResult pr2 = dijkstra.shortestPath("Depot", "D");
        System.out.println("  Path: " + String.join(" -> ", pr2.getPath()));
        System.out.println("  Distance: " + pr2.getDistance());

        System.out.println("\n=== Multi-stop Plan (nearest-neighbor from Depot) ===");
        RoutePlanner planner = new RoutePlanner(dijkstra);
        List<String> stops = Arrays.asList("C", "A", "E", "D");
        PathResult tour = planner.planTour("Depot", stops);
        System.out.println("  Visit order: " + String.join(" -> ", tour.getPath()));
        System.out.println("  Total distance: " + tour.getDistance());
    }
}

class Edge {
    final String to;
    final int weight;
    Edge(String to, int weight) { this.to = to; this.weight = weight; }
}

class Graph {
    private final Map<String, List<Edge>> adj = new HashMap<>();

    public void addEdge(String u, String v, int w) {
        adj.computeIfAbsent(u, k -> new ArrayList<Edge>()).add(new Edge(v, w));
        adj.computeIfAbsent(v, k -> new ArrayList<Edge>()).add(new Edge(u, w));
    }

    public List<Edge> neighbors(String node) {
        return adj.getOrDefault(node, Collections.<Edge>emptyList());
    }

    public Set<String> nodes() { return adj.keySet(); }
}

class PathResult {
    private final List<String> path;
    private final int distance;

    public PathResult(List<String> path, int distance) {
        this.path = path;
        this.distance = distance;
    }

    public List<String> getPath() { return path; }
    public int getDistance() { return distance; }
}

class Dijkstra {
    private final Graph graph;

    public Dijkstra(Graph graph) { this.graph = graph; }

    public PathResult shortestPath(String src, String dst) {
        Map<String, Integer> dist = new HashMap<>();
        Map<String, String> prev = new HashMap<>();
        for (String n : graph.nodes()) dist.put(n, Integer.MAX_VALUE);
        dist.put(src, 0);

        PriorityQueue<String> pq = new PriorityQueue<>(Comparator.comparingInt(dist::get));
        pq.add(src);
        Set<String> settled = new HashSet<>();

        while (!pq.isEmpty()) {
            String u = pq.poll();
            if (!settled.add(u)) continue;
            if (u.equals(dst)) break;
            int du = dist.get(u);
            if (du == Integer.MAX_VALUE) break;
            for (Edge e : graph.neighbors(u)) {
                int nd = du + e.weight;
                if (nd < dist.getOrDefault(e.to, Integer.MAX_VALUE)) {
                    dist.put(e.to, nd);
                    prev.put(e.to, u);
                    pq.add(e.to);
                }
            }
        }

        int total = dist.getOrDefault(dst, Integer.MAX_VALUE);
        if (total == Integer.MAX_VALUE) {
            return new PathResult(Collections.<String>emptyList(), -1);
        }
        LinkedList<String> path = new LinkedList<>();
        for (String at = dst; at != null; at = prev.get(at)) {
            path.addFirst(at);
        }
        return new PathResult(path, total);
    }
}

class RoutePlanner {
    private final Dijkstra dijkstra;

    public RoutePlanner(Dijkstra dijkstra) { this.dijkstra = dijkstra; }

    public PathResult planTour(String start, List<String> stops) {
        List<String> remaining = new ArrayList<>(stops);
        List<String> order = new ArrayList<>();
        order.add(start);
        String current = start;
        int total = 0;

        while (!remaining.isEmpty()) {
            String best = null;
            PathResult bestLeg = null;
            for (String candidate : remaining) {
                PathResult leg = dijkstra.shortestPath(current, candidate);
                if (bestLeg == null || leg.getDistance() < bestLeg.getDistance()) {
                    bestLeg = leg;
                    best = candidate;
                }
            }
            // append the leg path (skip the duplicated current node)
            List<String> legPath = bestLeg.getPath();
            for (int i = 1; i < legPath.size(); i++) {
                order.add(legPath.get(i));
            }
            total += bestLeg.getDistance();
            current = best;
            remaining.remove(best);
        }
        return new PathResult(order, total);
    }
}
