/*
 * Job Portal / Recruitment System - Low Level Design
 * --------------------------------------------------
 * Recruiters post jobs with required skills; candidates with skill sets apply.
 * A skill-match score ranks applicants, recruiters shortlist by a threshold,
 * and each application moves through a state machine
 * (APPLIED -> SHORTLISTED -> INTERVIEW -> OFFER / REJECTED).
 *
 * Key classes / abstractions:
 *   - JobPosting, Candidate
 *   - Application (+ AppStatus enum, state-transition rules)
 *   - MatchScorer (skill-overlap scoring strategy)
 *   - JobPortal (facade)
 *
 * Patterns used: State (AppStatus transitions), Strategy (MatchScorer), Facade (JobPortal).
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JobPortalRecruitmentSystemDemo {
    public static void main(String[] args) {
        System.out.println("=== Job Portal / Recruitment System Demo ===");
        JobPortal portal = new JobPortal(new SkillMatchScorer());

        JobPosting job = portal.postJob("J1", "Backend Engineer",
                Arrays.asList("java", "sql", "aws", "rest"));
        System.out.println("Posted '" + job.title + "' requiring " + job.requiredSkills);

        portal.addCandidate(new Candidate("alice", Arrays.asList("java", "sql", "aws", "rest", "docker")));
        portal.addCandidate(new Candidate("bob", Arrays.asList("java", "sql")));
        portal.addCandidate(new Candidate("carol", Arrays.asList("python", "ml")));

        System.out.println("\n-- Applications --");
        portal.apply("J1", "alice");
        portal.apply("J1", "bob");
        portal.apply("J1", "carol");
        for (Application a : portal.applicationsFor("J1")) {
            System.out.println("  " + a.candidateId + " match=" + portal.scoreOf("J1", a.candidateId)
                    + "% status=" + a.status);
        }

        System.out.println("\n-- Recruiter shortlists (threshold 50%) --");
        portal.shortlistByThreshold("J1", 50);
        for (Application a : portal.applicationsFor("J1")) {
            System.out.println("  " + a.candidateId + " -> " + a.status);
        }

        System.out.println("\n-- Progress alice through pipeline --");
        portal.advance("J1", "alice", AppStatus.INTERVIEW);
        portal.advance("J1", "alice", AppStatus.OFFER);
        System.out.println("  alice final: " + portal.statusOf("J1", "alice"));

        System.out.println("\n-- Invalid transition attempt (carol REJECTED then OFFER) --");
        portal.advance("J1", "carol", AppStatus.REJECTED);
        try {
            portal.advance("J1", "carol", AppStatus.OFFER);
        } catch (IllegalStateException e) {
            System.out.println("  blocked: " + e.getMessage());
        }
        System.out.println("  carol final: " + portal.statusOf("J1", "carol"));
    }
}

enum AppStatus {
    APPLIED, SHORTLISTED, INTERVIEW, OFFER, REJECTED;

    // Allowed forward transitions; REJECTED reachable from any non-terminal state.
    boolean canTransitionTo(AppStatus next) {
        if (this == OFFER || this == REJECTED) return false; // terminal
        if (next == REJECTED) return true;
        switch (this) {
            case APPLIED:     return next == SHORTLISTED;
            case SHORTLISTED: return next == INTERVIEW;
            case INTERVIEW:   return next == OFFER;
            default:          return false;
        }
    }
}

class JobPosting {
    final String id;
    final String title;
    final List<String> requiredSkills;
    JobPosting(String id, String title, List<String> requiredSkills) {
        this.id = id; this.title = title; this.requiredSkills = requiredSkills;
    }
}

class Candidate {
    final String id;
    final Set<String> skills;
    Candidate(String id, List<String> skills) { this.id = id; this.skills = new HashSet<String>(skills); }
}

class Application {
    final String jobId;
    final String candidateId;
    AppStatus status = AppStatus.APPLIED;
    Application(String jobId, String candidateId) { this.jobId = jobId; this.candidateId = candidateId; }
    void moveTo(AppStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException("cannot move from " + status + " to " + next);
        }
        status = next;
    }
}

interface MatchScorer {
    int score(JobPosting job, Candidate candidate);
}

// Percentage of required skills the candidate possesses.
class SkillMatchScorer implements MatchScorer {
    public int score(JobPosting job, Candidate candidate) {
        if (job.requiredSkills.isEmpty()) return 0;
        int have = 0;
        for (String skill : job.requiredSkills) if (candidate.skills.contains(skill)) have++;
        return (have * 100) / job.requiredSkills.size();
    }
}

class JobPortal {
    private final MatchScorer scorer;
    private final Map<String, JobPosting> jobs = new HashMap<String, JobPosting>();
    private final Map<String, Candidate> candidates = new HashMap<String, Candidate>();
    // jobId -> (candidateId -> Application)
    private final Map<String, Map<String, Application>> applications = new HashMap<String, Map<String, Application>>();

    JobPortal(MatchScorer scorer) { this.scorer = scorer; }

    JobPosting postJob(String id, String title, List<String> requiredSkills) {
        JobPosting job = new JobPosting(id, title, requiredSkills);
        jobs.put(id, job);
        applications.put(id, new HashMap<String, Application>());
        return job;
    }

    void addCandidate(Candidate c) { candidates.put(c.id, c); }

    void apply(String jobId, String candidateId) {
        requireJob(jobId);
        if (!candidates.containsKey(candidateId)) throw new IllegalArgumentException("No such candidate");
        applications.get(jobId).put(candidateId, new Application(jobId, candidateId));
    }

    int scoreOf(String jobId, String candidateId) {
        return scorer.score(requireJob(jobId), candidates.get(candidateId));
    }

    void shortlistByThreshold(String jobId, int threshold) {
        JobPosting job = requireJob(jobId);
        for (Application a : applications.get(jobId).values()) {
            if (a.status != AppStatus.APPLIED) continue;
            int score = scorer.score(job, candidates.get(a.candidateId));
            a.moveTo(score >= threshold ? AppStatus.SHORTLISTED : AppStatus.REJECTED);
        }
    }

    void advance(String jobId, String candidateId, AppStatus next) {
        application(jobId, candidateId).moveTo(next);
    }

    AppStatus statusOf(String jobId, String candidateId) {
        return application(jobId, candidateId).status;
    }

    List<Application> applicationsFor(String jobId) {
        requireJob(jobId);
        return new ArrayList<Application>(applications.get(jobId).values());
    }

    private Application application(String jobId, String candidateId) {
        requireJob(jobId);
        Application a = applications.get(jobId).get(candidateId);
        if (a == null) throw new IllegalArgumentException("No application for " + candidateId);
        return a;
    }

    private JobPosting requireJob(String jobId) {
        JobPosting job = jobs.get(jobId);
        if (job == null) throw new IllegalArgumentException("No such job: " + jobId);
        return job;
    }
}
