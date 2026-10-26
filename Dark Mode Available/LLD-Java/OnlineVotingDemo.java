/*
 * Online Voting System - Low Level Design
 * ---------------------------------------
 * Conducts elections: register voters & candidates, cast one vote per voter
 * (idempotent guard), and tally results.
 *
 * Key classes:
 *   - Voter / Candidate
 *   - Election (open/close, castVote, results)
 *   - VotingService (facade)
 *
 * Patterns used: Facade, State-ish election lifecycle, Encapsulation.
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OnlineVotingDemo {
    public static void main(String[] args) {
        VotingService service = new VotingService();
        Election e = service.createElection("E1", "Best Language");
        e.addCandidate(new Candidate("C1", "Java"));
        e.addCandidate(new Candidate("C2", "Python"));
        e.addCandidate(new Candidate("C3", "Go"));

        for (String v : new String[]{"v1","v2","v3","v4","v5"}) e.registerVoter(new Voter(v));

        System.out.println("=== Online Voting Demo ===");
        e.open();
        service.vote("E1", "v1", "C1");
        service.vote("E1", "v2", "C1");
        service.vote("E1", "v3", "C2");
        service.vote("E1", "v1", "C2"); // double vote -> rejected
        service.vote("E1", "v4", "C3");
        service.vote("E1", "v5", "C1");
        service.vote("E1", "v6", "C1"); // unregistered -> rejected
        e.close();

        e.results();
    }
}

class Voter {
    final String id;
    Voter(String id) { this.id = id; }
}

class Candidate {
    final String id; final String name; int votes = 0;
    Candidate(String id, String name) { this.id = id; this.name = name; }
}

enum ElectionState { CREATED, OPEN, CLOSED }

class Election {
    final String id; final String title;
    private final Map<String, Candidate> candidates = new HashMap<String, Candidate>();
    private final Set<String> registeredVoters = new HashSet<String>();
    private final Set<String> voted = new HashSet<String>();
    private ElectionState state = ElectionState.CREATED;

    Election(String id, String title) { this.id = id; this.title = title; }
    void addCandidate(Candidate c) { candidates.put(c.id, c); }
    void registerVoter(Voter v) { registeredVoters.add(v.id); }
    void open() { state = ElectionState.OPEN; System.out.println("Election '" + title + "' OPEN"); }
    void close() { state = ElectionState.CLOSED; System.out.println("Election '" + title + "' CLOSED"); }

    boolean castVote(String voterId, String candidateId) {
        if (state != ElectionState.OPEN) { System.out.println("Voting not open"); return false; }
        if (!registeredVoters.contains(voterId)) { System.out.println("  REJECT: " + voterId + " not registered"); return false; }
        if (voted.contains(voterId)) { System.out.println("  REJECT: " + voterId + " already voted"); return false; }
        Candidate c = candidates.get(candidateId);
        if (c == null) { System.out.println("  REJECT: invalid candidate"); return false; }
        c.votes++;
        voted.add(voterId);
        System.out.println("  " + voterId + " voted for " + c.name);
        return true;
    }

    void results() {
        System.out.println("--- Results: " + title + " ---");
        Candidate winner = null;
        for (Candidate c : candidates.values()) {
            System.out.println("  " + c.name + ": " + c.votes);
            if (winner == null || c.votes > winner.votes) winner = c;
        }
        if (winner != null) System.out.println("WINNER: " + winner.name);
    }
}

class VotingService {
    private final Map<String, Election> elections = new HashMap<String, Election>();
    Election createElection(String id, String title) {
        Election e = new Election(id, title);
        elections.put(id, e);
        return e;
    }
    void vote(String electionId, String voterId, String candidateId) {
        Election e = elections.get(electionId);
        if (e == null) { System.out.println("No such election"); return; }
        e.castVote(voterId, candidateId);
    }
}
