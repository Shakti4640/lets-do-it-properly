/*
 * Community Q&A Platform - Low Level Design
 * -----------------------------------------
 * A StackOverflow-style platform: users post questions (with tags), submit
 * answers, cast up/down votes, and accept an answer. Reputation is awarded
 * based on votes and acceptance.
 *
 * Key classes / abstractions:
 *   - User      : carries a reputation score
 *   - Post (+ Question/Answer) : votable content
 *   - Question  : holds answers, tags, accepted answer
 *   - QAService : facade orchestrating posting, voting, acceptance, reputation
 *
 * Patterns used: Template-ish inheritance (Post), Facade (QAService).
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CommunityQAPlatformDemo {
    // Reputation rules.
    static final int REP_QUESTION_UPVOTE = 5;
    static final int REP_ANSWER_UPVOTE = 10;
    static final int REP_DOWNVOTE = -2;
    static final int REP_ACCEPTED = 15;

    public static void main(String[] args) {
        QAService qa = new QAService();
        qa.register("alice");
        qa.register("bob");
        qa.register("carol");

        System.out.println("=== Community Q&A Platform Demo ===");

        long q1 = qa.askQuestion("alice", "How does HashMap resize?",
                "Curious about the load factor.", new String[]{"java", "collections"});
        long a1 = qa.answerQuestion("bob", q1, "It doubles capacity at load factor 0.75.");
        long a2 = qa.answerQuestion("carol", q1, "Buckets are rehashed on resize.");

        qa.upvoteQuestion("bob", q1);
        qa.upvoteQuestion("carol", q1);
        qa.upvoteAnswer("alice", a1);
        qa.upvoteAnswer("carol", a1);
        qa.downvoteAnswer("alice", a2);

        qa.acceptAnswer("alice", q1, a1);

        System.out.println("\n--- Question thread ---");
        qa.printQuestion(q1);

        System.out.println("\n--- Reputation ---");
        qa.printReputation();

        System.out.println("\n--- Search by tag: java ---");
        for (Long id : qa.findByTag("java")) {
            System.out.println("Q" + id + ": " + qa.titleOf(id));
        }
    }
}

class User {
    final String name;
    int reputation = 0;
    User(String name) { this.name = name; }
}

abstract class Post {
    final long id;
    final String author;
    final String body;
    int upvotes = 0;
    int downvotes = 0;
    Post(long id, String author, String body) {
        this.id = id;
        this.author = author;
        this.body = body;
    }
    int score() { return upvotes - downvotes; }
}

class Answer extends Post {
    boolean accepted = false;
    Answer(long id, String author, String body) { super(id, author, body); }
}

class Question extends Post {
    final String title;
    final Set<String> tags = new LinkedHashSet<String>();
    final List<Answer> answers = new ArrayList<Answer>();
    Long acceptedAnswerId = null;
    Question(long id, String author, String title, String body, String[] tags) {
        super(id, author, body);
        this.title = title;
        for (String t : tags) this.tags.add(t);
    }
}

class QAService {
    private final Map<String, User> users = new HashMap<String, User>();
    private final Map<Long, Question> questions = new HashMap<Long, Question>();
    private final Map<Long, Answer> answers = new HashMap<Long, Answer>();
    private final Map<Long, Long> answerToQuestion = new HashMap<Long, Long>();
    private long nextId = 1L;

    void register(String name) {
        users.put(name, new User(name));
        System.out.println("Registered " + name);
    }

    private User require(String name) {
        User u = users.get(name);
        if (u == null) throw new IllegalArgumentException("No such user: " + name);
        return u;
    }

    long askQuestion(String author, String title, String body, String[] tags) {
        require(author);
        long id = nextId++;
        questions.put(id, new Question(id, author, title, body, tags));
        System.out.println(author + " asked Q" + id + ": " + title);
        return id;
    }

    long answerQuestion(String author, long questionId, String body) {
        require(author);
        Question q = requireQuestion(questionId);
        long id = nextId++;
        Answer a = new Answer(id, author, body);
        answers.put(id, a);
        answerToQuestion.put(id, questionId);
        q.answers.add(a);
        System.out.println(author + " answered Q" + questionId + " with A" + id);
        return id;
    }

    private Question requireQuestion(long id) {
        Question q = questions.get(id);
        if (q == null) throw new IllegalArgumentException("No such question: " + id);
        return q;
    }

    private Answer requireAnswer(long id) {
        Answer a = answers.get(id);
        if (a == null) throw new IllegalArgumentException("No such answer: " + id);
        return a;
    }

    void upvoteQuestion(String voter, long questionId) {
        require(voter);
        Question q = requireQuestion(questionId);
        q.upvotes++;
        require(q.author).reputation += CommunityQAPlatformDemo.REP_QUESTION_UPVOTE;
        System.out.println(voter + " upvoted Q" + questionId);
    }

    void upvoteAnswer(String voter, long answerId) {
        require(voter);
        Answer a = requireAnswer(answerId);
        a.upvotes++;
        require(a.author).reputation += CommunityQAPlatformDemo.REP_ANSWER_UPVOTE;
        System.out.println(voter + " upvoted A" + answerId);
    }

    void downvoteAnswer(String voter, long answerId) {
        require(voter);
        Answer a = requireAnswer(answerId);
        a.downvotes++;
        require(a.author).reputation += CommunityQAPlatformDemo.REP_DOWNVOTE;
        System.out.println(voter + " downvoted A" + answerId);
    }

    void acceptAnswer(String asker, long questionId, long answerId) {
        Question q = requireQuestion(questionId);
        if (!q.author.equals(asker)) {
            System.out.println("Only the asker (" + q.author + ") can accept an answer");
            return;
        }
        Answer a = requireAnswer(answerId);
        a.accepted = true;
        q.acceptedAnswerId = answerId;
        require(a.author).reputation += CommunityQAPlatformDemo.REP_ACCEPTED;
        System.out.println(asker + " accepted A" + answerId + " for Q" + questionId);
    }

    List<Long> findByTag(String tag) {
        List<Long> result = new ArrayList<Long>();
        for (Question q : questions.values()) {
            if (q.tags.contains(tag)) result.add(q.id);
        }
        return result;
    }

    String titleOf(long questionId) { return requireQuestion(questionId).title; }

    void printQuestion(long questionId) {
        Question q = requireQuestion(questionId);
        System.out.println("Q" + q.id + " [" + q.tags + "] by " + q.author
                + " (score " + q.score() + ")");
        System.out.println("  " + q.title + " - " + q.body);
        for (Answer a : q.answers) {
            String mark = a.accepted ? " [ACCEPTED]" : "";
            System.out.println("  A" + a.id + " by " + a.author
                    + " (score " + a.score() + ")" + mark + ": " + a.body);
        }
    }

    void printReputation() {
        Set<String> seen = new HashSet<String>();
        for (User u : users.values()) {
            if (seen.add(u.name)) {
                System.out.println(u.name + ": " + u.reputation + " rep");
            }
        }
    }
}
