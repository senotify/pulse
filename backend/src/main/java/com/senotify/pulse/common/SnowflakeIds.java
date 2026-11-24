package com.senotify.pulse.common;

import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Snowflake-style ids for posts: not a guessable 1, 2, 3 sequence (so ids don't reveal how many
 * posts exist), but still ordered by creation time, which feeds, Redis timeline scores and
 * keyset cursors all rely on.
 *
 * <p>Layout, 53 bits in total so ids stay exact as JavaScript numbers and as Redis sorted-set
 * scores (both IEEE-754 doubles):
 * <pre>
 *   | 41 bits: ms since 2026-01-01 (~69 years) | 2 bits: worker | 10 bits: sequence |
 * </pre>
 * That allows 4 workers and 1,024 ids per millisecond per worker. X's original Snowflake uses
 * 64 bits and has to send ids as strings to browsers; this layout trades worker count for
 * plain JSON numbers.
 *
 * <p>If the clock moves backwards, ids keep using the last timestamp seen rather than going
 * backwards, so uniqueness and ordering hold without blocking.
 */
@Component
public class SnowflakeIds {

    static final long EPOCH_MS = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli();
    static final int WORKER_BITS = 2;
    static final int SEQUENCE_BITS = 10;
    static final long MAX_WORKER = (1L << WORKER_BITS) - 1;
    static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;

    private final long worker;
    private final Clock clock;
    private long lastMs = -1;
    private long sequence;

    @Autowired
    public SnowflakeIds(@Value("${pulse.ids.worker:0}") long worker) {
        this(worker, Clock.systemUTC());
    }

    SnowflakeIds(long worker, Clock clock) {
        if (worker < 0 || worker > MAX_WORKER) {
            throw new IllegalArgumentException("pulse.ids.worker must be 0-" + MAX_WORKER);
        }
        this.worker = worker;
        this.clock = clock;
    }

    public synchronized long next() {
        long now = Math.max(clock.millis() - EPOCH_MS, lastMs); // never step backwards
        if (now == lastMs) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                now = lastMs + 1; // sequence exhausted for this ms: borrow the next one
            }
        } else {
            sequence = 0;
        }
        lastMs = now;
        return (now << (WORKER_BITS + SEQUENCE_BITS)) | (worker << SEQUENCE_BITS) | sequence;
    }

    /** When an id was generated, for debugging and analytics. */
    public static Instant timestampOf(long id) {
        return Instant.ofEpochMilli((id >>> (WORKER_BITS + SEQUENCE_BITS)) + EPOCH_MS);
    }
}
