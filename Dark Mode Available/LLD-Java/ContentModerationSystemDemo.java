/*
 * Content Moderation System - Low Level Design
 * --------------------------------------------
 * Content is passed through a chain of pluggable moderation rules. Each rule
 * inspects the content and may downgrade the verdict (APPROVED -> FLAGGED ->
 * REJECTED). Rejected/flagged items land in a moderation queue for review.
 *
 * Key classes / abstractions:
 *   - ModerationRule (+ BannedWords/SpamLink/AllCaps/Length) : Strategy
 *   - ModerationPipeline : Chain-of-Responsibility runner
 *   - Verdict / ModerationResult
 *   - ModerationQueue    : holds non-approved content for human review
 *
 * Patterns used: Strategy, Chain-of-Responsibility, Builder (pipeline).
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ContentModerationSystemDemo {
    public static void main(String[] args) {
        ModerationPipeline pipeline = new ModerationPipeline()
                .addRule(new BannedWordsRule(Arrays.asList("scam", "hate")))
                .addRule(new SpamLinkRule())
                .addRule(new AllCapsRule())
                .addRule(new MaxLengthRule(120));

        ModerationQueue queue = new ModerationQueue();

        System.out.println("=== Content Moderation System Demo ===");

        String[] posts = {
            "Loving the new release, great work team!",
            "Cheap meds visit http://spam.example.com now",
            "This is a total scam, avoid it",
            "WHY IS EVERYONE YELLING ALL THE TIME",
            "A perfectly normal comment about cats."
        };

        for (int i = 0; i < posts.length; i++) {
            Content content = new Content(i + 1, "user" + (i + 1), posts[i]);
            ModerationResult result = pipeline.evaluate(content);
            System.out.println("\nContent #" + content.id + " by " + content.author);
            System.out.println("  text: \"" + content.text + "\"");
            System.out.println("  verdict: " + result.verdict + " reasons=" + result.reasons);
            if (result.verdict != Verdict.APPROVED) {
                queue.enqueue(content, result);
            }
        }

        System.out.println("\n--- Moderation Queue ---");
        queue.printQueue();
    }
}

enum Verdict {
    APPROVED(0), FLAGGED(1), REJECTED(2);
    final int severity;
    Verdict(int severity) { this.severity = severity; }
    static Verdict worst(Verdict a, Verdict b) {
        return a.severity >= b.severity ? a : b;
    }
}

class Content {
    final int id;
    final String author;
    final String text;
    Content(int id, String author, String text) {
        this.id = id;
        this.author = author;
        this.text = text;
    }
}

class ModerationResult {
    Verdict verdict = Verdict.APPROVED;
    final List<String> reasons = new ArrayList<String>();

    void record(Verdict v, String reason) {
        verdict = Verdict.worst(verdict, v);
        reasons.add(reason);
    }
}

interface ModerationRule {
    void inspect(Content content, ModerationResult result);
    String name();
}

class BannedWordsRule implements ModerationRule {
    private final List<String> bannedWords;
    BannedWordsRule(List<String> bannedWords) { this.bannedWords = bannedWords; }
    public void inspect(Content content, ModerationResult result) {
        String lower = content.text.toLowerCase();
        for (String word : bannedWords) {
            if (lower.contains(word)) {
                result.record(Verdict.REJECTED, "banned word: " + word);
            }
        }
    }
    public String name() { return "BannedWordsRule"; }
}

class SpamLinkRule implements ModerationRule {
    public void inspect(Content content, ModerationResult result) {
        String lower = content.text.toLowerCase();
        if (lower.contains("http://") || lower.contains("https://")) {
            result.record(Verdict.FLAGGED, "contains link");
        }
    }
    public String name() { return "SpamLinkRule"; }
}

class AllCapsRule implements ModerationRule {
    public void inspect(Content content, ModerationResult result) {
        int letters = 0;
        int upper = 0;
        for (int i = 0; i < content.text.length(); i++) {
            char c = content.text.charAt(i);
            if (Character.isLetter(c)) {
                letters++;
                if (Character.isUpperCase(c)) upper++;
            }
        }
        if (letters >= 8 && upper == letters) {
            result.record(Verdict.FLAGGED, "all caps shouting");
        }
    }
    public String name() { return "AllCapsRule"; }
}

class MaxLengthRule implements ModerationRule {
    private final int maxLength;
    MaxLengthRule(int maxLength) { this.maxLength = maxLength; }
    public void inspect(Content content, ModerationResult result) {
        if (content.text.length() > maxLength) {
            result.record(Verdict.FLAGGED, "exceeds " + maxLength + " chars");
        }
    }
    public String name() { return "MaxLengthRule"; }
}

class ModerationPipeline {
    private final List<ModerationRule> rules = new ArrayList<ModerationRule>();

    ModerationPipeline addRule(ModerationRule rule) {
        rules.add(rule);
        return this;
    }

    ModerationResult evaluate(Content content) {
        ModerationResult result = new ModerationResult();
        for (ModerationRule rule : rules) {
            rule.inspect(content, result);
        }
        return result;
    }
}

class QueueEntry {
    final Content content;
    final ModerationResult result;
    QueueEntry(Content content, ModerationResult result) {
        this.content = content;
        this.result = result;
    }
}

class ModerationQueue {
    private final List<QueueEntry> entries = new ArrayList<QueueEntry>();

    void enqueue(Content content, ModerationResult result) {
        entries.add(new QueueEntry(content, result));
    }

    void printQueue() {
        if (entries.isEmpty()) {
            System.out.println("(empty)");
            return;
        }
        for (QueueEntry e : entries) {
            System.out.println(e.result.verdict + " #" + e.content.id
                    + " by " + e.content.author + " -> " + e.result.reasons);
        }
    }
}
