package com.senotify.pulse.notification;

import com.senotify.pulse.identity.UserSummary;
import java.time.OffsetDateTime;

/**
 * One notification. {@code type} is LIKE, REPOST, REPLY, FOLLOW or MENTION. {@code actor} is the
 * most recent person behind it and {@code actorCount} how many there are (likes on one post are
 * grouped). {@code postId} and {@code postPreview} describe the subject: your post for LIKE and
 * REPOST, the new reply or mentioning post for REPLY and MENTION, and null for FOLLOW.
 */
public record NotificationView(long id, String type, UserSummary actor, int actorCount, Long postId,
                               String postPreview, boolean read, OffsetDateTime createdAt) {
}
