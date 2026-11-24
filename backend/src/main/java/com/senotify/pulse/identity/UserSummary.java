package com.senotify.pulse.identity;

/** The public, minimal view of a user that other modules embed (post authors, follower lists). */
public record UserSummary(long id, String username, String displayName) {
}
