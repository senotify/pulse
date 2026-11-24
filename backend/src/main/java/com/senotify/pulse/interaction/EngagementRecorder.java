package com.senotify.pulse.interaction;

import com.senotify.pulse.common.outbox.OutboxEvent;
import com.senotify.pulse.common.outbox.OutboxEventHandler;
import com.senotify.pulse.interaction.InteractionRepository.Event;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Copies server-side engagement (likes, reposts, replies) from the outbox into the interaction log,
 * so training data lives in one table. The outbox event id makes redelivery a no-op.
 */
@Component
class EngagementRecorder implements OutboxEventHandler {

    private final InteractionRepository interactions;

    EngagementRecorder(InteractionRepository interactions) {
        this.interactions = interactions;
    }

    @Override
    public Set<String> eventTypes() {
        return Set.of("PostLiked", "PostReposted", "PostCreated");
    }

    @Override
    public void handle(OutboxEvent event) {
        switch (event.eventType()) {
            case "PostLiked" -> record(event, event.longField("userId"), event.longField("postId"), "LIKE");
            case "PostReposted" -> record(event, event.longField("userId"), event.longField("originalId"), "REPOST");
            case "PostCreated" -> {
                if (event.hasField("replyToId")) {
                    record(event, event.longField("authorId"), event.longField("replyToId"), "REPLY");
                }
            }
            default -> {
            }
        }
    }

    private void record(OutboxEvent event, long userId, long postId, String type) {
        interactions.insert(new Event(userId, postId, type, null, null, null, null, event.id()));
    }
}
