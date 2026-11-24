package com.senotify.pulse.interaction;

import java.sql.Types;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class InteractionRepository {

    record Event(long userId, long postId, String type, String surface, Integer rankPosition, Integer dwellMs,
                 String modelVersion, Long outboxEventId) {
    }

    private final JdbcClient jdbc;

    InteractionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void insert(Event event) {
        jdbc.sql("""
                INSERT INTO interaction_events
                    (user_id, post_id, type, surface, rank_position, dwell_ms, model_version, outbox_event_id)
                VALUES (:user, :post, :type, :surface, :position, :dwell, :model, :outbox)
                ON CONFLICT DO NOTHING
                """)
                .param("user", event.userId())
                .param("post", event.postId())
                .param("type", event.type())
                .param("surface", event.surface(), Types.VARCHAR)
                .param("position", event.rankPosition(), Types.INTEGER)
                .param("dwell", event.dwellMs(), Types.INTEGER)
                .param("model", event.modelVersion(), Types.VARCHAR)
                .param("outbox", event.outboxEventId(), Types.BIGINT)
                .update();
    }
}
