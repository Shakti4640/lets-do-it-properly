/*
 * Cloud File Storage (Dropbox-like) (LLD)
 * ---------------------------------------
 * Hierarchical folders/files with upload, move, delete, file versioning,
 * shareable links, and a per-user storage quota.
 *
 * Key classes / abstractions:
 *   - Node (abstract)        : Composite base for FileNode and FolderNode
 *   - FolderNode / FileNode  : tree nodes; files keep an ordered version history
 *   - FileVersion            : immutable snapshot (size, content hash, timestamp)
 *   - User                   : owns a root folder and a storage quota
 *   - StorageService         : facade for upload/move/delete/share + quota tracking
 *   - ShareLink              : tokenized public link to a file
 *
 * Patterns used: Composite (folder/file tree), Facade (StorageService),
 *                Factory (share-link token creation).
 */
import java.util.*;

public class CloudFileStorageDropboxDemo {
    public static void main(String[] args) {
        StorageService svc = new StorageService();
        User alice = svc.registerUser("alice", 1000); // 1000 bytes quota

        System.out.println("=== Upload files ===");
        svc.createFolder(alice, "/docs");
        svc.upload(alice, "/docs/report.txt", 200);
        svc.upload(alice, "/docs/report.txt", 250); // new version
        svc.upload(alice, "/photo.png", 300);
        svc.printTree(alice);

        System.out.println("\n=== Versioning ===");
        FileNode report = (FileNode) svc.resolve(alice, "/docs/report.txt");
        System.out.println("report.txt versions: " + report.versionCount() + ", latest size=" + report.latest().size);

        System.out.println("\n=== Move ===");
        svc.move(alice, "/photo.png", "/docs/photo.png");
        svc.printTree(alice);

        System.out.println("\n=== Share link ===");
        ShareLink link = svc.share(alice, "/docs/report.txt");
        System.out.println("Share token: " + link.token + " -> " + link.path);
        System.out.println("Resolve link: " + svc.openLink(link.token).name);

        System.out.println("\n=== Quota enforcement ===");
        System.out.println("Used: " + alice.usedBytes() + "/" + alice.quota);
        try {
            svc.upload(alice, "/big.bin", 600); // exceeds remaining quota
        } catch (RuntimeException ex) {
            System.out.println("Rejected: " + ex.getMessage());
        }

        System.out.println("\n=== Delete ===");
        svc.delete(alice, "/docs/photo.png");
        svc.printTree(alice);
        System.out.println("Used after delete: " + alice.usedBytes() + "/" + alice.quota);
    }
}

abstract class Node {
    final String name;
    FolderNode parent;
    Node(String name) { this.name = name; }
    abstract int totalSize();
}

class FileVersion {
    final int size;
    final String hash;
    final long timestamp;
    FileVersion(int size, String hash, long timestamp) {
        this.size = size; this.hash = hash; this.timestamp = timestamp;
    }
}

class FileNode extends Node {
    private final List<FileVersion> versions = new ArrayList<>();
    FileNode(String name) { super(name); }
    void addVersion(FileVersion v) { versions.add(v); }
    FileVersion latest() { return versions.get(versions.size() - 1); }
    int versionCount() { return versions.size(); }
    int totalSize() { return latest().size; }
}

class FolderNode extends Node {
    final Map<String, Node> children = new LinkedHashMap<>();
    FolderNode(String name) { super(name); }
    void add(Node n) { n.parent = this; children.put(n.name, n); }
    void remove(String childName) { children.remove(childName); }
    int totalSize() {
        int sum = 0;
        for (Node c : children.values()) sum += c.totalSize();
        return sum;
    }
}

class User {
    final String id;
    final long quota;
    final FolderNode root = new FolderNode("");
    User(String id, long quota) { this.id = id; this.quota = quota; }
    long usedBytes() { return root.totalSize(); }
}

class ShareLink {
    final String token;
    final String path;
    final String owner;
    ShareLink(String token, String path, String owner) {
        this.token = token; this.path = path; this.owner = owner;
    }
}

