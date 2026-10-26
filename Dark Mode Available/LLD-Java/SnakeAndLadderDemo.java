/*
 * Snake and Ladder Game - Low Level Design
 * ----------------------------------------
 * Classic board game for N players on a 100-cell board with snakes and
 * ladders. Players roll a die; landing on a snake head/ladder bottom
 * jumps them. First to reach the last cell wins.
 *
 * Key classes:
 *   - Board / Jump (snake or ladder)
 *   - Dice
 *   - Player
 *   - Game (orchestrator)
 *
 * Patterns used: Encapsulation, simple composition.
 */

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;

public class SnakeAndLadderDemo {
    public static void main(String[] args) {
        Board board = new Board(100);
        // ladders (bottom -> top)
        board.addJump(2, 38);
        board.addJump(7, 14);
        board.addJump(28, 76);
        // snakes (head -> tail)
        board.addJump(99, 5);
        board.addJump(47, 26);
        board.addJump(83, 39);

        List<Player> players = new ArrayList<Player>();
        players.add(new Player("Alice"));
        players.add(new Player("Bob"));

        Game game = new Game(board, players, new Dice(1, 42L));
        System.out.println("=== Snake and Ladder Demo ===");
        game.play();
    }
}

class Dice {
    private final int count;
    private final Random random;
    Dice(int count, long seed) { this.count = count; this.random = new Random(seed); }
    int roll() {
        int total = 0;
        for (int i = 0; i < count; i++) total += random.nextInt(6) + 1;
        return total;
    }
}

class Jump {
    final int start;
    final int end;
    Jump(int start, int end) { this.start = start; this.end = end; }
    boolean isLadder() { return end > start; }
}

class Board {
    private final int size;
    private final Map<Integer, Jump> jumps = new HashMap<Integer, Jump>();
    Board(int size) { this.size = size; }
    int getSize() { return size; }
    void addJump(int start, int end) { jumps.put(start, new Jump(start, end)); }
    int resolve(int position) {
        Jump j = jumps.get(position);
        if (j == null) return position;
        System.out.println("   " + (j.isLadder() ? "Ladder!" : "Snake!") + " " + position + " -> " + j.end);
        return j.end;
    }
}

class Player {
    private final String name;
    private int position = 0;
    Player(String name) { this.name = name; }
    String getName() { return name; }
    int getPosition() { return position; }
    void setPosition(int p) { position = p; }
}

class Game {
    private final Board board;
    private final Queue<Player> turnOrder = new ArrayDeque<Player>();
    private final Dice dice;

    Game(Board board, List<Player> players, Dice dice) {
        this.board = board;
        this.dice = dice;
        turnOrder.addAll(players);
    }

    void play() {
        while (true) {
            Player p = turnOrder.poll();
            int roll = dice.roll();
            int target = p.getPosition() + roll;
            if (target > board.getSize()) {
                System.out.println(p.getName() + " rolled " + roll + " - overshoot, stays at " + p.getPosition());
            } else {
                target = board.resolve(target);
                p.setPosition(target);
                System.out.println(p.getName() + " rolled " + roll + " -> " + target);
            }
            if (p.getPosition() == board.getSize()) {
                System.out.println("*** " + p.getName() + " WINS! ***");
                return;
            }
            turnOrder.add(p);
        }
    }
}
