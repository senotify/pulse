package com.senotify.pulse.timeline;

import java.sql.Types;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** PostgreSQL reads behind the timeline: fan-out targets, rebuilds and the pull side of hybrid fan-out. */
@Repository
class TimelineRepository {

    private final JdbcClient jdbc;

    TimelineRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<Long> followerIds(long userId) {
        return jdbc.sql("SELECT follower_id FROM follows WHERE followee_id = :u")
                .param("u", userId)
                .query(Long.class)
                .list();
    }

    long followerCount(long userId) {
        return jdbc.sql("SELECT COUNT(*) FROM follows WHERE followee_id = :u")
                .param("u", userId)
                .query(Long.class)
                .single();
    }

    /** Accounts the user follows that are big enough to be merged at read time rather than fanned out. */
    List<Long> followedHighReachAuthors(long userId, int threshold) {
        return jdbc.sql("""
                SELECT f.followee_id FROM follows f
                WHERE f.follower_id = :u
                  AND (SELECT COUNT(*) FROM follows c WHERE c.followee_id = f.followee_id) >= :threshold
                """)
                .param("u", userId)
                .param("threshold", threshold)
                .query(Long.class)
                .list();
    }

    Set<Long> mutedIds(long userId) {
        return new HashSet<>(jdbc.sql("SELECT muted_id FROM mutes WHERE muter_id = :u")
                .param("u", userId)
                .query(Long.class)
                .list());
    }

    /**
     * Home timeline straight from PostgreSQL: the user's own posts plus those of everyone they follow,
     * replies excluded. Used to rebuild the cache and as the fallback when Redis is unavailable.
     */
    List<TimelineEntry> home(long userId, Long beforeId, int limit) {
        return jdbc.sql("""
                SELECT p.id AS post_id, p.author_id FROM posts p
                WHERE p.reply_to_id IS NULL
                  AND (p.author_id = :u
                       OR p.author_id IN (SELECT followee_id FROM follows WHERE follower_id = :u))
                  AND (:before IS NULL OR p.id < :before)
                ORDER BY p.id DESC
                LIMIT :limit
                """)
                .param("u", userId)
                .param("before", beforeId, Types.BIGINT)
                .param("limit", limit)
                .query(TimelineEntry.class)
                .list();
    }

    /** Recent top-level posts and reposts by the given authors, newest first. */
    List<TimelineEntry> recentByAuthors(Collection<Long> authorIds, Long beforeId, int limit) {
        if (authorIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                SELECT p.id AS post_id, p.author_id FROM posts p
                WHERE p.reply_to_id IS NULL
                  AND p.author_id IN (:authors)
                  AND (:before IS NULL OR p.id < :before)
                ORDER BY p.id DESC
                LIMIT :limit
                """)
                .param("authors", authorIds)
                .param("before", beforeId, Types.BIGINT)
                .param("limit", limit)
                .query(TimelineEntry.class)
                .list();
    }
}
