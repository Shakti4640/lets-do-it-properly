/*
 * Leaderboard System - Low Level Design
 * -------------------------------------
 * Tracks player scores and supports score updates, top-K queries, a player's
 * rank, and the neighbors around a given player. Scores are kept in a sorted
 * structure (TreeMap keyed by descending score) so ranking queries are cheap.
 *
 * Key classes / abstractions:
 *   - Leaderboard (TreeMap-backed ranking)
 *   - ScoreKey (score desc, name asc tie-break)
 *   - RankedEntry (query result)
 *
 * Patterns used: Comparator-driven ordering, Facade (Leaderboard API).
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class LeaderboardSystemDemo {
    public static void main(String[] args) {
        System.out.println("=== Leaderboard System Demo ===");
        Leaderboard board = new Leaderboard();

        board.updateScore("alice", 50);
        board.updateScore("bob", 80);
        board.updateScore("carol", 65);
        board.updateScore("dave", 80);   // tie with bob; name breaks tie
        board.updateScore("erin", 30);

        System.out.println("\n-- Top 3 --");
        for (RankedEntry e : board.topK(3)) System.out.println("  " + e);

        System.out.println("\n-- Ranks --");
        for (String p : new String[]{"bob", "dave", "carol", "erin"}) {
            System.out.println("  " + p + " is rank " + board.rankOf(p) + " (score " + board.scoreOf(p) + ")");
        }

        System.out.println("\n-- Update: carol +30 (=95) --");
        board.updateScore("carol", 30);
        System.out.println("  carol new score=" + board.scoreOf("carol") + " rank=" + board.rankOf("carol"));

        System.out.println("\n-- Neighbors around carol (k=1) --");
        for (RankedEntry e : board.neighbors("carol", 1)) System.out.println("  " + e);

        System.out.println("\n-- Full standings --");
        for (RankedEntry e : board.topK(board.size())) System.out.println("  " + e);
    }
}

class ScoreKey implements Comparable<ScoreKey> {
    final int score;
    final String name;
    ScoreKey(int score, String name) { this.score = score; this.name = name; }
    // Higher score first; tie-break by name ascending.
    public int compareTo(ScoreKey o) {
        if (this.score != o.score) return o.score - this.score;
        return this.name.compareTo(o.name);
    }
}

class RankedEntry {
    final int rank;
    final String name;
    final int score;
    RankedEntry(int rank, String name, int score) { this.rank = rank; this.name = name; this.score = score; }
    public String toString() { return "#" + rank + " " + name + " = " + score; }
}

class Leaderboard {
    private final TreeMap<ScoreKey, String> ordered = new TreeMap<ScoreKey, String>();
    private final Map<String, Integer> scores = new HashMap<String, Integer>();

    void updateScore(String player, int delta) {
        Integer existing = scores.get(player);
        if (existing != null) ordered.remove(new ScoreKey(existing, player));
        int updated = (existing == null ? 0 : existing) + delta;
        scores.put(player, updated);
        ordered.put(new ScoreKey(updated, player), player);
    }

    Integer scoreOf(String player) { return scores.get(player); }
    int size() { return ordered.size(); }

    int rankOf(String player) {
        Integer score = scores.get(player);
        if (score == null) return -1;
        ScoreKey target = new ScoreKey(score, player);
        int rank = 1;
        for (ScoreKey key : ordered.keySet()) {
            if (key.compareTo(target) == 0) return rank;
            rank++;
        }
        return -1;
    }

    List<RankedEntry> topK(int k) {
        List<RankedEntry> result = new ArrayList<RankedEntry>();
        int rank = 1;
        for (Map.Entry<ScoreKey, String> e : ordered.entrySet()) {
            if (rank > k) break;
            result.add(new RankedEntry(rank, e.getValue(), e.getKey().score));
            rank++;
        }
        return result;
    }

    // k entries above and k below the given player (inclusive of the player).
    List<RankedEntry> neighbors(String player, int k) {
        List<RankedEntry> all = topK(ordered.size());
        int idx = -1;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).name.equals(player)) { idx = i; break; }
        }
        List<RankedEntry> result = new ArrayList<RankedEntry>();
        if (idx < 0) return result;
        int from = Math.max(0, idx - k);
        int to = Math.min(all.size() - 1, idx + k);
        for (int i = from; i <= to; i++) result.add(all.get(i));
        return result;
    }
}
