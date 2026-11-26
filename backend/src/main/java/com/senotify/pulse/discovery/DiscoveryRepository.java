package com.senotify.pulse.discovery;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Queries behind the discovery surfaces. They scan tables directly, which is fine at portfolio
 * scale; at real scale suggestions and trends are precomputed offline and search uses OpenSearch.
 */
@Repository
class DiscoveryRepository {

    record UserResult(long id, String username, String displayName, String avatarKey, String bio,
                      long followers, boolean followedByMe) {
    }

    record Suggestion(long id, String username, String displayName, String avatarKey, long mutuals, long followers) {
    }

    private final JdbcClient jdbc;

    DiscoveryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<String> recentContents(OffsetDateTime since, int limit) {
        return jdbc.sql("""
                SELECT content FROM posts
                WHERE created_at >= :since AND content IS NOT NULL
                ORDER BY id DESC
                LIMIT :limit
                """)
                .param("since", since)
                .param("limit", limit)
                .query(String.class)
                .list();
    }

    /**
     * Accounts the viewer does not follow, ranked by how many of the viewer's follows follow them,
     * then by audience size, so new users with no graph still get sensible suggestions.
     */
    List<Suggestion> suggestions(long viewer, int limit) {
        return jdbc.sql("""
                SELECT u.id, u.username, u.display_name, u.avatar_key,
                       (SELECT COUNT(*) FROM follows f1
                        JOIN follows f2 ON f2.follower_id = f1.followee_id
                        WHERE f1.follower_id = :viewer AND f2.followee_id = u.id) AS mutuals,
                       (SELECT COUNT(*) FROM follows f WHERE f.followee_id = u.id) AS followers
                FROM users u
                WHERE u.id <> :viewer
                  AND u.id NOT IN (SELECT followee_id FROM follows WHERE follower_id = :viewer)
                  AND u.id NOT IN (SELECT blocked_id FROM blocks WHERE blocker_id = :viewer)
                  AND u.id NOT IN (SELECT blocker_id FROM blocks WHERE blocked_id = :viewer)
                ORDER BY mutuals DESC, followers DESC, u.id DESC
                LIMIT :limit
                """)
                .param("viewer", viewer)
                .param("limit", limit)
                .query(Suggestion.class)
                .list();
    }

    /** Ids of original posts whose text contains the term, newest first. */
    List<Long> searchPostIds(String term, Long beforeId, int limit) {
        return jdbc.sql("""
                SELECT id FROM posts
                WHERE repost_of_id IS NULL
                  AND LOWER(content) LIKE :pattern ESCAPE '\\'
                  AND (:before IS NULL OR id < :before)
                ORDER BY id DESC
                LIMIT :limit
                """)
                .param("pattern", "%" + escapeLike(term.toLowerCase(java.util.Locale.ROOT)) + "%")
                .param("before", beforeId, Types.BIGINT)
                .param("limit", limit)
                .query(Long.class)
                .list();
    }

    /**
     * People search over handles and display names, best match first: exact handle, handle prefix,
     * name prefix, then substring matches, with bigger accounts first within each group. Accounts
     * blocked in either direction never appear.
     */
    List<UserResult> searchUsers(String term, long viewer, int limit) {
        String t = escapeLike(term.toLowerCase(java.util.Locale.ROOT));
        return jdbc.sql("""
                SELECT u.id, u.username, u.display_name, u.avatar_key, u.bio,
                       (SELECT COUNT(*) FROM follows f WHERE f.followee_id = u.id) AS followers,
                       EXISTS (SELECT 1 FROM follows f
                                WHERE f.follower_id = :viewer AND f.followee_id = u.id) AS followed_by_me
                FROM users u
                WHERE (u.username LIKE :contains ESCAPE '\\' OR LOWER(u.display_name) LIKE :contains ESCAPE '\\')
                  AND NOT EXISTS (SELECT 1 FROM blocks b
                                   WHERE (b.blocker_id = u.id AND b.blocked_id = :viewer)
                                      OR (b.blocker_id = :viewer AND b.blocked_id = u.id))
                ORDER BY CASE
                             WHEN u.username = :exact THEN 0
                             WHEN u.username LIKE :prefix ESCAPE '\\' THEN 1
                             WHEN LOWER(u.display_name) LIKE :prefix ESCAPE '\\' THEN 2
                             ELSE 3
                         END,
                         followers DESC, u.id
                LIMIT :limit
                """)
                .param("contains", "%" + t + "%")
                .param("prefix", t + "%")
                .param("exact", term.toLowerCase(java.util.Locale.ROOT))
                .param("viewer", viewer)
                .param("limit", limit)
                .query(UserResult.class)
                .list();
    }

    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
