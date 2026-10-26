/*
 * Instagram Photo Sharing - Low Level Design
 * ------------------------------------------
 * Users upload photos with captions, follow other users, and like/comment
 * on photos. A user's feed is the photos from accounts they follow, sorted
 * newest-first.
 *
 * Key classes / abstractions:
 *   - User              : identity + followees
 *   - Photo             : image ref, caption, likes, comments
 *   - Comment           : author + text
 *   - InstagramService  : upload, social graph, engagement, feed
 *   - LogicalClock      : injected ordering for deterministic demos
 *
 * Patterns used: Facade (InstagramService), Dependency Injection (clock).
 */

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class InstagramPhotoSharingDemo {
    public static void main(String[] args) {
        LogicalClock clock = new LogicalClock();
        InstagramService ig = new InstagramService(clock);

        ig.register("alice");
        ig.register("bob");
        ig.register("carol");

        System.out.println("=== Instagram Photo Sharing Demo ===");

        ig.follow("alice", "bob");
        ig.follow("alice", "carol");

        long p1 = ig.uploadPhoto("bob", "beach.jpg", "Sunset at the beach");
        long p2 = ig.uploadPhoto("carol", "cake.jpg", "Birthday cake!");
        long p3 = ig.uploadPhoto("bob", "dog.jpg", "My new puppy");

        ig.like(p1, "alice");
        ig.like(p1, "carol");
        ig.comment(p1, "alice", "Gorgeous!");
        ig.like(p2, "alice");
        ig.comment(p3, "carol", "So cute");

        System.out.println("\n--- Alice's feed ---");
        for (Photo photo : ig.getFeed("alice")) {
            System.out.println(photo.summary());
        }

        System.out.println("\n--- Photo detail (beach.jpg) ---");
        ig.printPhotoDetail(p1);
    }
}

class LogicalClock {
    private long current = 0L;
    long tick() { return ++current; }
}

class Comment {
    final String author;
    final String text;
    Comment(String author, String text) { this.author = author; this.text = text; }
    public String toString() { return "@" + author + ": " + text; }
}

class Photo {
    private final long id;
    private final String owner;
    private final String imageRef;
    private final String caption;
    private final long timestamp;
    private final Set<String> likes = new LinkedHashSet<String>();
    private final List<Comment> comments = new ArrayList<Comment>();

    Photo(long id, String owner, String imageRef, String caption, long timestamp) {
        this.id = id;
        this.owner = owner;
        this.imageRef = imageRef;
        this.caption = caption;
        this.timestamp = timestamp;
    }

    long getId() { return id; }
    long getTimestamp() { return timestamp; }
    void addLike(String user) { likes.add(user); }
    void addComment(Comment c) { comments.add(c); }

    String summary() {
        return "[t=" + timestamp + "] @" + owner + " " + imageRef
                + " \"" + caption + "\" likes=" + likes.size()
                + " comments=" + comments.size();
    }

    void printDetail() {
        System.out.println("Photo " + imageRef + " by @" + owner);
        System.out.println("  Caption: " + caption);
        System.out.println("  Likes (" + likes.size() + "): " + likes);
        System.out.println("  Comments (" + comments.size() + "):");
        for (Comment c : comments) System.out.println("    " + c);
    }
}

class User {
    private final String handle;
    private final Set<String> following = new LinkedHashSet<String>();
    User(String handle) { this.handle = handle; }
    String getHandle() { return handle; }
    void follow(String other) { following.add(other); }
    Set<String> getFollowing() { return following; }
}

class InstagramService {
    private final Map<String, User> users = new HashMap<String, User>();
    private final Map<Long, Photo> photos = new HashMap<Long, Photo>();
    private final Map<String, List<Photo>> photosByUser = new HashMap<String, List<Photo>>();
    private final LogicalClock clock;
    private long nextPhotoId = 1L;

    InstagramService(LogicalClock clock) { this.clock = clock; }

    void register(String handle) {
        users.put(handle, new User(handle));
        photosByUser.put(handle, new ArrayList<Photo>());
        System.out.println("Registered @" + handle);
    }

    private User require(String handle) {
        User u = users.get(handle);
        if (u == null) throw new IllegalArgumentException("No such user: " + handle);
        return u;
    }

    void follow(String follower, String followee) {
        require(follower); require(followee);
        require(follower).follow(followee);
        System.out.println("@" + follower + " follows @" + followee);
    }

    long uploadPhoto(String owner, String imageRef, String caption) {
        require(owner);
        long id = nextPhotoId++;
        Photo photo = new Photo(id, owner, imageRef, caption, clock.tick());
        photos.put(id, photo);
        photosByUser.get(owner).add(photo);
        System.out.println("@" + owner + " uploaded " + imageRef);
        return id;
    }

    private Photo requirePhoto(long id) {
        Photo p = photos.get(id);
        if (p == null) throw new IllegalArgumentException("No such photo: " + id);
        return p;
    }

    void like(long photoId, String user) {
        require(user);
        requirePhoto(photoId).addLike(user);
        System.out.println("@" + user + " liked photo " + photoId);
    }

    void comment(long photoId, String user, String text) {
        require(user);
        requirePhoto(photoId).addComment(new Comment(user, text));
        System.out.println("@" + user + " commented on photo " + photoId);
    }

    List<Photo> getFeed(String handle) {
        User user = require(handle);
        List<Photo> feed = new ArrayList<Photo>();
        for (String followee : user.getFollowing()) {
            feed.addAll(photosByUser.get(followee));
        }
        Collections.sort(feed, new Comparator<Photo>() {
            public int compare(Photo a, Photo b) {
                return Long.compare(b.getTimestamp(), a.getTimestamp());
            }
        });
        return feed;
    }

    void printPhotoDetail(long photoId) {
        requirePhoto(photoId).printDetail();
    }
}
