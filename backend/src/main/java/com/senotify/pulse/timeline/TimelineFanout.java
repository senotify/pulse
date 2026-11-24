package com.senotify.pulse.timeline;

import com.senotify.pulse.common.outbox.OutboxEvent;
import com.senotify.pulse.common.outbox.OutboxEventHandler;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Keeps materialized timelines in step with the outbox. Every operation is idempotent (sorted-set
 * adds and removes), so redelivery after a failure is harmless.
 *
 * <p>Hybrid fan-out: a post is pushed to the author's own timeline and, unless the author has at
 * least {@code fanoutThreshold} followers, to every follower's. High-reach authors are pulled and
 * merged at read time instead, which bounds the write cost of a single post.
 *
 * <p>Deleted posts are not chased through timelines: rendering skips ids that no longer exist.
 */
@Component
class TimelineFanout implements OutboxEventHandler {

    private final TimelineStore store;
    private final TimelineRepository repository;
    private final TimelineProperties properties;

    TimelineFanout(TimelineStore store, TimelineRepository repository, TimelineProperties properties) {
        this.store = store;
        this.repository = repository;
        this.properties = properties;
    }

    @Override
    public Set<String> eventTypes() {
        return Set.of("PostCreated", "PostReposted", "UserFollowed", "UserUnfollowed", "UserBlocked");
    }

    @Override
    public void handle(OutboxEvent event) {
        switch (event.eventType()) {
            case "PostCreated" -> {
                if (!event.hasField("replyToId")) {
                    fanOut(new TimelineEntry(event.longField("postId"), event.longField("authorId")));
                }
            }
            case "PostReposted" -> fanOut(new TimelineEntry(event.longField("postId"), event.longField("userId")));
            case "UserFollowed" -> backfill(event.longField("followerId"), event.longField("followeeId"));
            case "UserUnfollowed" -> store.removeAuthor(event.longField("followerId"), event.longField("followeeId"));
            case "UserBlocked" -> {
                long blocker = event.longField("blockerId");
                long blocked = event.longField("blockedId");
                store.removeAuthor(blocker, blocked);
                store.removeAuthor(blocked, blocker);
            }
            default -> {
            }
        }
    }

    private void fanOut(TimelineEntry entry) {
        List<TimelineEntry> one = List.of(entry);
        store.addIfPresent(entry.authorId(), one);
        if (isHighReach(entry.authorId())) {
            return;
        }
        for (long follower : repository.followerIds(entry.authorId())) {
            store.addIfPresent(follower, one);
        }
    }

    /** Copies the new followee's recent posts in, so the timeline doesn't wait for their next post. */
    private void backfill(long follower, long followee) {
        if (isHighReach(followee)) {
            return; // merged at read time
        }
        store.addIfPresent(follower, repository.recentByAuthors(List.of(followee), null, properties.followBackfill()));
    }

    private boolean isHighReach(long authorId) {
        return repository.followerCount(authorId) >= properties.fanoutThreshold();
    }
}
