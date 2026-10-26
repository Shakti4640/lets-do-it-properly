/*
 * Web Crawler (LLD focus) - Low Level Design
 * ------------------------------------------
 * Crawls pages starting from seed URLs using BFS, deduplicates visited URLs,
 * respects a max-depth limit and a pluggable URL frontier. Uses an injected
 * page fetcher so it runs deterministically without real network calls.
 *
 * Key classes:
 *   - PageFetcher (+ MockFetcher) : Strategy for retrieving page links
 *   - UrlFrontier                  : queue of URLs to visit (BFS)
 *   - WebCrawler                   : orchestrator with visited set + depth
 *
 * Patterns used: Strategy (fetcher), BFS traversal, dedup set.
 */

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public class WebCrawlerDemo {
    public static void main(String[] args) {
        MockFetcher fetcher = new MockFetcher();
        fetcher.addPage("http://a.com", "http://a.com/1", "http://a.com/2", "http://b.com");
        fetcher.addPage("http://a.com/1", "http://a.com", "http://a.com/3"); // cycle back to a.com
        fetcher.addPage("http://a.com/2", "http://a.com/3");
        fetcher.addPage("http://a.com/3");
        fetcher.addPage("http://b.com", "http://b.com/x");
        fetcher.addPage("http://b.com/x");

        WebCrawler crawler = new WebCrawler(fetcher, 2); // max depth 2
        System.out.println("=== Web Crawler Demo ===");
        crawler.crawl("http://a.com");
        System.out.println("Total pages crawled: " + crawler.getVisited().size());
    }
}

interface PageFetcher {
    List<String> fetchLinks(String url);
}

class MockFetcher implements PageFetcher {
    private final Map<String, List<String>> pages = new HashMap<String, List<String>>();
    void addPage(String url, String... links) {
        List<String> list = new ArrayList<String>();
        for (String l : links) list.add(l);
        pages.put(url, list);
    }
    public List<String> fetchLinks(String url) {
        return pages.getOrDefault(url, new ArrayList<String>());
    }
}

class UrlFrontier {
    private final Queue<String[]> queue = new ArrayDeque<String[]>(); // [url, depth]
    void add(String url, int depth) { queue.offer(new String[]{url, String.valueOf(depth)}); }
    boolean isEmpty() { return queue.isEmpty(); }
    String[] next() { return queue.poll(); }
}

class WebCrawler {
    private final PageFetcher fetcher;
    private final int maxDepth;
    private final Set<String> visited = new HashSet<String>();

    WebCrawler(PageFetcher fetcher, int maxDepth) { this.fetcher = fetcher; this.maxDepth = maxDepth; }
    Set<String> getVisited() { return visited; }

    void crawl(String seed) {
        UrlFrontier frontier = new UrlFrontier();
        frontier.add(seed, 0);
        while (!frontier.isEmpty()) {
            String[] entry = frontier.next();
            String url = entry[0];
            int depth = Integer.parseInt(entry[1]);
            if (visited.contains(url) || depth > maxDepth) continue;
            visited.add(url);
            System.out.println("Crawling [d=" + depth + "] " + url);
            for (String link : fetcher.fetchLinks(url)) {
                if (!visited.contains(link)) frontier.add(link, depth + 1);
            }
        }
    }
}
