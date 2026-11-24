package com.senotify.pulse.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Appends domain events to the transactional outbox. It must run inside the transaction that
 * changes the state, so the event and the change commit or roll back together.
 */
@Component
public class OutboxWriter {

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    public OutboxWriter(JdbcClient jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String aggregateType, long aggregateId, String eventType, Map<String, ?> payload) {
        String json;
        try {
            json = mapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize " + eventType, e);
        }
        jdbc.sql("""
                INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, payload)
                VALUES (:type, :id, :event, :payload)
                """)
                .param("type", aggregateType)
                .param("id", aggregateId)
                .param("event", eventType)
                .param("payload", json)
                .update();
    }
}
