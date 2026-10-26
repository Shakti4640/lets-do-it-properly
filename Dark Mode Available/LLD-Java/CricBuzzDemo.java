/*
 * CricBuzz (Live Cricket Scoring) - Low Level Design
 * --------------------------------------------------
 * Models a cricket match, processes ball-by-ball events, maintains the
 * scorecard and notifies subscribers (Observer) of score updates.
 *
 * Key classes:
 *   - Player / Team
 *   - Ball (outcome: runs / wicket / extras)
 *   - Innings / Scorecard
 *   - MatchObserver (+ Commentary) : Observer
 *   - Match (facade)
 *
 * Patterns used: Observer (live updates), Facade, Encapsulation.
 */

import java.util.ArrayList;
import java.util.List;

public class CricBuzzDemo {
    public static void main(String[] args) {
        Team india = new Team("India");
        india.addPlayer(new Player("Rohit"));
        india.addPlayer(new Player("Virat"));
        Team aus = new Team("Australia");
        aus.addPlayer(new Player("Smith"));

        Match match = new Match(india, aus, 2); // 2 overs (demo)
        match.subscribe(new CommentaryObserver());

        System.out.println("=== CricBuzz Demo ===");
        match.startInnings(india);
        match.playBall(new Ball(4, false, false));
        match.playBall(new Ball(1, false, false));
        match.playBall(new Ball(0, false, true));  // wide (extra)
        match.playBall(new Ball(6, false, false));
        match.playBall(new Ball(0, true, false));   // wicket
        match.playBall(new Ball(2, false, false));
        match.playBall(new Ball(3, false, false));  // over likely completes

        match.scorecard();
    }
}

class Player {
    final String name; int runs = 0; boolean out = false;
    Player(String name) { this.name = name; }
}

class Team {
    final String name;
    final List<Player> players = new ArrayList<Player>();
    Team(String name) { this.name = name; }
    void addPlayer(Player p) { players.add(p); }
}

class Ball {
    final int runs; final boolean wicket; final boolean extra;
    Ball(int runs, boolean wicket, boolean extra) { this.runs = runs; this.wicket = wicket; this.extra = extra; }
}

class Innings {
    final Team batting;
    int totalRuns = 0; int wickets = 0; int legalBalls = 0;
    Innings(Team batting) { this.batting = batting; }
    double overs() { return legalBalls / 6 + (legalBalls % 6) / 10.0; }
}

interface MatchObserver {
    void onUpdate(Innings innings, Ball ball);
}

class CommentaryObserver implements MatchObserver {
    public void onUpdate(Innings innings, Ball ball) {
        String desc;
        if (ball.extra) desc = "Extra! +1 run";
        else if (ball.wicket) desc = "WICKET!";
        else if (ball.runs == 4) desc = "FOUR!";
        else if (ball.runs == 6) desc = "SIX!";
        else desc = ball.runs + " run(s)";
        System.out.println("  [" + innings.batting.name + " " + innings.totalRuns + "/" + innings.wickets
                + " (" + innings.overs() + ")] " + desc);
    }
}

class Match {
    private final Team teamA; private final Team teamB; private final int maxOvers;
    private final List<MatchObserver> observers = new ArrayList<MatchObserver>();
    private Innings currentInnings;

    Match(Team teamA, Team teamB, int maxOvers) { this.teamA = teamA; this.teamB = teamB; this.maxOvers = maxOvers; }
    void subscribe(MatchObserver o) { observers.add(o); }

    void startInnings(Team batting) {
        currentInnings = new Innings(batting);
        System.out.println(batting.name + " innings started (" + maxOvers + " overs)");
    }

    void playBall(Ball ball) {
        if (currentInnings == null) return;
        if (currentInnings.legalBalls >= maxOvers * 6) { System.out.println("  Innings over."); return; }
        currentInnings.totalRuns += ball.runs + (ball.extra ? 1 : 0);
        if (ball.wicket) currentInnings.wickets++;
        if (!ball.extra) currentInnings.legalBalls++;
        for (MatchObserver o : observers) o.onUpdate(currentInnings, ball);
    }

    void scorecard() {
        System.out.println("--- Scorecard ---");
        System.out.println(currentInnings.batting.name + ": " + currentInnings.totalRuns + "/" + currentInnings.wickets
                + " in " + currentInnings.overs() + " overs");
    }
}
