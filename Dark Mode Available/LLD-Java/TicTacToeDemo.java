/*
 * Tic-Tac-Toe Game - Low Level Design
 * -----------------------------------
 * Two-player NxN tic-tac-toe with win/draw detection.
 *
 * Key classes:
 *   - Symbol (X/O) / Player
 *   - Board (grid + move + winner check)
 *   - Game (turn loop)
 *
 * Patterns used: Encapsulation, simple state in Board.
 */

import java.util.ArrayDeque;
import java.util.Queue;

public class TicTacToeDemo {
    public static void main(String[] args) {
        Player p1 = new Player("Alice", Symbol.X);
        Player p2 = new Player("Bob", Symbol.O);
        Game game = new Game(3, p1, p2);

        System.out.println("=== Tic-Tac-Toe Demo ===");
        int[][] moves = {{0,0},{1,1},{0,1},{2,2},{0,2}}; // Alice wins top row
        for (int[] m : moves) {
            if (!game.play(m[0], m[1])) break;
        }
        game.getBoard().print();
    }
}

enum Symbol { X, O, EMPTY }

class Player {
    private final String name;
    private final Symbol symbol;
    Player(String name, Symbol symbol) { this.name = name; this.symbol = symbol; }
    String getName() { return name; }
    Symbol getSymbol() { return symbol; }
}

class Board {
    private final int n;
    private final Symbol[][] grid;
    private int movesPlayed = 0;
    Board(int n) {
        this.n = n;
        grid = new Symbol[n][n];
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) grid[i][j] = Symbol.EMPTY;
    }
    int size() { return n; }
    boolean isFull() { return movesPlayed == n * n; }

    boolean place(int r, int c, Symbol s) {
        if (r < 0 || c < 0 || r >= n || c >= n || grid[r][c] != Symbol.EMPTY) return false;
        grid[r][c] = s;
        movesPlayed++;
        return true;
    }

    boolean isWinning(int r, int c, Symbol s) {
        boolean row = true, col = true, diag = true, anti = true;
        for (int i = 0; i < n; i++) {
            if (grid[r][i] != s) row = false;
            if (grid[i][c] != s) col = false;
            if (grid[i][i] != s) diag = false;
            if (grid[i][n - 1 - i] != s) anti = false;
        }
        return row || col || diag || anti;
    }

    void print() {
        for (int i = 0; i < n; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < n; j++) {
                sb.append(grid[i][j] == Symbol.EMPTY ? "." : grid[i][j].name());
                if (j < n - 1) sb.append(" | ");
            }
            System.out.println(sb.toString());
        }
    }
}

class Game {
    private final Board board;
    private final Queue<Player> turns = new ArrayDeque<Player>();
    private boolean over = false;

    Game(int n, Player p1, Player p2) {
        this.board = new Board(n);
        turns.add(p1); turns.add(p2);
    }
    Board getBoard() { return board; }

    /** Returns false when the game has ended. */
    boolean play(int r, int c) {
        if (over) return false;
        Player p = turns.peek();
        if (!board.place(r, c, p.getSymbol())) {
            System.out.println("Invalid move at (" + r + "," + c + ") - try again");
            return true;
        }
        System.out.println(p.getName() + " -> (" + r + "," + c + ")");
        if (board.isWinning(r, c, p.getSymbol())) {
            System.out.println("*** " + p.getName() + " WINS! ***");
            over = true;
            return false;
        }
        if (board.isFull()) {
            System.out.println("*** DRAW ***");
            over = true;
            return false;
        }
        turns.add(turns.poll());
        return true;
    }
}
