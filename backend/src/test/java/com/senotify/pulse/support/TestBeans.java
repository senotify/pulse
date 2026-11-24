package com.senotify.pulse.support;

import com.senotify.pulse.common.outbox.OutboxEvent;
import com.senotify.pulse.common.outbox.OutboxEventHandler;
import com.senotify.pulse.recommendation.FeedSnapshotStore;
import com.senotify.pulse.recommendation.RankedEntry;
import com.senotify.pulse.timeline.InMemoryTimelineStore;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import com.senotify.pulse.timeline.TimelineProperties;
import com.senotify.pulse.timeline.TimelineStore;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** Picked up by component scanning in every test context, so all tests share one context. */
@Configuration
public class TestBeans {

    @Bean
    @Primary
    TimelineStore inMemoryTimelineStore(TimelineProperties properties) {
        return new InMemoryTimelineStore(properties.capacity());
    }

    @Bean
    @Primary
    FeedSnapshotStore inMemoryFeedSnapshotStore() {
        Map<String, List<RankedEntry>> snapshots = new ConcurrentHashMap<>();
        return new FeedSnapshotStore() {
            @Override
            public void save(long viewer, String token, List<RankedEntry> entries) {
                snapshots.put(viewer + ":" + token, List.copyOf(entries));
            }

            @Override
            public Optional<List<RankedEntry>> load(long viewer, String token) {
                return Optional.ofNullable(snapshots.get(viewer + ":" + token));
            }
        };
    }

    /** Fails on demand, for exercising relay retries. */
    @Bean
    OutboxEventHandler testBoomHandler() {
        return new OutboxEventHandler() {
            @Override
            public Set<String> eventTypes() {
                return Set.of("TestBoom");
            }

            @Override
            public void handle(OutboxEvent event) {
                if (event.payload().path("fail").asBoolean()) {
                    throw new IllegalStateException("boom");
                }
            }
        };
    }
}
