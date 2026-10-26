/*
 * Collaborative Document Editor - Low Level Design
 * ------------------------------------------------
 * A shared document edited by multiple users via Insert/Delete operations.
 * Operations are funneled through a central server that assigns a global
 * sequence number, giving a single conflict-free total order. Each applied
 * operation is pushed onto an undo stack so edits can be reverted (Command).
 *
 * Key classes / abstractions:
 *   - Operation (+ Insert/Delete) : Command objects that mutate the document
 *   - Document                    : the shared text buffer + version
 *   - EditorServer                : totally orders ops, applies, supports undo
 *
 * Patterns used: Command (operations + undo), Facade (EditorServer).
 */

import java.util.ArrayDeque;
import java.util.Deque;

public class CollaborativeDocumentEditorDemo {
    public static void main(String[] args) {
        EditorServer server = new EditorServer();

        System.out.println("=== Collaborative Document Editor Demo ===");
        System.out.println("Initial: \"" + server.getText() + "\"");

        // Two editors submit operations; server assigns global order.
        server.submit("alice", new InsertOperation(0, "Hello"));
        server.submit("bob",   new InsertOperation(5, " World"));
        server.submit("alice", new InsertOperation(11, "!"));
        System.out.println("After inserts: \"" + server.getText() + "\" (v" + server.getVersion() + ")");

        server.submit("bob", new DeleteOperation(0, 6)); // remove "Hello "
        System.out.println("After delete:  \"" + server.getText() + "\" (v" + server.getVersion() + ")");

        server.submit("carol", new InsertOperation(0, "Brave "));
        System.out.println("After insert:  \"" + server.getText() + "\" (v" + server.getVersion() + ")");

        System.out.println("\n--- Undo last two operations ---");
        server.undo();
        System.out.println("After undo:    \"" + server.getText() + "\" (v" + server.getVersion() + ")");
        server.undo();
        System.out.println("After undo:    \"" + server.getText() + "\" (v" + server.getVersion() + ")");

        System.out.println("\n--- Operation log (global order) ---");
        server.printLog();
    }
}

interface Operation {
    /** Apply this op to the document, recording any data needed for undo. */
    void apply(Document doc);
    /** Reverse the effect of a previously applied op. */
    void undo(Document doc);
    String describe();
}

class InsertOperation implements Operation {
    private final int position;
    private final String content;

    InsertOperation(int position, String content) {
        this.position = position;
        this.content = content;
    }

    public void apply(Document doc) { doc.insert(position, content); }
    public void undo(Document doc) { doc.delete(position, content.length()); }
    public String describe() { return "INSERT @" + position + " \"" + content + "\""; }
}

class DeleteOperation implements Operation {
    private final int position;
    private final int length;
    private String removed = "";

    DeleteOperation(int position, int length) {
        this.position = position;
        this.length = length;
    }

    public void apply(Document doc) { removed = doc.delete(position, length); }
    public void undo(Document doc) { doc.insert(position, removed); }
    public String describe() { return "DELETE @" + position + " len=" + length; }
}

class Document {
    private final StringBuilder buffer = new StringBuilder();
    private int version = 0;

    void insert(int position, String content) {
        int p = clamp(position);
        buffer.insert(p, content);
        version++;
    }

    String delete(int position, int length) {
        int start = clamp(position);
        int end = Math.min(start + length, buffer.length());
        String removed = buffer.substring(start, end);
        buffer.delete(start, end);
        version++;
        return removed;
    }

    private int clamp(int p) {
        if (p < 0) return 0;
        if (p > buffer.length()) return buffer.length();
        return p;
    }

    String getText() { return buffer.toString(); }
    int getVersion() { return version; }
}

class AppliedOp {
    final long seq;
    final String editor;
    final Operation op;
    AppliedOp(long seq, String editor, Operation op) {
        this.seq = seq;
        this.editor = editor;
        this.op = op;
    }
}

class EditorServer {
    private final Document document = new Document();
    private final Deque<AppliedOp> history = new ArrayDeque<AppliedOp>();
    private final java.util.List<AppliedOp> log = new java.util.ArrayList<AppliedOp>();
    private long sequence = 0L;

    void submit(String editor, Operation op) {
        long seq = ++sequence;
        op.apply(document);
        AppliedOp applied = new AppliedOp(seq, editor, op);
        history.push(applied);
        log.add(applied);
        System.out.println("seq#" + seq + " @" + editor + " " + op.describe());
    }

    void undo() {
        if (history.isEmpty()) {
            System.out.println("Nothing to undo");
            return;
        }
        AppliedOp last = history.pop();
        last.op.undo(document);
        System.out.println("Undid seq#" + last.seq + " (" + last.op.describe() + ")");
    }

    String getText() { return document.getText(); }
    int getVersion() { return document.getVersion(); }

    void printLog() {
        for (AppliedOp a : log) {
            System.out.println("seq#" + a.seq + " @" + a.editor + " " + a.op.describe());
        }
    }
}
