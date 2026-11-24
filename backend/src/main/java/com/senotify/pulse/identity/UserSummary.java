package com.senotify.pulse.identity;

/**
 * The public, minimal view of a user that other modules embed (post authors, follower lists).
 * {@code avatarKey} is null without a photo; otherwise the image is at /api/avatars/{id}?v={avatarKey}.
 */
public record UserSummary(long id, String username, String displayName, String avatarKey) {
}
