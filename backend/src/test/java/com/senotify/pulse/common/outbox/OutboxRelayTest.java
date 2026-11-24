package com.senotify.pulse.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class OutboxRelayTest {

    record State(int attempts, OffsetDateTime publishedAt, OffsetDateTime nextAttemptAt, String lastError) {
    }

    @Autowired
    OutboxRelay relay;

    @Autowired
    JdbcClient jdbc;

    @Test
    void deliversAndMarksPublished() {
        long id = insert("{\"fail\":false}", 0);
        relay.drain();
        State state = state(id);
        assertThat(state.publishedAt()).isNotNull();
        assertThat(state.attempts()).isZero();
    }

    @Test
    void failedDeliveryBacksOffAndIsNotRetriedEarly() {
        long id = insert("{\"fail\":true}", 0);
        relay.drain();
        State state = state(id);
        assertThat(state.publishedAt()).isNull();
        assertThat(state.attempts()).isEqualTo(1);
        assertThat(state.nextAttemptAt()).isAfter(OffsetDateTime.now());
        assertThat(state.lastError()).contains("boom");

        relay.drain();
        assertThat(state(id).attempts()).isEqualTo(1);
    }

    @Test
    void deadLettersAfterMaxAttempts() {
        long id = insert("{\"fail\":true}", OutboxRelay.MAX_ATTEMPTS - 1);
        relay.drain();
        State state = state(id);
        assertThat(state.attempts()).isEqualTo(OutboxRelay.MAX_ATTEMPTS);
        assertThat(state.publishedAt()).isNotNull();
        assertThat(state.lastError()).contains("boom");
    }

    private long insert(String payload, int attempts) {
        jdbc.sql("""
                INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, payload, attempts)
                VALUES ('test', 1, 'TestBoom', :payload, :attempts)
                """)
                .params(Map.of("payload", payload, "attempts", attempts))
                .update();
        return jdbc.sql("SELECT MAX(id) FROM outbox_events").query(Long.class).single();
    }

    private State state(long id) {
        return jdbc.sql("""
                SELECT attempts, published_at, next_attempt_at, last_error FROM outbox_events WHERE id = :id
                """)
                .param("id", id)
                .query(State.class)
                .single();
    }
}
