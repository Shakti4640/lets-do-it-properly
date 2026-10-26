/*
 * Distributed ID Generator (Snowflake-like) - Low Level Design
 * ------------------------------------------------------------
 * Generates 64-bit unique, roughly time-ordered IDs:
 *   [ 1 sign bit | 41 timestamp bits | 10 machine bits | 12 sequence bits ]
 * Handles same-millisecond collisions via sequence and clock-rollback guard.
 *
 * Key classes:
 *   - SnowflakeIdGenerator
 *
 * Patterns used: bit-packing, per-instance state.
 */

public class DistributedIDGeneratorDemo {
    public static void main(String[] args) {
        SnowflakeIdGenerator gen = new SnowflakeIdGenerator(1, 1);
        System.out.println("=== Distributed ID Generator Demo ===");

        long prev = -1;
        for (int i = 0; i < 5; i++) {
            long id = gen.nextId();
            System.out.println("ID " + i + ": " + id + "  (monotonic=" + (id > prev) + ")");
            prev = id;
        }

        // Two generators on different machines produce different IDs
        SnowflakeIdGenerator gen2 = new SnowflakeIdGenerator(2, 1);
        System.out.println("Machine1: " + gen.nextId());
        System.out.println("Machine2: " + gen2.nextId());
    }
}

class SnowflakeIdGenerator {
    private static final long EPOCH = 1700000000000L; // custom epoch
    private static final long MACHINE_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;
    private static final long MAX_MACHINE_ID = (1L << MACHINE_BITS) - 1;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;
    private static final long MACHINE_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + MACHINE_BITS;

    private final long machineId;
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    SnowflakeIdGenerator(long machineId, long datacenterId) {
        if (machineId < 0 || machineId > MAX_MACHINE_ID)
            throw new IllegalArgumentException("machineId out of range");
        this.machineId = machineId;
    }

    synchronized long nextId() {
        long timestamp = System.currentTimeMillis();
        if (timestamp < lastTimestamp) {
            throw new IllegalStateException("Clock moved backwards; refusing to generate id");
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                // sequence exhausted this ms; spin to next ms
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (machineId << MACHINE_SHIFT)
                | sequence;
    }

    private long waitNextMillis(long last) {
        long ts = System.currentTimeMillis();
        while (ts <= last) ts = System.currentTimeMillis();
        return ts;
    }
}
