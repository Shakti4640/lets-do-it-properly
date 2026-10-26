/*
 * Recommendation Engine (LLD focus) - Low Level Design
 * ----------------------------------------------------
 * Recommends items to a user using pluggable strategies:
 *   - ContentBasedStrategy        : items sharing tags with what the user liked
 *   - CollaborativeStrategy       : items liked by similar users
 *
 * Key classes:
 *   - Item / User (ratings)
 *   - RecommendationStrategy (+ two implementations)
 *   - RecommendationEngine (facade, swap strategy at runtime)
 *
 * Patterns used: Strategy (algorithms), Facade.
 */

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RecommendationEngineDemo {
    public static void main(String[] args) {
        RecommendationEngine engine = new RecommendationEngine();

        engine.addItem(new Item("i1", "Inception", new String[]{"sci-fi","thriller"}));
        engine.addItem(new Item("i2", "Interstellar", new String[]{"sci-fi","drama"}));
        engine.addItem(new Item("i3", "The Notebook", new String[]{"romance","drama"}));
        engine.addItem(new Item("i4", "Matrix", new String[]{"sci-fi","action"}));

        engine.rate("alice", "i1", 5);
        engine.rate("alice", "i2", 4);
        engine.rate("bob", "i1", 5);
        engine.rate("bob", "i4", 5);
        engine.rate("carol", "i3", 5);

        System.out.println("=== Recommendation Engine Demo ===");
        engine.setStrategy(new ContentBasedStrategy());
        System.out.println("Content-based for alice: " + engine.recommend("alice", 3));

        engine.setStrategy(new CollaborativeStrategy());
        System.out.println("Collaborative for alice: " + engine.recommend("alice", 3));
    }
}

class Item {
    final String id; final String title; final Set<String> tags = new HashSet<String>();
    Item(String id, String title, String[] tags) {
        this.id = id; this.title = title;
        for (String t : tags) this.tags.add(t);
    }
    public String toString() { return title; }
}

class UserProfile {
    final String name;
    final Map<String, Integer> ratings = new HashMap<String, Integer>(); // itemId -> rating
    UserProfile(String name) { this.name = name; }
}

interface RecommendationStrategy {
    List<Item> recommend(UserProfile user, Map<String, Item> items, Map<String, UserProfile> users, int k);
}

class ContentBasedStrategy implements RecommendationStrategy {
    public List<Item> recommend(UserProfile user, Map<String, Item> items, Map<String, UserProfile> users, int k) {
        // Build preferred tag set from highly-rated items
        Map<String, Integer> tagScore = new HashMap<String, Integer>();
        for (Map.Entry<String, Integer> e : user.ratings.entrySet()) {
            Item it = items.get(e.getKey());
            if (it != null && e.getValue() >= 4) for (String tag : it.tags) tagScore.merge(tag, e.getValue(), Integer::sum);
        }
        List<Item> candidates = new ArrayList<Item>();
        for (Item it : items.values()) {
            if (user.ratings.containsKey(it.id)) continue;
            int score = 0;
            for (String tag : it.tags) score += tagScore.getOrDefault(tag, 0);
            if (score > 0) candidates.add(it);
        }
        candidates.sort(new Comparator<Item>() {
            public int compare(Item a, Item b) { return Integer.compare(tagMatch(b, tagScore), tagMatch(a, tagScore)); }
            private int tagMatch(Item it, Map<String, Integer> ts) {
                int s = 0; for (String t : it.tags) s += ts.getOrDefault(t, 0); return s;
            }
        });
        return candidates.subList(0, Math.min(k, candidates.size()));
    }
}

class CollaborativeStrategy implements RecommendationStrategy {
    public List<Item> recommend(UserProfile user, Map<String, Item> items, Map<String, UserProfile> users, int k) {
        // Find most similar user by overlap of highly-rated items
        UserProfile bestPeer = null; int bestOverlap = 0;
        for (UserProfile other : users.values()) {
            if (other == user) continue;
            int overlap = 0;
            for (String itemId : user.ratings.keySet())
                if (other.ratings.containsKey(itemId)) overlap++;
            if (overlap > bestOverlap) { bestOverlap = overlap; bestPeer = other; }
        }
        List<Item> result = new ArrayList<Item>();
        if (bestPeer != null) {
            for (Map.Entry<String, Integer> e : bestPeer.ratings.entrySet()) {
                if (!user.ratings.containsKey(e.getKey()) && e.getValue() >= 4) {
                    result.add(items.get(e.getKey()));
                }
            }
        }
        return result.subList(0, Math.min(k, result.size()));
    }
}

class RecommendationEngine {
    private final Map<String, Item> items = new HashMap<String, Item>();
    private final Map<String, UserProfile> users = new HashMap<String, UserProfile>();
    private RecommendationStrategy strategy = new ContentBasedStrategy();

    void addItem(Item item) { items.put(item.id, item); }
    void setStrategy(RecommendationStrategy s) { this.strategy = s; }

    void rate(String username, String itemId, int rating) {
        UserProfile u = users.computeIfAbsent(username, UserProfile::new);
        u.ratings.put(itemId, rating);
    }

    List<Item> recommend(String username, int k) {
        UserProfile u = users.get(username);
        if (u == null) return new ArrayList<Item>();
        return strategy.recommend(u, items, users, k);
    }
}
