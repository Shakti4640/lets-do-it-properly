/*
 * Online Judge / Code Runner - Low Level Design
 * ---------------------------------------------
 * Models a competitive-programming judge: problems carry test cases, a
 * "solution" is a Function<Integer,Integer> simulating a compiled program,
 * the runner executes it against all tests and assigns a verdict (AC / WA /
 * TLE), and a scoreboard ranks users by solved count.
 *
 * Key classes / abstractions:
 *   - TestCase, Problem
 *   - Verdict (enum), SubmissionResult
 *   - Judge (runs a solution, applies time limit via injected cost model)
 *   - Scoreboard (ranking)
 *
 * Patterns used: Strategy (solution as Function), Facade (Judge), value objects.
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class OnlineJudgeCodeRunnerDemo {
    public static void main(String[] args) {
        System.out.println("=== Online Judge / Code Runner Demo ===");

        Problem square = new Problem("P1", "Square the number");
        square.addCase(new TestCase(2, 4));
        square.addCase(new TestCase(3, 9));
        square.addCase(new TestCase(5, 25));

        Judge judge = new Judge();
        judge.addProblem(square);
        Scoreboard board = new Scoreboard();

        // Correct solution -> Accepted
        SubmissionResult r1 = judge.submit("alice", "P1", x -> x * x, perOpCost(1));
        board.record("alice", r1);
        printResult(r1);

        // Wrong solution -> Wrong Answer on a specific test
        SubmissionResult r2 = judge.submit("bob", "P1", x -> x + x, perOpCost(1));
        board.record("bob", r2);
        printResult(r2);

        // Slow solution -> Time Limit Exceeded
        SubmissionResult r3 = judge.submit("carol", "P1", x -> x * x, perOpCost(1000));
        board.record("carol", r3);
        printResult(r3);

        // Alice solves it again (idempotent for scoring)
        board.record("alice", judge.submit("alice", "P1", x -> x * x, perOpCost(1)));

        System.out.println("\n-- Scoreboard --");
        int rank = 1;
        for (Map.Entry<String, Integer> e : board.ranking().entrySet()) {
            System.out.println("  #" + (rank++) + " " + e.getKey() + " solved=" + e.getValue());
        }
    }

    // Cost model: time consumed per test = unitCost (independent of input here, deterministic)
    static Function<Integer, Integer> perOpCost(int unitCost) {
        return input -> unitCost;
    }

    static void printResult(SubmissionResult r) {
        System.out.println("\n[" + r.user + "] problem " + r.problemId + " -> " + r.verdict
                + (r.failedCase >= 0 ? " (failed test #" + r.failedCase + ")" : "")
                + " time=" + r.timeUsed + "ms");
    }
}

enum Verdict { ACCEPTED, WRONG_ANSWER, TIME_LIMIT_EXCEEDED }

class TestCase {
    final int input;
    final int expected;
    TestCase(int input, int expected) { this.input = input; this.expected = expected; }
}

class Problem {
    final String id;
    final String title;
    final List<TestCase> tests = new ArrayList<TestCase>();
    Problem(String id, String title) { this.id = id; this.title = title; }
    void addCase(TestCase tc) { tests.add(tc); }
}

class SubmissionResult {
    final String user;
    final String problemId;
    final Verdict verdict;
    final int failedCase;
    final int timeUsed;
    SubmissionResult(String user, String problemId, Verdict verdict, int failedCase, int timeUsed) {
        this.user = user; this.problemId = problemId; this.verdict = verdict;
        this.failedCase = failedCase; this.timeUsed = timeUsed;
    }
    boolean accepted() { return verdict == Verdict.ACCEPTED; }
}

class Judge {
    private static final int TIME_LIMIT_MS = 2000;
    private final Map<String, Problem> problems = new HashMap<String, Problem>();

    void addProblem(Problem p) { problems.put(p.id, p); }

    SubmissionResult submit(String user, String problemId,
                            Function<Integer, Integer> solution,
                            Function<Integer, Integer> costModel) {
        Problem problem = problems.get(problemId);
        if (problem == null) throw new IllegalArgumentException("No such problem: " + problemId);

        int totalTime = 0;
        for (int i = 0; i < problem.tests.size(); i++) {
            TestCase tc = problem.tests.get(i);
            totalTime += costModel.apply(tc.input);
            if (totalTime > TIME_LIMIT_MS) {
                return new SubmissionResult(user, problemId, Verdict.TIME_LIMIT_EXCEEDED, i, totalTime);
            }
            int actual = solution.apply(tc.input);
            if (actual != tc.expected) {
                return new SubmissionResult(user, problemId, Verdict.WRONG_ANSWER, i, totalTime);
            }
        }
        return new SubmissionResult(user, problemId, Verdict.ACCEPTED, -1, totalTime);
    }
}

class Scoreboard {
    // user -> set of solved problem ids (tracked as count via map of maps)
    private final Map<String, Map<String, Boolean>> solved = new HashMap<String, Map<String, Boolean>>();

    void record(String user, SubmissionResult result) {
        if (!result.accepted()) {
            solved.putIfAbsent(user, new HashMap<String, Boolean>());
            return;
        }
        Map<String, Boolean> set = solved.get(user);
        if (set == null) { set = new HashMap<String, Boolean>(); solved.put(user, set); }
        set.put(result.problemId, Boolean.TRUE);
    }

    // Returns users ordered by solved count (desc), name (asc) for ties.
    LinkedHashMap<String, Integer> ranking() {
        List<Map.Entry<String, Integer>> entries = new ArrayList<Map.Entry<String, Integer>>();
        for (Map.Entry<String, Map<String, Boolean>> e : solved.entrySet()) {
            entries.add(new java.util.AbstractMap.SimpleEntry<String, Integer>(e.getKey(), e.getValue().size()));
        }
        entries.sort((a, b) -> {
            if (!a.getValue().equals(b.getValue())) return b.getValue() - a.getValue();
            return a.getKey().compareTo(b.getKey());
        });
        LinkedHashMap<String, Integer> result = new LinkedHashMap<String, Integer>();
        for (Map.Entry<String, Integer> e : entries) result.put(e.getKey(), e.getValue());
        return result;
    }
}
