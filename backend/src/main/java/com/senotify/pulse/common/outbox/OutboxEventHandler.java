package com.senotify.pulse.common.outbox;

import java.util.Set;

/**
 * Consumes outbox events. Delivery is at-least-once, so handlers must be idempotent.
 * In the scale-out topology each handler becomes a Kafka consumer in its own service.
 */
public interface OutboxEventHandler {

    Set<String> eventTypes();

    void handle(OutboxEvent event);
}
