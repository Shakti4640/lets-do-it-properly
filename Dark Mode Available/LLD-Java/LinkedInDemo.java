/*
 * LinkedIn - Low Level Design
 * --------------------------------------------------------------
 * Models a professional networking platform.
 *
 * Key classes:
 *   - User / Profile : a member and their profile (experience, skills).
 *   - Connection     : undirected relationship between two users.
 *   - Post           : content shared by a user; supports likes.
 *   - NewsFeed       : aggregates posts from a user's connections.
 *   - SearchService  : strategy-based search over users.
 *   - LinkedInService: singleton facade orchestrating the platform.
 *
 * Patterns used:
 *   - Singleton (LinkedInService)
 *   - Strategy  (SearchStrategy: by name / by skill)
 *   - Factory   (PostFactory)
 *   - Observer  (ConnectionObserver notified on new posts)
 */

import java.util.*;
import java.util.stream.Collectors;

public class LinkedInDemo {
    public static void main(String[] args) {
        LinkedInService service = LinkedInService.getInstance();

        User alice = service.registerUser("Alice", "alice@x.com");
        User bob = service.registerUser("Bob", "bob@x.com");
        User carol = service.registerUser("Carol", "carol@x.com");

        alice.getProfile().addSkill("Java");
        alice.getProfile().addExperience("Senior Engineer @ Acme");
        bob.getProfile().addSkill("Java");
        bob.getProfile().addSkill("Kubernetes");
        carol.getProfile().addSkill("Product Management");

        service.connect(alice.getId(), bob.getId());
        service.connect(alice.getId(), carol.getId());

        System.out.println("== Connections of Alice ==");
        service.getConnections(alice.getId()).forEach(u -> System.out.println("  - " + u.getName()));

        System.out.println("\n== Posting ==");
        Post p1 = service.createPost(alice.getId(), "Excited to start a new project!");
        Post p2 = service.createPost(bob.getId(), "Learning Kubernetes this week.");
        service.likePost(p1.getId(), bob.getId());
        service.likePost(p1.getId(), carol.getId());

        System.out.println("\n== Alice's News Feed ==");
        service.getNewsFeed(alice.getId()).forEach(p ->
            System.out.println("  [" + p.getAuthorName() + "] " + p.getContent() + " (" + p.getLikeCount() + " likes)"));

        System.out.println("\n== Search by skill 'Java' ==");
        service.searchUsers(new SkillSearch(), "Java").forEach(u -> System.out.println("  - " + u.getName()));

        System.out.println("\n== Search by name 'car' ==");
        service.searchUsers(new NameSearch(), "car").forEach(u -> System.out.println("  - " + u.getName()));
    }
}

class Profile {
    private final List<String> experiences = new ArrayList<>();
    private final Set<String> skills = new LinkedHashSet<>();
    private String headline = "";

    public void addExperience(String e) { experiences.add(e); }
    public void addSkill(String s) { skills.add(s); }
    public boolean hasSkill(String s) {
        return skills.stream().anyMatch(k -> k.equalsIgnoreCase(s));
    }
    public Set<String> getSkills() { return skills; }
    public List<String> getExperiences() { return experiences; }
    public void setHeadline(String h) { this.headline = h; }
    public String getHeadline() { return headline; }
}

class User {
    private final String id;
    private final String name;
    private final String email;
    private final Profile profile = new Profile();

    public User(String id, String name, String email) {
        this.id = id; this.name = name; this.email = email;
    }
    public String getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public Profile getProfile() { return profile; }
}

class Post {
    private final String id;
    private final String authorId;
    private final String authorName;
    private final String content;
    private final long timestamp;
    private final Set<String> likedBy = new HashSet<>();

    public Post(String id, String authorId, String authorName, String content) {
        this.id = id; this.authorId = authorId; this.authorName = authorName;
        this.content = content; this.timestamp = System.nanoTime();
    }
    public String getId() { return id; }
    public String getAuthorId() { return authorId; }
    public String getAuthorName() { return authorName; }
    public String getContent() { return content; }
    public long getTimestamp() { return timestamp; }
    public void like(String userId) { likedBy.add(userId); }
    public int getLikeCount() { return likedBy.size(); }
}

class PostFactory {
    private int counter = 0;
    public Post create(User author, String content) {
        return new Post("post-" + (++counter), author.getId(), author.getName(), content);
    }
}

interface ConnectionObserver {
    void onNewPost(Post post);
}

interface SearchStrategy {
    List<User> search(Collection<User> users, String query);
}

class NameSearch implements SearchStrategy {
    public List<User> search(Collection<User> users, String query) {
        String q = query.toLowerCase();
        return users.stream()
            .filter(u -> u.getName().toLowerCase().contains(q))
            .collect(Collectors.toList());
    }
}

class SkillSearch implements SearchStrategy {
    public List<User> search(Collection<User> users, String query) {
        return users.stream()
            .filter(u -> u.getProfile().hasSkill(query))
            .collect(Collectors.toList());
    }
}

class LinkedInService {
    private static LinkedInService instance;
    private final Map<String, User> users = new LinkedHashMap<>();
    private final Map<String, Set<String>> connections = new HashMap<>();
    private final Map<String, Post> posts = new LinkedHashMap<>();
    private final PostFactory postFactory = new PostFactory();
    private int userCounter = 0;

    private LinkedInService() {}

    public static synchronized LinkedInService getInstance() {
        if (instance == null) instance = new LinkedInService();
        return instance;
    }

    public User registerUser(String name, String email) {
        String id = "user-" + (++userCounter);
        User u = new User(id, name, email);
        users.put(id, u);
        connections.put(id, new HashSet<>());
        return u;
    }

    public void connect(String a, String b) {
        if (!users.containsKey(a) || !users.containsKey(b)) throw new NoSuchElementException("Unknown user");
        connections.get(a).add(b);
        connections.get(b).add(a);
    }

    public List<User> getConnections(String userId) {
        return connections.getOrDefault(userId, Set.of()).stream()
            .map(users::get).collect(Collectors.toList());
    }

    public Post createPost(String authorId, String content) {
        User author = users.get(authorId);
        Post post = postFactory.create(author, content);
        posts.put(post.getId(), post);
        return post;
    }

    public void likePost(String postId, String userId) {
        posts.get(postId).like(userId);
    }

    public List<Post> getNewsFeed(String userId) {
        Set<String> network = connections.getOrDefault(userId, Set.of());
        return posts.values().stream()
            .filter(p -> network.contains(p.getAuthorId()) || p.getAuthorId().equals(userId))
            .sorted(Comparator.comparingLong(Post::getTimestamp).reversed())
            .collect(Collectors.toList());
    }

    public List<User> searchUsers(SearchStrategy strategy, String query) {
        return strategy.search(users.values(), query);
    }
}
