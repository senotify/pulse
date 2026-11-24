package com.senotify.pulse.common.outbox;

import com.fasterxml.jackson.databind.JsonNode;

/** A domain event read back from the outbox for delivery. */
public record OutboxEvent(long id, String aggregateType, long aggregateId, String eventType, JsonNode payload,
                          int attempts) {

    public long longField(String name) {
        return payload.path(name).asLong();
    }

    public boolean hasField(String name) {
        JsonNode node = payload.get(name);
        return node != null && !node.isNull();
    }
}
