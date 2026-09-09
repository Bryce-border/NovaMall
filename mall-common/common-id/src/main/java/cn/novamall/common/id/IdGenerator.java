package cn.novamall.common.id;

/**
 * Small, process-local Snowflake generator for the first milestone.
 * A distributed worker id can replace the fixed worker id when deployment grows.
 */
public final class IdGenerator {

    private static final long EPOCH = 1735689600000L;
    private static final long WORKER_ID = 1L;
    private static final long SEQUENCE_MASK = 4095L;

    private static long lastTimestamp = -1L;
    private static long sequence;

    private IdGenerator() { }

    public static synchronized long nextId() {
        long timestamp = System.currentTimeMillis();
        if (timestamp < lastTimestamp) {
            throw new IllegalStateException("system clock moved backwards");
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                timestamp = waitForNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0;
        }
        lastTimestamp = timestamp;
        return ((timestamp - EPOCH) << 22) | (WORKER_ID << 12) | sequence;
    }

    private static long waitForNextMillis(long previousTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= previousTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
}
