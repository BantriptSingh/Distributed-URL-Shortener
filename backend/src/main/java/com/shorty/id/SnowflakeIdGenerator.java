package com.shorty.id;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * 64-bit Snowflake: 41 bits timestamp, 10 bits worker, 12 bits sequence.
 * Internal PKs only — never encoded into the public short code.
 */
@Component
public class SnowflakeIdGenerator {

    private static final long EPOCH = 1_704_067_200_000L;
    private static final long WORKER_BITS = 10;
    private static final long SEQUENCE_BITS = 12;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;
    private static final long WORKER_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_BITS;

    private final long workerId;
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdGenerator() {
        this(new SecureRandom().nextInt() & 0x3FF);
    }

    public SnowflakeIdGenerator(long workerId) {
        long maxWorker = (1L << WORKER_BITS) - 1;
        if (workerId < 0 || workerId > maxWorker) {
            throw new IllegalArgumentException("workerId out of range");
        }
        this.workerId = workerId;
    }

    public synchronized long nextId() {
        long now = System.currentTimeMillis();
        if (now < lastTimestamp) {
            now = lastTimestamp;
        }
        if (now == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                now = waitNextMillis(now);
            }
        } else {
            sequence = 0;
        }
        lastTimestamp = now;
        return ((now - EPOCH) << TIMESTAMP_SHIFT) | (workerId << WORKER_SHIFT) | sequence;
    }

    private long waitNextMillis(long current) {
        long now = System.currentTimeMillis();
        while (now <= current) {
            now = System.currentTimeMillis();
        }
        return now;
    }
}
