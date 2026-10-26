/*
 * Community Discussion Platform (Reddit-like) - Low Level Design
 * --------------------------------------------------------------
 * Subreddits with posts, nested comments (Composite), and voting that
 * ranks posts by score.
 *
 * Key classes:
 *   - User
 *   - Votable (abstract) -> Post, Comment   : shared up/down voting
 *   - Comment (nested via Composite)
 *   - Community / Platform (facade)
 *
 * Patterns used: Composite (comments), Template (votable), Facade.
 */

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommunityDiscussionDemo {
    public static void main(String[] args) {
        Platform platform = new Platform();
        platform.createCommunity("java");
        User alice = new User("alice");
        User bob = new User("bob");

        System.out.println("=== Community Discussion Demo ===");
        Post p1 = platform.post("java", alice, "Records in Java 16", "They are great");
        Post p2 = platform.post("java", bob, "Why streams are slow", "Discuss");

        p1.upvote(); p1.upvote(); p1.upvote();
        p2.upvote(); p2.downvote();

        Comment c1 = platform.comment(p1, bob, "Totally agree!");
        Comment c2 = platform.reply(c1, alice, "Thanks Bob");
        c1.upvote();

        platform.showHot("java");
        System.out.println("--- Post 1 thread ---");
        p1.printThread();
    }
}

class User {
    final String name;
    User(String name) { this.name = name; }
}

abstract class Votable {
    protected int upvotes = 0;
    protected int downvotes = 0;
    void upvote() { upvotes++; }
    void downvote() { downvotes++; }
    int score() { return upvotes - downvotes; }
}

class Comment extends Votable {
    final User author; final String text;
    final List<Comment> replies = new ArrayList<Comment>();
    Comment(User author, String text) { this.author = author; this.text = text; }
    void addReply(Comment c) { replies.add(c); }
    void print(int depth) {
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < depth; i++) indent.append("  ");
        System.out.println(indent + author.name + ": " + text + " (" + score() + ")");
        for (Comment r : replies) r.print(depth + 1);
    }
}

class Post extends Votable {
    final User author; final String title; final String body;
    final List<Comment> comments = new ArrayList<Comment>();
    Post(User author, String title, String body) { this.author = author; this.title = title; this.body = body; }
    void addComment(Comment c) { comments.add(c); }
    void printThread() {
        System.out.println("[" + score() + "] " + title + " by " + author.name);
        for (Comment c : comments) c.print(1);
    }
}

class Community {
    final String name;
    final List<Post> posts = new ArrayList<Post>();
    Community(String name) { this.name = name; }
}

class Platform {
    private final Map<String, Community> communities = new HashMap<String, Community>();

    void createCommunity(String name) {
        communities.put(name, new Community(name));
        System.out.println("Created r/" + name);
    }

    Post post(String community, User author, String title, String body) {
        Post p = new Post(author, title, body);
        communities.get(community).posts.add(p);
        System.out.println(author.name + " posted '" + title + "' in r/" + community);
        return p;
    }

    Comment comment(Post post, User author, String text) {
        Comment c = new Comment(author, text);
        post.addComment(c);
        return c;
    }

    Comment reply(Comment parent, User author, String text) {
        Comment c = new Comment(author, text);
        parent.addReply(c);
        return c;
    }

    void showHot(String community) {
        List<Post> posts = new ArrayList<Post>(communities.get(community).posts);
        posts.sort(new Comparator<Post>() {
            public int compare(Post a, Post b) { return Integer.compare(b.score(), a.score()); }
        });
        System.out.println("--- Hot in r/" + community + " ---");
        for (Post p : posts) System.out.println("  [" + p.score() + "] " + p.title);
    }
}
