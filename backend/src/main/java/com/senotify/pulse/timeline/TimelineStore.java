package com.senotify.pulse.timeline;

import java.util.Collection;
import java.util.List;

/**
 * Materialized home timelines: per user, post ids ordered newest first. Post ids are monotonic,
 * so the id doubles as the sort key and the pagination cursor.
 *
 * <p>A timeline is either absent (never built, or expired after the owner stopped reading) or
 * complete. Writes to an absent timeline are dropped, never applied, so a partial timeline can
 * never pass for a complete one; the next read rebuilds it from PostgreSQL instead.
 */
public interface TimelineStore {

    boolean exists(long userId);

    /** Adds entries only if the timeline exists, trimming it to capacity. */
    void addIfPresent(long userId, Collection<TimelineEntry> entries);

    /** Replaces the timeline with exactly these entries and marks it present, even when empty. */
    void replace(long userId, List<TimelineEntry> entries);

    /** Entries with {@code postId < beforeId} (all when null), newest first. Refreshes the idle TTL. */
    List<TimelineEntry> page(long userId, Long beforeId, int count);

    void removeAuthor(long userId, long authorId);

    void evict(long userId);
}
