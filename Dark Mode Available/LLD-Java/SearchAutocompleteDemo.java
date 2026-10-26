/*
 * Search Autocomplete System - Low Level Design
 * ---------------------------------------------
 * Trie-based prefix search returning top-K suggestions ranked by frequency.
 *
 * Key classes:
 *   - TrieNode
 *   - AutocompleteSystem (insert with weight, query prefix -> top K)
 *
 * Patterns used: Trie data structure, frequency ranking.
 */

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SearchAutocompleteDemo {
    public static void main(String[] args) {
        AutocompleteSystem ac = new AutocompleteSystem();
        ac.add("apple", 50);
        ac.add("app", 30);
        ac.add("application", 20);
        ac.add("apply", 40);
        ac.add("banana", 10);
        ac.add("band", 25);

        System.out.println("=== Search Autocomplete Demo ===");
        System.out.println("'app' -> " + ac.suggest("app", 3));
        System.out.println("'ban' -> " + ac.suggest("ban", 5));
        System.out.println("'xyz' -> " + ac.suggest("xyz", 3));

        ac.add("app", 100); // bump frequency
        System.out.println("After bump, 'app' -> " + ac.suggest("app", 3));
    }
}

class TrieNode {
    final Map<Character, TrieNode> children = new HashMap<Character, TrieNode>();
    boolean isWord = false;
    int frequency = 0;
    String word = null;
}

class AutocompleteSystem {
    private final TrieNode root = new TrieNode();

    void add(String word, int weight) {
        TrieNode node = root;
        for (char c : word.toCharArray()) {
            node = node.children.computeIfAbsent(c, k -> new TrieNode());
        }
        node.isWord = true;
        node.frequency += weight;
        node.word = word;
    }

    List<String> suggest(String prefix, int k) {
        TrieNode node = root;
        for (char c : prefix.toCharArray()) {
            node = node.children.get(c);
            if (node == null) return new ArrayList<String>();
        }
        List<TrieNode> matches = new ArrayList<TrieNode>();
        collect(node, matches);
        matches.sort(new Comparator<TrieNode>() {
            public int compare(TrieNode a, TrieNode b) { return Integer.compare(b.frequency, a.frequency); }
        });
        List<String> result = new ArrayList<String>();
        for (int i = 0; i < Math.min(k, matches.size()); i++) {
            result.add(matches.get(i).word + "(" + matches.get(i).frequency + ")");
        }
        return result;
    }

    private void collect(TrieNode node, List<TrieNode> out) {
        if (node.isWord) out.add(node);
        for (TrieNode child : node.children.values()) collect(child, out);
    }
}
