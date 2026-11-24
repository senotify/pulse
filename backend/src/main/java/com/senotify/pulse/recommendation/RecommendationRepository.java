package com.senotify.pulse.recommendation;

import com.senotify.pulse.recommendation.Candidate.CandidatePost;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Candidate retrieval and feature lookups, each a single batched query. */
@Repository
class RecommendationRepository {

    record SocialProof(long postId, long proofCount, String sampleUsername) {
    }

    record IdValue(long id, double amount) {
    }

    /** Original top-level posts only: replies and reposts are never candidates themselves. */
    private static final String CANDIDATE_COLUMNS = """
            SELECT p.id AS post_id, p.author_id, p.content, p.created_at, p.like_count, p.reply_count,
                   p.repost_count
            FROM posts p
            WHERE p.reply_to_id IS NULL AND p.repost_of_id IS NULL
            """;

    private static final String FOLLOWING = "SELECT followee_id FROM follows WHERE follower_id = :viewer";

    private final JdbcClient jdbc;

    RecommendationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<CandidatePost> inNetwork(long viewer, OffsetDateTime since, int limit) {
        return candidates(CANDIDATE_COLUMNS + """
                  AND p.author_id IN (%s) AND p.created_at >= :since
                ORDER BY p.id DESC LIMIT :limit
                """.formatted(FOLLOWING), viewer, since, limit);
    }

    List<CandidatePost> engagedByFollowing(long viewer, OffsetDateTime since, int limit) {
        return candidates(CANDIDATE_COLUMNS + """
                  AND p.id IN (
                      SELECT l.post_id FROM likes l
                      WHERE l.user_id IN (%1$s) AND l.created_at >= :since
                      UNION
                      SELECT r.repost_of_id FROM posts r
                      WHERE r.author_id IN (%1$s) AND r.repost_of_id IS NOT NULL AND r.created_at >= :since)
                  AND p.author_id <> :viewer AND p.author_id NOT IN (%1$s)
                ORDER BY p.id DESC LIMIT :limit
                """.formatted(FOLLOWING), viewer, since, limit);
    }

    List<CandidatePost> followedByFollowing(long viewer, OffsetDateTime since, int limit) {
        return candidates(CANDIDATE_COLUMNS + """
                  AND p.author_id IN (
                      SELECT f2.followee_id FROM follows f1
                      JOIN follows f2 ON f2.follower_id = f1.followee_id
                      WHERE f1.follower_id = :viewer)
                  AND p.author_id <> :viewer AND p.author_id NOT IN (%s)
                  AND p.created_at >= :since
                ORDER BY p.id DESC LIMIT :limit
                """.formatted(FOLLOWING), viewer, since, limit);
    }

    List<CandidatePost> trending(long viewer, OffsetDateTime since, int limit) {
        return candidates(CANDIDATE_COLUMNS + """
                  AND p.created_at >= :since AND p.author_id <> :viewer
                ORDER BY (p.like_count + 2 * p.repost_count + p.reply_count) DESC, p.id DESC
                LIMIT :limit
                """, viewer, since, limit);
    }

    /** Authors the viewer must never be shown: blocked in either direction, or muted. */
    Set<Long> excludedAuthors(long viewer) {
        return new HashSet<>(jdbc.sql("""
                SELECT blocked_id FROM blocks WHERE blocker_id = :viewer
                UNION SELECT blocker_id FROM blocks WHERE blocked_id = :viewer
                UNION SELECT muted_id FROM mutes WHERE muter_id = :viewer
                """)
                .param("viewer", viewer)
                .query(Long.class)
                .list());
    }

    Set<Long> seen(long viewer, Collection<Long> postIds, OffsetDateTime since) {
        if (postIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.sql("""
                SELECT DISTINCT post_id FROM interaction_events
                WHERE user_id = :viewer AND type = 'IMPRESSION' AND created_at >= :since AND post_id IN (:ids)
                """)
                .param("viewer", viewer)
                .param("since", since)
                .param("ids", postIds)
                .query(Long.class)
                .list());
    }

