package com.senotify.pulse.common.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Polls the outbox. Disabled in tests, which call {@link OutboxRelay#drain()} directly. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "pulse.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
class OutboxRelayScheduler {

    private final OutboxRelay relay;

    OutboxRelayScheduler(OutboxRelay relay) {
        this.relay = relay;
    }

    @Scheduled(fixedDelayString = "${pulse.outbox.relay.interval-ms:500}")
    void tick() {
        relay.drain();
    }
}
