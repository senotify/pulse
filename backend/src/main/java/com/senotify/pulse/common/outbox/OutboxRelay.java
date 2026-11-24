package com.senotify.pulse.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * In-process outbox relay: reads due events in id order and hands them to every handler that
 * subscribes to the type. A failure backs the event off exponentially (2^attempts seconds, capped
 * at 10 minutes) and after {@link #MAX_ATTEMPTS} the event is dead-lettered.
 *
 * <p>Runs on a single instance. Several instances would need {@code FOR UPDATE SKIP LOCKED};
 * at that scale Debezium publishing to Kafka replaces this class.
 */
@Component
public class OutboxRelay {

    static final int MAX_ATTEMPTS = 10;
    private static final int BATCH_SIZE = 100;
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(10);
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private record Row(long id, String aggregateType, long aggregateId, String eventType, String payload,
                       int attempts) {
    }

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    private final List<OutboxEventHandler> handlers;
    private final Clock clock;

    public OutboxRelay(JdbcClient jdbc, ObjectMapper mapper, List<OutboxEventHandler> handlers) {
        this(jdbc, mapper, handlers, Clock.systemUTC());
    }

    OutboxRelay(JdbcClient jdbc, ObjectMapper mapper, List<OutboxEventHandler> handlers, Clock clock) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.handlers = handlers;
        this.clock = clock;
    }

    /** Delivers every event that is currently due. Returns the number of events processed. */
    public synchronized int drain() {
        int processed = 0;
        while (true) {
            List<Row> batch = due();
            boolean progressed = false;
            for (Row row : batch) {
                if (deliver(row)) {
                    progressed = true;
                }
                processed++;
            }
            // Stop when the queue is empty or everything left in it is backing off.
            if (batch.size() < BATCH_SIZE || !progressed) {
                return processed;
            }
        }
    }

    private List<Row> due() {
        return jdbc.sql("""
                SELECT id, aggregate_type, aggregate_id, event_type, payload, attempts
                FROM outbox_events
                WHERE published_at IS NULL AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
                ORDER BY id
                LIMIT :limit
                """)
                .param("now", now())
                .param("limit", BATCH_SIZE)
                .query(Row.class)
                .list();
    }

    /** @return true when the event was delivered (or dead-lettered), false when it was deferred */
    private boolean deliver(Row row) {
        try {
            OutboxEvent event = new OutboxEvent(row.id(), row.aggregateType(), row.aggregateId(), row.eventType(),
                    mapper.readTree(row.payload()), row.attempts());
            for (OutboxEventHandler handler : handlers) {
                if (handler.eventTypes().contains(event.eventType())) {
                    handler.handle(event);
                }
            }
            jdbc.sql("UPDATE outbox_events SET published_at = :now, last_error = NULL WHERE id = :id")
                    .param("now", now())
                    .param("id", row.id())
                    .update();
            return true;
        } catch (JsonProcessingException | RuntimeException e) {
            return fail(row, e);
        }
    }

    private boolean fail(Row row, Exception e) {
        int attempts = row.attempts() + 1;
        String error = truncate(e.getClass().getSimpleName() + ": " + e.getMessage());
        boolean dead = attempts >= MAX_ATTEMPTS;
        Duration backoff = Duration.ofSeconds(1L << Math.min(attempts, 20));
        if (backoff.compareTo(MAX_BACKOFF) > 0) {
            backoff = MAX_BACKOFF;
        }
        if (dead) {
            log.error("Dead-lettering outbox event {} ({}) after {} attempts: {}", row.id(), row.eventType(),
                    attempts, error);
        } else {
            log.warn("Outbox event {} ({}) failed, attempt {}: {}", row.id(), row.eventType(), attempts, error);
        }
        jdbc.sql("""
                UPDATE outbox_events
                SET attempts = :attempts, last_error = :error, next_attempt_at = :next,
                    published_at = :published
                WHERE id = :id
                """)
                .param("attempts", attempts)
                .param("error", error)
                .param("next", now().plus(backoff))
                .param("published", dead ? now() : null, java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
                .param("id", row.id())
                .update();
        return dead;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private static String truncate(String value) {
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