    Map<Long, Double> followerCounts(Collection<Long> authorIds) {
        if (authorIds.isEmpty()) {
            return Map.of();
        }
        return toMap(jdbc.sql("""
                SELECT followee_id AS id, COUNT(*) AS amount FROM follows
                WHERE followee_id IN (:ids) GROUP BY followee_id
                """)
                .param("ids", authorIds)
                .query(IdValue.class)
                .list());
    }

    /**
     * How much the viewer has engaged with each author recently. Replies weigh most, then reposts,
     * likes and clicks.
     */
    Map<Long, Double> authorAffinity(long viewer, Collection<Long> authorIds, OffsetDateTime since) {
        if (authorIds.isEmpty()) {
            return Map.of();
        }
        return toMap(jdbc.sql("""
                SELECT a.author_id AS id, SUM(a.w) AS amount FROM (
                    SELECT p.author_id, 1.0 AS w FROM likes l JOIN posts p ON p.id = l.post_id
                    WHERE l.user_id = :viewer AND l.created_at >= :since
                    UNION ALL
                    SELECT p.author_id, 2.0 AS w FROM posts r JOIN posts p ON p.id = r.reply_to_id
                    WHERE r.author_id = :viewer AND r.created_at >= :since
                    UNION ALL
                    SELECT p.author_id, 1.5 AS w FROM posts r JOIN posts p ON p.id = r.repost_of_id
                    WHERE r.author_id = :viewer AND r.created_at >= :since
                    UNION ALL
                    SELECT p.author_id, 0.5 AS w FROM interaction_events e JOIN posts p ON p.id = e.post_id
                    WHERE e.user_id = :viewer AND e.type = 'CLICK' AND e.created_at >= :since
                ) a
                WHERE a.author_id IN (:ids)
                GROUP BY a.author_id
                """)
                .param("viewer", viewer)
                .param("since", since)
                .param("ids", authorIds)
                .query(IdValue.class)
                .list());
    }

    /** Per post: how many accounts the viewer follows liked or reposted it, and one of their handles. */
    Map<Long, SocialProof> socialProof(long viewer, Collection<Long> postIds) {
        if (postIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, SocialProof> result = new HashMap<>();
        jdbc.sql("""
                SELECT e.post_id, COUNT(DISTINCT e.user_id) AS proof_count, MIN(u.username) AS sample_username
                FROM (
                    SELECT l.post_id, l.user_id FROM likes l WHERE l.post_id IN (:ids)
                    UNION ALL
                    SELECT r.repost_of_id AS post_id, r.author_id AS user_id FROM posts r
                    WHERE r.repost_of_id IN (:ids)
                ) e
                JOIN follows f ON f.followee_id = e.user_id AND f.follower_id = :viewer
                JOIN users u ON u.id = e.user_id
                GROUP BY e.post_id
                """)
                .param("viewer", viewer)
                .param("ids", postIds)
                .query(SocialProof.class)
                .list()
                .forEach(proof -> result.put(proof.postId(), proof));
        return result;
    }

    /** The viewer's most recently liked post texts, the basis of their content profile. */
    List<String> likedContents(long viewer, int limit) {
        return jdbc.sql("""
                SELECT p.content FROM likes l JOIN posts p ON p.id = l.post_id
                WHERE l.user_id = :viewer AND p.content IS NOT NULL
                ORDER BY l.created_at DESC
                LIMIT :limit
                """)
                .param("viewer", viewer)
                .param("limit", limit)
                .query(String.class)
                .list();
    }

    private List<CandidatePost> candidates(String sql, long viewer, OffsetDateTime since, int limit) {
        return jdbc.sql(sql)
                .param("viewer", viewer)
                .param("since", since)
                .param("limit", limit)
                .query(CandidatePost.class)
                .list();
    }

    private static Map<Long, Double> toMap(List<IdValue> rows) {
        Map<Long, Double> map = new HashMap<>();
        rows.forEach(row -> map.put(row.id(), row.amount()));
        return map;
    }
}
