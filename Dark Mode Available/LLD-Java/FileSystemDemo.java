/*
 * File System - Low Level Design
 * ------------------------------
 * In-memory hierarchical file system using the Composite pattern: both
 * files and directories are FileSystemNodes; directories contain children.
 *
 * Key classes:
 *   - FileSystemNode (component)
 *   - File (leaf) / Directory (composite)
 *   - FileSystem (facade with path navigation)
 *
 * Patterns used: Composite, Facade.
 */

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FileSystemDemo {
    public static void main(String[] args) {
        FileSystem fs = new FileSystem();
        System.out.println("=== File System Demo ===");
        fs.mkdir("/home");
        fs.mkdir("/home/alice");
        fs.create("/home/alice/notes.txt", "hello world");
        fs.create("/home/alice/todo.txt", "buy milk");
        fs.mkdir("/home/alice/projects");
        fs.create("/home/alice/projects/app.java", "class App {}");

        fs.tree();
        System.out.println("Read notes.txt: " + fs.read("/home/alice/notes.txt"));
        System.out.println("Size of /home/alice: " + fs.size("/home/alice") + " bytes");
        fs.delete("/home/alice/todo.txt");
        System.out.println("After delete:");
        fs.tree();
    }
}

abstract class FileSystemNode {
    protected final String name;
    FileSystemNode(String name) { this.name = name; }
    String getName() { return name; }
    abstract boolean isDirectory();
    abstract int size();
}

class File extends FileSystemNode {
    private String content;
    File(String name, String content) { super(name); this.content = content; }
    String getContent() { return content; }
    void setContent(String c) { content = c; }
    boolean isDirectory() { return false; }
    int size() { return content == null ? 0 : content.length(); }
}

class Directory extends FileSystemNode {
    private final Map<String, FileSystemNode> children = new LinkedHashMap<String, FileSystemNode>();
    Directory(String name) { super(name); }
    boolean isDirectory() { return true; }
    Map<String, FileSystemNode> getChildren() { return children; }
    void add(FileSystemNode node) { children.put(node.getName(), node); }
    void remove(String name) { children.remove(name); }
    int size() {
        int total = 0;
        for (FileSystemNode n : children.values()) total += n.size();
        return total;
    }
}

class FileSystem {
    private final Directory root = new Directory("");

    private String[] parts(String path) {
        List<String> list = new ArrayList<String>();
        for (String p : path.split("/")) if (!p.isEmpty()) list.add(p);
        return list.toArray(new String[0]);
    }

    private Directory navigateToParent(String path) {
        String[] p = parts(path);
        Directory cur = root;
        for (int i = 0; i < p.length - 1; i++) {
            FileSystemNode next = cur.getChildren().get(p[i]);
            if (!(next instanceof Directory)) return null;
            cur = (Directory) next;
        }
        return cur;
    }

    void mkdir(String path) {
        String[] p = parts(path);
        Directory parent = navigateToParent(path);
        if (parent == null) { System.out.println("mkdir failed: " + path); return; }
        parent.add(new Directory(p[p.length - 1]));
        System.out.println("mkdir " + path);
    }

    void create(String path, String content) {
        String[] p = parts(path);
        Directory parent = navigateToParent(path);
        if (parent == null) { System.out.println("create failed: " + path); return; }
        parent.add(new File(p[p.length - 1], content));
        System.out.println("create " + path);
    }

    String read(String path) {
        String[] p = parts(path);
        Directory parent = navigateToParent(path);
        if (parent == null) return null;
        FileSystemNode node = parent.getChildren().get(p[p.length - 1]);
        return (node instanceof File) ? ((File) node).getContent() : null;
    }

    void delete(String path) {
        String[] p = parts(path);
        Directory parent = navigateToParent(path);
        if (parent != null) { parent.remove(p[p.length - 1]); System.out.println("delete " + path); }
    }

    int size(String path) {
        String[] p = parts(path);
        Directory parent = navigateToParent(path);
        if (parent == null) return 0;
        FileSystemNode node = (p.length == 0) ? root : parent.getChildren().get(p[p.length - 1]);
        return node == null ? 0 : node.size();
    }

    void tree() { print(root, 0); }
    private void print(FileSystemNode node, int depth) {
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < depth; i++) indent.append("  ");
        if (node != root) System.out.println(indent + (node.isDirectory() ? "[" + node.getName() + "]" : node.getName()));
        if (node instanceof Directory) {
            for (FileSystemNode child : ((Directory) node).getChildren().values()) print(child, depth + 1);
        }
    }
}
