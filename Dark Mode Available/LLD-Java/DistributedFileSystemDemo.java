/*
 * Distributed File System (HDFS-like) (LLD)
 * -----------------------------------------
 * Files are split into fixed-size blocks. Each block is replicated across data
 * nodes (replication factor). A NameNode holds metadata mapping file -> blocks
 * and block -> replica locations. Reads reconstruct the file from any live
 * replica; node failures are tolerated as long as one replica survives.
 *
 * Key classes / abstractions:
 *   - DataNode           : stores block-id -> content; can be marked down
 *   - BlockInfo          : block id + the set of nodes holding a replica
 *   - NameNode           : metadata + block placement (round-robin) + read path
 *   - DistributedFileSystem : Facade for write/read/fail-node operations
 *
 * Patterns used: Facade (DFS), round-robin placement strategy, Composite-ish
 *                metadata. Deterministic block splitting.
 */
import java.util.*;

public class DistributedFileSystemDemo {
    public static void main(String[] args) {
        DistributedFileSystem dfs = new DistributedFileSystem(4, 8, 3); // 4 nodes, 8-char blocks, RF=3

        System.out.println("=== Write a file ===");
        String content = "HELLO-DISTRIBUTED-FILE-SYSTEMS-WORLD"; // 36 chars -> 5 blocks
        dfs.write("/data/greeting.txt", content);
        dfs.printMetadata("/data/greeting.txt");

        System.out.println("\n=== Read back (all nodes healthy) ===");
        String read1 = dfs.read("/data/greeting.txt");
        System.out.println("read = " + read1);
        System.out.println("matches original? " + read1.equals(content));

        System.out.println("\n=== Fail one node (RF=3 tolerates it) ===");
        dfs.failNode(1);
        String read2 = dfs.read("/data/greeting.txt");
        System.out.println("read after 1 failure = " + read2);
        System.out.println("matches original? " + read2.equals(content));

        System.out.println("\n=== Fail a second node ===");
        dfs.failNode(2);
        String read3 = dfs.read("/data/greeting.txt");
        System.out.println("read after 2 failures = " + read3);
        System.out.println("matches original? " + read3.equals(content));

        System.out.println("\n=== Cluster health ===");
        dfs.printClusterHealth();
    }
}

class DataNode {
    final int id;
    boolean up = true;
    final Map<String, String> blocks = new HashMap<>();
    DataNode(int id) { this.id = id; }
    void store(String blockId, String data) { blocks.put(blockId, data); }
    String fetch(String blockId) { return blocks.get(blockId); }
}

class BlockInfo {
    final String blockId;
    final List<Integer> replicaNodes = new ArrayList<>();
    BlockInfo(String blockId) { this.blockId = blockId; }
}

class NameNode {
    // file path -> ordered list of block ids
    final Map<String, List<String>> fileBlocks = new LinkedHashMap<>();
    // block id -> replica placement metadata
    final Map<String, BlockInfo> blockTable = new HashMap<>();

    void registerBlock(String file, BlockInfo info) {
        fileBlocks.computeIfAbsent(file, f -> new ArrayList<>()).add(info.blockId);
        blockTable.put(info.blockId, info);
    }
}

class DistributedFileSystem {
    private final List<DataNode> nodes = new ArrayList<>();
    private final NameNode nameNode = new NameNode();
    private final int blockSize;
    private final int replicationFactor;
    private int placementCursor = 0;
    private int blockSeq = 0;

    DistributedFileSystem(int nodeCount, int blockSize, int replicationFactor) {
        for (int i = 0; i < nodeCount; i++) nodes.add(new DataNode(i));
        this.blockSize = blockSize;
        this.replicationFactor = replicationFactor;
        if (replicationFactor > nodeCount)
            throw new RuntimeException("replication factor exceeds node count");
    }

    void write(String path, String content) {
        for (int off = 0; off < content.length(); off += blockSize) {
            String chunk = content.substring(off, Math.min(off + blockSize, content.length()));
            String blockId = "blk-" + (++blockSeq);
            BlockInfo info = new BlockInfo(blockId);
            // Round-robin placement of RF distinct replicas.
            for (int r = 0; r < replicationFactor; r++) {
                int nodeId = (placementCursor + r) % nodes.size();
                nodes.get(nodeId).store(blockId, chunk);
                info.replicaNodes.add(nodeId);
            }
            placementCursor = (placementCursor + 1) % nodes.size();
            nameNode.registerBlock(path, info);
            System.out.println("  " + blockId + " ('" + chunk + "') -> nodes " + info.replicaNodes);
        }
    }

    String read(String path) {
        List<String> blockIds = nameNode.fileBlocks.get(path);
        if (blockIds == null) throw new RuntimeException("file not found: " + path);
        StringBuilder sb = new StringBuilder();
        for (String blockId : blockIds) {
            BlockInfo info = nameNode.blockTable.get(blockId);
            String data = null;
            for (int nodeId : info.replicaNodes) {
                DataNode n = nodes.get(nodeId);
                if (n.up) { data = n.fetch(blockId); break; } // first live replica wins
            }
            if (data == null)
                throw new RuntimeException("block " + blockId + " unavailable (all replicas down)");
            sb.append(data);
        }
        return sb.toString();
    }

    void failNode(int nodeId) {
        nodes.get(nodeId).up = false;
        System.out.println("node " + nodeId + " marked DOWN");
    }

    void printMetadata(String path) {
        System.out.println("metadata for " + path + ":");
        for (String blockId : nameNode.fileBlocks.get(path)) {
            BlockInfo info = nameNode.blockTable.get(blockId);
            System.out.println("  " + blockId + " replicas=" + info.replicaNodes);
        }
    }

    void printClusterHealth() {
        for (DataNode n : nodes)
            System.out.println("  node " + n.id + " : " + (n.up ? "UP  " : "DOWN")
                    + " blocks=" + n.blocks.size());
    }
}
