/*
 * Video Streaming (Netflix-like) - Low Level Design
 * --------------------------------------------------------------
 * A content catalog with multiple profiles per account. Each profile keeps a
 * watch history with resume positions, gets simple genre-based
 * recommendations from what it has watched, and the player selects an
 * adaptive bitrate based on the measured bandwidth.
 *
 * Key classes / abstractions:
 *   - Content / Genre   : catalog titles tagged by genre + runtime.
 *   - Profile           : a viewer with its own history + resume points.
 *   - WatchHistory      : resume positions keyed by content id.
 *   - Recommender       : ranks unwatched titles by watched-genre affinity.
 *   - BitrateSelector   : picks a stream quality from bandwidth.
 *   - StreamingService  : facade tying it together.
 *
 * Patterns used:
 *   - Strategy (BitrateSelector by bandwidth tier)
 *   - Facade   (StreamingService)
 */

import java.util.*;
import java.util.stream.Collectors;

public class VideoStreamingNetflixDemo {
    public static void main(String[] args) {
        StreamingService service = new StreamingService();
        service.addContent(new Content("C1", "Stranger Things", Genre.SCI_FI, 50));
        service.addContent(new Content("C2", "Dark", Genre.SCI_FI, 60));
        service.addContent(new Content("C3", "The Crown", Genre.DRAMA, 58));
        service.addContent(new Content("C4", "Narcos", Genre.CRIME, 55));
        service.addContent(new Content("C5", "Black Mirror", Genre.SCI_FI, 62));
        service.addContent(new Content("C6", "Money Heist", Genre.CRIME, 47));

        Profile alice = service.createProfile("Alice");

        System.out.println("=== Alice watches some sci-fi + crime ===");
        service.watch(alice, "C1", 50); // finished
        service.watch(alice, "C2", 35); // paused mid-episode
        service.watch(alice, "C4", 55); // finished

        System.out.println("\n=== Watch history (with resume points) ===");
        service.printHistory(alice);

        System.out.println("\n=== Resume a partially watched title ===");
        service.resume(alice, "C2");

        System.out.println("\n=== Recommendations (by watched-genre affinity) ===");
        List<Content> recs = service.recommend(alice, 3);
        for (Content c : recs) System.out.println("  " + c);

        System.out.println("\n=== Adaptive bitrate selection ===");
        BitrateSelector selector = new BitrateSelector();
        int[] bandwidths = {800, 2500, 6000, 18000};
        for (int bw : bandwidths) {
            System.out.println("  " + bw + " kbps -> " + selector.select(bw));
        }
    }
}

enum Genre { SCI_FI, DRAMA, CRIME, COMEDY }

enum Quality {
    SD_480("480p", 1000),
    HD_720("720p", 2500),
    FHD_1080("1080p", 5000),
    UHD_4K("4K", 15000);

    private final String label;
    private final int minKbps;

    Quality(String label, int minKbps) { this.label = label; this.minKbps = minKbps; }
    public String getLabel() { return label; }
    public int getMinKbps() { return minKbps; }
}

class Content {
    private final String id;
    private final String title;
    private final Genre genre;
    private final int runtimeMin;

    public Content(String id, String title, Genre genre, int runtimeMin) {
        this.id = id;
        this.title = title;
        this.genre = genre;
        this.runtimeMin = runtimeMin;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public Genre getGenre() { return genre; }

    @Override
    public String toString() {
        return id + " " + title + " [" + genre + "]";
    }
}

class WatchHistory {
    // contentId -> resume position in minutes
    private final Map<String, Integer> resumePoints = new LinkedHashMap<>();

    public void record(String contentId, int positionMin) {
        resumePoints.put(contentId, positionMin);
    }

    public Integer resumeOf(String contentId) { return resumePoints.get(contentId); }
    public Set<String> watchedIds() { return resumePoints.keySet(); }
    public Map<String, Integer> all() { return resumePoints; }
}

class Profile {
    private final String name;
    private final WatchHistory history = new WatchHistory();

    public Profile(String name) { this.name = name; }
    public String getName() { return name; }
    public WatchHistory getHistory() { return history; }
}

class Recommender {
    public List<Content> recommend(Profile profile, Collection<Content> catalog, int limit) {
        WatchHistory h = profile.getHistory();
        // Build genre affinity from watched content.
        Map<Genre, Integer> affinity = new EnumMap<>(Genre.class);
        Map<String, Content> byId = new HashMap<>();
        for (Content c : catalog) byId.put(c.getId(), c);
        for (String id : h.watchedIds()) {
            Content c = byId.get(id);
            if (c != null) affinity.merge(c.getGenre(), 1, Integer::sum);
        }
        // Rank unwatched titles by affinity score of their genre.
        return catalog.stream()
                .filter(c -> !h.watchedIds().contains(c.getId()))
                .sorted((a, b) -> Integer.compare(
                        affinity.getOrDefault(b.getGenre(), 0),
                        affinity.getOrDefault(a.getGenre(), 0)))
                .limit(limit)
                .collect(Collectors.toList());
    }
}

class BitrateSelector {
    public Quality select(int bandwidthKbps) {
        Quality chosen = Quality.SD_480;
        for (Quality q : Quality.values()) {
            if (bandwidthKbps >= q.getMinKbps()) chosen = q;
        }
        return chosen;
    }
}

class StreamingService {
    private final Map<String, Content> catalog = new LinkedHashMap<>();
    private final List<Profile> profiles = new ArrayList<>();
    private final Recommender recommender = new Recommender();

    public void addContent(Content c) { catalog.put(c.getId(), c); }

    public Profile createProfile(String name) {
        Profile p = new Profile(name);
        profiles.add(p);
        System.out.println("Created profile " + name);
        return p;
    }

    public void watch(Profile profile, String contentId, int positionMin) {
        Content c = catalog.get(contentId);
        if (c == null) { System.out.println("  Unknown content " + contentId); return; }
        profile.getHistory().record(contentId, positionMin);
        System.out.println("  " + profile.getName() + " watched " + c.getTitle() + " up to " + positionMin + "min");
    }

    public void resume(Profile profile, String contentId) {
        Integer pos = profile.getHistory().resumeOf(contentId);
        Content c = catalog.get(contentId);
        if (pos == null || c == null) {
            System.out.println("  Nothing to resume for " + contentId);
            return;
        }
        System.out.println("  Resuming " + c.getTitle() + " from " + pos + "min");
    }

    public List<Content> recommend(Profile profile, int limit) {
        return recommender.recommend(profile, catalog.values(), limit);
    }

    public void printHistory(Profile profile) {
        for (Map.Entry<String, Integer> e : profile.getHistory().all().entrySet()) {
            Content c = catalog.get(e.getKey());
            System.out.println("  " + c.getTitle() + " @ " + e.getValue() + "min");
        }
    }
}