class StorageService {
    private final Map<String, User> users = new HashMap<>();
    private final Map<String, ShareLink> links = new HashMap<>();
    private long clock = 1_000L;
    private int tokenSeq = 0;

    User registerUser(String id, long quota) {
        User u = new User(id, quota);
        users.put(id, u);
        return u;
    }

    private String[] split(String path) {
        List<String> parts = new ArrayList<>();
        for (String p : path.split("/")) if (!p.isEmpty()) parts.add(p);
        return parts.toArray(new String[0]);
    }

    void createFolder(User u, String path) {
        String[] parts = split(path);
        FolderNode cur = u.root;
        for (String part : parts) {
            Node next = cur.children.get(part);
            if (next == null) { FolderNode f = new FolderNode(part); cur.add(f); next = f; }
            cur = (FolderNode) next;
        }
    }

    void upload(User u, String path, int size) {
        String[] parts = split(path);
        FolderNode cur = u.root;
        for (int i = 0; i < parts.length - 1; i++) {
            Node next = cur.children.get(parts[i]);
            if (next == null) { FolderNode f = new FolderNode(parts[i]); cur.add(f); next = f; }
            cur = (FolderNode) next;
        }
        String fileName = parts[parts.length - 1];
        FileVersion v = new FileVersion(size, "h" + (clock++), clock);
        Node existing = cur.children.get(fileName);
        FileNode file = (existing instanceof FileNode) ? (FileNode) existing : new FileNode(fileName);
        int delta = size - (existing instanceof FileNode ? file.latest().size : 0);
        if (u.usedBytes() + delta > u.quota)
            throw new RuntimeException("quota exceeded for " + u.id + " (need " + delta + " more bytes)");
        file.addVersion(v);
        if (existing == null) cur.add(file);
        System.out.println("uploaded " + path + " v" + file.versionCount() + " (" + size + "b)");
    }

    Node resolve(User u, String path) {
        String[] parts = split(path);
        Node cur = u.root;
        for (String part : parts) {
            if (!(cur instanceof FolderNode)) return null;
            cur = ((FolderNode) cur).children.get(part);
            if (cur == null) return null;
        }
        return cur;
    }

    void move(User u, String from, String to) {
        Node node = resolve(u, from);
        if (node == null) throw new RuntimeException("not found: " + from);
        node.parent.remove(node.name);
        String[] parts = split(to);
        FolderNode cur = u.root;
        for (int i = 0; i < parts.length - 1; i++)
            cur = (FolderNode) cur.children.get(parts[i]);
        cur.add(node);
        System.out.println("moved " + from + " -> " + to);
    }

    void delete(User u, String path) {
        Node node = resolve(u, path);
        if (node == null) throw new RuntimeException("not found: " + path);
        node.parent.remove(node.name);
        System.out.println("deleted " + path);
    }

    ShareLink share(User u, String path) {
        Node node = resolve(u, path);
        if (!(node instanceof FileNode)) throw new RuntimeException("can only share files: " + path);
        String token = "tok-" + (++tokenSeq);
        ShareLink link = new ShareLink(token, path, u.id);
        links.put(token, link);
        return link;
    }

    Node openLink(String token) {
        ShareLink link = links.get(token);
        if (link == null) throw new RuntimeException("invalid token");
        return resolve(users.get(link.owner), link.path);
    }

    void printTree(User u) {
        System.out.println("[" + u.id + "]");
        printNode(u.root, 1);
    }

    private void printNode(FolderNode folder, int depth) {
        for (Node c : folder.children.values()) {
            StringBuilder indent = new StringBuilder();
            for (int i = 0; i < depth; i++) indent.append("  ");
            if (c instanceof FolderNode) {
                System.out.println(indent + c.name + "/");
                printNode((FolderNode) c, depth + 1);
            } else {
                FileNode f = (FileNode) c;
                System.out.println(indent + c.name + " (" + f.totalSize() + "b, v" + f.versionCount() + ")");
            }
        }
    }
}
