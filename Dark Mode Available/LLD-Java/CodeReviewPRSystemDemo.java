/*
 * Code Review / Pull Request System - Low Level Design
 * ----------------------------------------------------
 * Models pull requests with reviewers, comments, and review decisions
 * (approve / request-changes). A PR transitions through states and can only
 * be merged when merge rules are satisfied: at least N approvals and no
 * outstanding "request changes" (blocking) reviews.
 *
 * Key classes / abstractions:
 *   - PullRequest (+ PRState enum), Review (+ ReviewDecision enum), Comment
 *   - MergePolicy (required approvals)
 *   - ReviewService (facade over PR operations)
 *
 * Patterns used: State (PRState), Strategy-ish MergePolicy, Facade (ReviewService).
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CodeReviewPRSystemDemo {
    public static void main(String[] args) {
        System.out.println("=== Code Review / PR System Demo ===");
        ReviewService service = new ReviewService(new MergePolicy(2));

        PullRequest pr = service.openPR("PR-1", "Add caching layer", "alice");
        System.out.println("Opened " + pr.id + " '" + pr.title + "' by " + pr.author + " [" + pr.state + "]");

        service.comment(pr.id, "bob", "Please add a unit test for eviction.");
        System.out.println("\n-- Reviews come in --");
        service.review(pr.id, "bob", ReviewDecision.REQUEST_CHANGES, "Needs tests");
        System.out.println("bob requested changes. mergeable=" + service.canMerge(pr.id));
        tryMerge(service, pr.id);

        // Author pushes a fix; bob re-reviews and approves
        service.review(pr.id, "bob", ReviewDecision.APPROVE, "Tests added, LGTM");
        service.review(pr.id, "carol", ReviewDecision.APPROVE, "LGTM");
        System.out.println("\n-- After updates --");
        System.out.println("approvals=" + service.approvalCount(pr.id)
                + " blocking=" + service.blockingCount(pr.id)
                + " mergeable=" + service.canMerge(pr.id));

        tryMerge(service, pr.id);
        System.out.println("Final state of " + pr.id + ": " + pr.state);
    }

    static void tryMerge(ReviewService service, String prId) {
        try {
            service.merge(prId);
            System.out.println(">> " + prId + " merged successfully.");
        } catch (IllegalStateException e) {
            System.out.println(">> merge rejected: " + e.getMessage());
        }
    }
}

enum PRState { OPEN, MERGED, CLOSED }
enum ReviewDecision { APPROVE, REQUEST_CHANGES, COMMENT }

class Comment {
    final String author;
    final String body;
    Comment(String author, String body) { this.author = author; this.body = body; }
}

class Review {
    final String reviewer;
    ReviewDecision decision;
    String note;
    Review(String reviewer, ReviewDecision decision, String note) {
        this.reviewer = reviewer; this.decision = decision; this.note = note;
    }
}

class PullRequest {
    final String id;
    final String title;
    final String author;
    PRState state = PRState.OPEN;
    final List<Comment> comments = new ArrayList<Comment>();
    // latest decision per reviewer
    final Map<String, Review> reviews = new HashMap<String, Review>();
    PullRequest(String id, String title, String author) {
        this.id = id; this.title = title; this.author = author;
    }
}

class MergePolicy {
    final int requiredApprovals;
    MergePolicy(int requiredApprovals) { this.requiredApprovals = requiredApprovals; }

    boolean satisfied(int approvals, int blocking) {
        return blocking == 0 && approvals >= requiredApprovals;
    }
}

class ReviewService {
    private final MergePolicy policy;
    private final Map<String, PullRequest> prs = new HashMap<String, PullRequest>();
    ReviewService(MergePolicy policy) { this.policy = policy; }

    PullRequest openPR(String id, String title, String author) {
        PullRequest pr = new PullRequest(id, title, author);
        prs.put(id, pr);
        return pr;
    }

    void comment(String prId, String author, String body) {
        require(prId).comments.add(new Comment(author, body));
    }

    void review(String prId, String reviewer, ReviewDecision decision, String note) {
        PullRequest pr = require(prId);
        if (pr.state != PRState.OPEN) throw new IllegalStateException("PR not open");
        if (reviewer.equals(pr.author)) throw new IllegalArgumentException("Author cannot review own PR");
        pr.reviews.put(reviewer, new Review(reviewer, decision, note));
    }

    int approvalCount(String prId) {
        int n = 0;
        for (Review r : require(prId).reviews.values()) if (r.decision == ReviewDecision.APPROVE) n++;
        return n;
    }

    int blockingCount(String prId) {
        int n = 0;
        for (Review r : require(prId).reviews.values()) if (r.decision == ReviewDecision.REQUEST_CHANGES) n++;
        return n;
    }

    boolean canMerge(String prId) {
        PullRequest pr = require(prId);
        return pr.state == PRState.OPEN && policy.satisfied(approvalCount(prId), blockingCount(prId));
    }

    void merge(String prId) {
        PullRequest pr = require(prId);
        if (!canMerge(prId)) {
            throw new IllegalStateException("need " + policy.requiredApprovals + " approvals & no blocking reviews (have "
                    + approvalCount(prId) + " approvals, " + blockingCount(prId) + " blocking)");
        }
        pr.state = PRState.MERGED;
    }

    private PullRequest require(String prId) {
        PullRequest pr = prs.get(prId);
        if (pr == null) throw new IllegalArgumentException("No such PR: " + prId);
        return pr;
    }
}
