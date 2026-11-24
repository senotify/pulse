package com.senotify.pulse.timeline;

/**
 * One cached timeline item. The author travels with the id so an unfollow can drop that author's
 * entries without a database round trip. For a repost, the author is the reposter.
 */
public record TimelineEntry(long postId, long authorId) {

    String member() {
        return postId + ":" + authorId;
    }

    static TimelineEntry parse(String member) {
        int colon = member.indexOf(':');
        return new TimelineEntry(Long.parseLong(member.substring(0, colon)),
                Long.parseLong(member.substring(colon + 1)));
    }
}
