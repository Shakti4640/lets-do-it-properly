/*
 * Twitter Feed System - Low Level Design
 * --------------------------------------
 * Users follow/unfollow each other and post tweets. The home timeline is
 * generated via fan-out on read: merge all followees' tweets, sort by
 * timestamp (newest first) and return the top-N.
 *
 * Key classes / abstractions:
 *   - User              : identity + set of followees
 *   - Tweet             : authored content with a logical timestamp
 *   - TwitterService    : follow graph, posting, timeline merge
 *   - TimeProvider      : injected logical clock for deterministic demos
 *
 * Patterns used: Facade (TwitterService), Dependency Injection (TimeProvider).
 */

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TwitterFeedSystemDemo {
    public static void main(String[] args) {
        LogicalClock clock = new LogicalClock();
        TwitterService twitter = new TwitterService(clock);

        twitter.registerUser("alice");
        twitter.registerUser("bob");
        twitter.registerUser("carol");
        twitter.registerUser("dave");

        System.out.println("=== Twitter Feed System Demo ===");

        twitter.follow("alice", "bob");
        twitter.follow("alice", "carol");
        twitter.follow("alice", "dave");

        twitter.postTweet("bob", "Bob: learning system design");
        twitter.postTweet("carol", "Carol: coffee time");
        twitter.postTweet("dave", "Dave: shipped a feature");
        twitter.postTweet("bob", "Bob: second tweet of the day");
        twitter.postTweet("carol", "Carol: reading a book");

        System.out.println("\n--- Follow graph ---");
        twitter.printFollowees("alice");

        System.out.println("\n--- Alice home timeline (top 3) ---");
        for (Tweet t : twitter.getHomeTimeline("alice", 3)) {
            System.out.println(t);
        }

        System.out.println("\n--- Alice unfollows carol ---");
        twitter.unfollow("alice", "carol");
        twitter.postTweet("dave", "Dave: late night deploy");

        System.out.println("\n--- Alice home timeline (top 5) ---");
        for (Tweet t : twitter.getHomeTimeline("alice", 5)) {
            System.out.println(t);
        }
    }
}

class LogicalClock {
    private long current = 0L;
    long tick() { return ++current; }
}

class Tweet {
    private final long id;
    private final String author;
    private final String text;
    private final long timestamp;

    Tweet(long id, String author, String text, long timestamp) {
        this.id = id;
        this.author = author;
        this.text = text;
        this.timestamp = timestamp;
    }

    long getTimestamp() { return timestamp; }

    public String toString() {
        return "[t=" + timestamp + "] @" + author + ": " + text;
    }
}

class User {
    private final String handle;
    private final Set<String> followees = new LinkedHashSet<String>();

    User(String handle) { this.handle = handle; }

    String getHandle() { return handle; }
    void follow(String other) { followees.add(other); }
    void unfollow(String other) { followees.remove(other); }
    Set<String> getFollowees() { return followees; }
}

class TwitterService {
    private final Map<String, User> users = new HashMap<String, User>();
    private final Map<String, List<Tweet>> tweetsByUser = new HashMap<String, List<Tweet>>();
    private final LogicalClock clock;
    private long nextTweetId = 1L;

    TwitterService(LogicalClock clock) { this.clock = clock; }

    void registerUser(String handle) {
        users.put(handle, new User(handle));
        tweetsByUser.put(handle, new ArrayList<Tweet>());
        System.out.println("Registered @" + handle);
    }

    private User require(String handle) {
        User u = users.get(handle);
        if (u == null) throw new IllegalArgumentException("No such user: " + handle);
        return u;
    }

    void follow(String follower, String followee) {
        require(follower); require(followee);
        if (follower.equals(followee)) return;
        require(follower).follow(followee);
        System.out.println("@" + follower + " now follows @" + followee);
    }

    void unfollow(String follower, String followee) {
        require(follower).unfollow(followee);
        System.out.println("@" + follower + " unfollowed @" + followee);
    }

    void postTweet(String author, String text) {
        require(author);
        Tweet tweet = new Tweet(nextTweetId++, author, text, clock.tick());
        tweetsByUser.get(author).add(tweet);
        System.out.println("@" + author + " posted: " + text);
    }

    void printFollowees(String handle) {
        System.out.println("@" + handle + " follows " + require(handle).getFollowees());
    }

    List<Tweet> getHomeTimeline(String handle, int topN) {
        User user = require(handle);
        List<Tweet> merged = new ArrayList<Tweet>();
        for (String followee : user.getFollowees()) {
            List<Tweet> theirTweets = tweetsByUser.get(followee);
            if (theirTweets != null) merged.addAll(theirTweets);
        }
        Collections.sort(merged, new Comparator<Tweet>() {
            public int compare(Tweet a, Tweet b) {
                return Long.compare(b.getTimestamp(), a.getTimestamp());
            }
        });
        if (merged.size() > topN) {
            return new ArrayList<Tweet>(merged.subList(0, topN));
        }
        return merged;
    }
}
