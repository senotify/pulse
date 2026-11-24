package com.senotify.pulse.post;

import com.senotify.pulse.identity.UserSummary;
import java.time.OffsetDateTime;

/**
 * A post as the viewer sees it. For a repost, {@code content} is null and {@code repostOf}
 * holds the original; counters on a repost itself stay at zero, since engagement goes to the original.
 */
public record PostView(long id, UserSummary author, String content, Long replyToId, PostView repostOf,
                       int likeCount, int replyCount, int repostCount, boolean likedByMe,
                       boolean repostedByMe, OffsetDateTime createdAt) {
}
