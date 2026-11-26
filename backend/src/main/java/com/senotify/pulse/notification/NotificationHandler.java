package com.senotify.pulse.notification;

import com.senotify.pulse.common.outbox.OutboxEvent;
import com.senotify.pulse.common.outbox.OutboxEventHandler;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Turns outbox events into notifications. Delivery is at-least-once, so every branch is idempotent:
 * a (event, recipient) pair is recorded at most once, and like groups are recomputed from the likes
 * table rather than incremented. Nobody is notified about their own actions, or by someone they
 * have a block with.
 */
@Component
class NotificationHandler implements OutboxEventHandler {

    private static final Pattern MENTION = Pattern.compile("(?<![A-Za-z0-9_])@([A-Za-z0-9_]{3,20})");
    private static final int MAX_MENTIONS = 10;

    private final NotificationRepository notifications;

    NotificationHandler(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @Override
    public Set<String> eventTypes() {
        return Set.of("PostLiked", "PostReposted", "PostCreated", "UserFollowed");
    }

    @Override
    public void handle(OutboxEvent event) {
        switch (event.eventType()) {
            case "PostLiked" -> liked(event);
            case "PostReposted" -> reposted(event);
            case "PostCreated" -> created(event);
            case "UserFollowed" -> followed(event);
            default -> {
            }
        }
    }

    private void liked(OutboxEvent event) {
        long postId = event.longField("postId");
        long actor = event.longField("userId");
        notifications.postAuthor(postId).ifPresent(owner -> {
            if (!eligible(owner, actor) || notifications.exists(event.id(), owner)) {
                return;
            }
            int count = notifications.likeCount(postId, owner);
            if (count > 0) { // everyone may have unliked again by the time the event is handled
                notifications.upsertLike(event.id(), owner, postId, actor, count);
            }
        });
    }

    private void reposted(OutboxEvent event) {
        long original = event.longField("originalId");
        long actor = event.longField("userId");
        notifications.postAuthor(original).ifPresent(owner -> notify(event, owner, "REPOST", actor, original));
    }

    private void created(OutboxEvent event) {
        long postId = event.longField("postId");
        long actor = event.longField("authorId");
        String content = notifications.postContent(postId).orElse(null);
        if (content == null) {
            return; // deleted before it was handled
        }
        Set<Long> notified = new LinkedHashSet<>();
        if (event.hasField("replyToId")) {
            notifications.postAuthor(event.longField("replyToId")).ifPresent(owner -> {
                if (notify(event, owner, "REPLY", actor, postId)) {
                    notified.add(owner);
                }
            });
        }
        Set<String> handles = new LinkedHashSet<>();
        Matcher matcher = MENTION.matcher(content);
        while (matcher.find() && handles.size() < MAX_MENTIONS) {
            handles.add(matcher.group(1).toLowerCase(Locale.ROOT));
        }
        for (long mentioned : notifications.userIdsByUsernames(handles)) {
            if (!notified.contains(mentioned)) { // a reply to someone who is also mentioned is one notification
                notify(event, mentioned, "MENTION", actor, postId);
            }
        }
    }

    private void followed(OutboxEvent event) {
        long follower = event.longField("followerId");
        long followee = event.longField("followeeId");
        if (eligible(followee, follower) && !notifications.exists(event.id(), followee)
                && !notifications.existsFollow(followee, follower)) {
            notifications.insert(event.id(), followee, "FOLLOW", follower, null);
        }
    }

    private boolean notify(OutboxEvent event, long recipient, String type, long actor, long postId) {
        if (!eligible(recipient, actor) || notifications.exists(event.id(), recipient)) {
            return false;
        }
        notifications.insert(event.id(), recipient, type, actor, postId);
        return true;
    }

    private boolean eligible(long recipient, long actor) {
        return recipient != actor && !notifications.blockedEitherWay(recipient, actor);
    }
}
