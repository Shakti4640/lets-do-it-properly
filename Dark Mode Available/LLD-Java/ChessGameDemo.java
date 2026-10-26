/*
 * Chess Game - Low Level Design
 * -----------------------------
 * Models a chess board, pieces and move validation. Each piece type
 * implements its own movement rule (Strategy via polymorphism). This is a
 * structural LLD focusing on extensible piece behavior, not a full engine.
 *
 * Key classes:
 *   - Color / Cell / Board
 *   - Piece (abstract) + King, Queen, Rook, Bishop, Knight, Pawn
 *   - Move / Player / ChessGame
 *
 * Patterns used: Strategy (per-piece movement), Template, Encapsulation.
 */

public class ChessGameDemo {
    public static void main(String[] args) {
        Board board = new Board();
        board.setupStandard();
        ChessGame game = new ChessGame(board);

        System.out.println("=== Chess Demo ===");
        board.print();
        // White pawn e2 -> e4 (row 6 col 4 -> row 4 col 4)
        game.move(6, 4, 4, 4);
        // White knight b1 -> c3 (row 7 col 1 -> row 5 col 2)
        game.move(7, 1, 5, 2);
        // Illegal: rook jumping over pieces
        game.move(7, 0, 5, 0);
        board.print();
    }
}

enum Color { WHITE, BLACK }

class Cell {
    final int row, col;
    private Piece piece;
    Cell(int row, int col) { this.row = row; this.col = col; }
    Piece getPiece() { return piece; }
    void setPiece(Piece p) { this.piece = p; }
    boolean isEmpty() { return piece == null; }
}

abstract class Piece {
    protected final Color color;
    protected Piece(Color color) { this.color = color; }
    Color getColor() { return color; }
    abstract boolean canMove(Board board, Cell from, Cell to);
    abstract char symbol();
}

class King extends Piece {
    King(Color c) { super(c); }
    boolean canMove(Board b, Cell f, Cell t) {
        return Math.abs(f.row - t.row) <= 1 && Math.abs(f.col - t.col) <= 1;
    }
    char symbol() { return color == Color.WHITE ? 'K' : 'k'; }
}
class Rook extends Piece {
    Rook(Color c) { super(c); }
    boolean canMove(Board b, Cell f, Cell t) {
        if (f.row != t.row && f.col != t.col) return false;
        return b.pathClear(f, t);
    }
    char symbol() { return color == Color.WHITE ? 'R' : 'r'; }
}
class Bishop extends Piece {
    Bishop(Color c) { super(c); }
    boolean canMove(Board b, Cell f, Cell t) {
        if (Math.abs(f.row - t.row) != Math.abs(f.col - t.col)) return false;
        return b.pathClear(f, t);
    }
    char symbol() { return color == Color.WHITE ? 'B' : 'b'; }
}
class Queen extends Piece {
    Queen(Color c) { super(c); }
    boolean canMove(Board b, Cell f, Cell t) {
        boolean straight = f.row == t.row || f.col == t.col;
        boolean diagonal = Math.abs(f.row - t.row) == Math.abs(f.col - t.col);
        if (!straight && !diagonal) return false;
        return b.pathClear(f, t);
    }
    char symbol() { return color == Color.WHITE ? 'Q' : 'q'; }
}
class Knight extends Piece {
    Knight(Color c) { super(c); }
    boolean canMove(Board b, Cell f, Cell t) {
        int dr = Math.abs(f.row - t.row), dc = Math.abs(f.col - t.col);
        return (dr == 2 && dc == 1) || (dr == 1 && dc == 2);
    }
    char symbol() { return color == Color.WHITE ? 'N' : 'n'; }
}
class Pawn extends Piece {
    Pawn(Color c) { super(c); }
    boolean canMove(Board b, Cell f, Cell t) {
        int dir = color == Color.WHITE ? -1 : 1;
        int startRow = color == Color.WHITE ? 6 : 1;
        // forward move
        if (f.col == t.col && t.isEmpty()) {
            if (t.row - f.row == dir) return true;
            if (f.row == startRow && t.row - f.row == 2 * dir) return b.pathClear(f, t);
        }
        // capture
        if (Math.abs(f.col - t.col) == 1 && t.row - f.row == dir && !t.isEmpty()
                && t.getPiece().getColor() != color) return true;
        return false;
    }
    char symbol() { return color == Color.WHITE ? 'P' : 'p'; }
}

class Board {
    private final Cell[][] cells = new Cell[8][8];
    Board() {
        for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) cells[r][c] = new Cell(r, c);
    }
    Cell getCell(int r, int c) { return cells[r][c]; }
    boolean inBounds(int r, int c) { return r >= 0 && r < 8 && c >= 0 && c < 8; }

    void setupStandard() {
        for (int c = 0; c < 8; c++) {
            cells[1][c].setPiece(new Pawn(Color.BLACK));
            cells[6][c].setPiece(new Pawn(Color.WHITE));
        }
        placeBackRank(0, Color.BLACK);
        placeBackRank(7, Color.WHITE);
    }
    private void placeBackRank(int row, Color color) {
        cells[row][0].setPiece(new Rook(color));
        cells[row][1].setPiece(new Knight(color));
        cells[row][2].setPiece(new Bishop(color));
        cells[row][3].setPiece(new Queen(color));
        cells[row][4].setPiece(new King(color));
        cells[row][5].setPiece(new Bishop(color));
        cells[row][6].setPiece(new Knight(color));
        cells[row][7].setPiece(new Rook(color));
    }

    /** True if all cells strictly between from and to (linear) are empty. */
    boolean pathClear(Cell from, Cell to) {
        int dr = Integer.compare(to.row, from.row);
        int dc = Integer.compare(to.col, from.col);
        int r = from.row + dr, c = from.col + dc;
        while (r != to.row || c != to.col) {
            if (!cells[r][c].isEmpty()) return false;
            r += dr; c += dc;
        }
        return true;
    }

    void print() {
        System.out.println("  a b c d e f g h");
        for (int r = 0; r < 8; r++) {
            StringBuilder sb = new StringBuilder((8 - r) + " ");
            for (int c = 0; c < 8; c++) {
                Piece p = cells[r][c].getPiece();
                sb.append(p == null ? '.' : p.symbol()).append(' ');
            }
            System.out.println(sb.toString());
        }
        System.out.println();
    }
}

class ChessGame {
    private final Board board;
    private Color turn = Color.WHITE;
    ChessGame(Board board) { this.board = board; }

    boolean move(int fr, int fc, int tr, int tc) {
        if (!board.inBounds(fr, fc) || !board.inBounds(tr, tc)) { System.out.println("Out of bounds"); return false; }
        Cell from = board.getCell(fr, fc);
        Cell to = board.getCell(tr, tc);
        Piece p = from.getPiece();
        if (p == null) { System.out.println("No piece at source"); return false; }
        if (p.getColor() != turn) { System.out.println("Not " + p.getColor() + "'s turn"); return false; }
        if (!to.isEmpty() && to.getPiece().getColor() == p.getColor()) { System.out.println("Cannot capture own piece"); return false; }
        if (!p.canMove(board, from, to)) { System.out.println("Illegal move for " + p.symbol()); return false; }
        to.setPiece(p);
        from.setPiece(null);
        System.out.println(p.getColor() + " " + p.symbol() + ": (" + fr + "," + fc + ") -> (" + tr + "," + tc + ")");
        turn = turn == Color.WHITE ? Color.BLACK : Color.WHITE;
        return true;
    }
}
