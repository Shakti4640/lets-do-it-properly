/*
 * Online Examination System - Low Level Design
 * --------------------------------------------
 * Models an exam composed of multiple-choice questions with a time limit.
 * A candidate submits answers (with the elapsed time), the system auto-grades
 * them, enforces the time limit, and produces a result with score and a
 * pass/fail outcome based on the passing threshold.
 *
 * Key classes / abstractions:
 *   - Question (MCQ), Exam
 *   - ExamAttempt (candidate answers + elapsed time)
 *   - ExamResult (+ Outcome enum)
 *   - ExamService (auto-grading facade)
 *
 * Patterns used: Builder (Exam), Facade (ExamService), value objects.
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OnlineExaminationSystemDemo {
    public static void main(String[] args) {
        System.out.println("=== Online Examination System Demo ===");

        Exam exam = new Exam.Builder("Java Basics", 60 /*seconds*/, 60 /*pass %*/)
                .question(new Question("Q1", "JVM stands for?",
                        new String[]{"Java Virtual Machine", "Java Variable Method", "Just Verify Memory"}, 0, 1))
                .question(new Question("Q2", "Which is NOT a primitive?",
                        new String[]{"int", "String", "boolean"}, 1, 1))
                .question(new Question("Q3", "Default value of boolean?",
                        new String[]{"true", "false", "null"}, 1, 1))
                .question(new Question("Q4", "Size of int in bytes?",
                        new String[]{"2", "4", "8"}, 1, 1))
                .build();

        ExamService service = new ExamService();
        System.out.println("Exam '" + exam.title + "' questions=" + exam.questions.size()
                + " limit=" + exam.timeLimitSeconds + "s pass=" + exam.passPercent + "%");

        // Candidate 1: 3/4 correct, within time -> PASS
        System.out.println("\n-- Candidate: alice --");
        ExamAttempt alice = new ExamAttempt("alice", 45);
        alice.answer("Q1", 0);
        alice.answer("Q2", 1);
        alice.answer("Q3", 1);
        alice.answer("Q4", 0); // wrong
        printResult(service.grade(exam, alice));

        // Candidate 2: all correct but over time limit -> FAIL (disqualified)
        System.out.println("\n-- Candidate: bob (over time) --");
        ExamAttempt bob = new ExamAttempt("bob", 75);
        bob.answer("Q1", 0);
        bob.answer("Q2", 1);
        bob.answer("Q3", 1);
        bob.answer("Q4", 1);
        printResult(service.grade(exam, bob));

        // Candidate 3: only 1/4 -> FAIL
        System.out.println("\n-- Candidate: carol --");
        ExamAttempt carol = new ExamAttempt("carol", 30);
        carol.answer("Q1", 0);
        carol.answer("Q2", 0);
        carol.answer("Q3", 0);
        printResult(service.grade(exam, carol));
    }

    static void printResult(ExamResult r) {
        System.out.println("  " + r.candidate + ": score=" + r.score + "/" + r.maxScore
                + " (" + r.percent + "%) -> " + r.outcome
                + (r.note != null ? " [" + r.note + "]" : ""));
    }
}

enum Outcome { PASS, FAIL }

class Question {
    final String id;
    final String text;
    final String[] options;
    final int correctIndex;
    final int marks;
    Question(String id, String text, String[] options, int correctIndex, int marks) {
        this.id = id; this.text = text; this.options = options;
        this.correctIndex = correctIndex; this.marks = marks;
    }
}

class Exam {
    final String title;
    final int timeLimitSeconds;
    final int passPercent;
    final List<Question> questions;

    private Exam(String title, int timeLimitSeconds, int passPercent, List<Question> questions) {
        this.title = title; this.timeLimitSeconds = timeLimitSeconds;
        this.passPercent = passPercent; this.questions = questions;
    }

    static class Builder {
        private final String title;
        private final int timeLimitSeconds;
        private final int passPercent;
        private final List<Question> questions = new ArrayList<Question>();
        Builder(String title, int timeLimitSeconds, int passPercent) {
            this.title = title; this.timeLimitSeconds = timeLimitSeconds; this.passPercent = passPercent;
        }
        Builder question(Question q) { questions.add(q); return this; }
        Exam build() { return new Exam(title, timeLimitSeconds, passPercent, questions); }
    }
}

class ExamAttempt {
    final String candidate;
    final int elapsedSeconds;
    final Map<String, Integer> answers = new HashMap<String, Integer>();
    ExamAttempt(String candidate, int elapsedSeconds) {
        this.candidate = candidate; this.elapsedSeconds = elapsedSeconds;
    }
    void answer(String questionId, int optionIndex) { answers.put(questionId, optionIndex); }
}

class ExamResult {
    final String candidate;
    final int score;
    final int maxScore;
    final int percent;
    final Outcome outcome;
    final String note;
    ExamResult(String candidate, int score, int maxScore, int percent, Outcome outcome, String note) {
        this.candidate = candidate; this.score = score; this.maxScore = maxScore;
        this.percent = percent; this.outcome = outcome; this.note = note;
    }
}

class ExamService {
    ExamResult grade(Exam exam, ExamAttempt attempt) {
        int maxScore = 0;
        for (Question q : exam.questions) maxScore += q.marks;

        if (attempt.elapsedSeconds > exam.timeLimitSeconds) {
            return new ExamResult(attempt.candidate, 0, maxScore, 0, Outcome.FAIL, "time limit exceeded");
        }

        int score = 0;
        for (Question q : exam.questions) {
            Integer chosen = attempt.answers.get(q.id);
            if (chosen != null && chosen == q.correctIndex) score += q.marks;
        }
        int percent = maxScore == 0 ? 0 : (score * 100) / maxScore;
        Outcome outcome = percent >= exam.passPercent ? Outcome.PASS : Outcome.FAIL;
        return new ExamResult(attempt.candidate, score, maxScore, percent, outcome, null);
    }
}
