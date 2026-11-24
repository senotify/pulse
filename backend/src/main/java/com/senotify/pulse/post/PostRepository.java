package com.senotify.pulse.post;

import com.senotify.pulse.common.SnowflakeIds;
import com.senotify.pulse.identity.UserSummary;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PostRepository {

    /** The fields needed for authorization and bookkeeping. */
    record PostCore(long id, long authorId, Long replyToId, Long repostOfId) {
    }

    /** A flat row for one post as seen by a viewer; reposts are hydrated in a second query. */
    record PostRow(long id, long authorId, String username, String displayName, String avatarKey, String content,
                   Long replyToId, Long replyToUserId, String replyToUsername, String replyToDisplayName,
                   String replyToAvatarKey,
                   Long repostOfId, int likeCount, int replyCount, int repostCount,
                   boolean likedByMe, boolean repostedByMe, OffsetDateTime createdAt) {

        PostView toView(PostView original) {
            UserSummary replyTo = replyToUserId == null
                    ? null
                    : new UserSummary(replyToUserId, replyToUsername, replyToDisplayName, replyToAvatarKey);
            return new PostView(id, new UserSummary(authorId, username, displayName, avatarKey), content, replyToId, replyTo,
                    List.of(), original, likeCount, replyCount, repostCount, likedByMe, repostedByMe, createdAt);
        }
    }

    enum Counter {
        LIKES("like_count"), REPLIES("reply_count"), REPOSTS("repost_count");

        final String column;

        Counter(String column) {
            this.column = column;
        }
    }

    private static final String SELECT_VIEW = """
            SELECT p.id, p.author_id, u.username, u.display_name, u.avatar_key, p.content, p.reply_to_id,
                   pu.id AS reply_to_user_id, pu.username AS reply_to_username,
                   pu.display_name AS reply_to_display_name, pu.avatar_key AS reply_to_avatar_key,
                   p.repost_of_id, p.like_count, p.reply_count, p.repost_count, p.created_at,
                   EXISTS (SELECT 1 FROM likes l
                            WHERE l.post_id = p.id AND l.user_id = :viewer) AS liked_by_me,
                   EXISTS (SELECT 1 FROM posts r
                            WHERE r.repost_of_id = p.id AND r.author_id = :viewer) AS reposted_by_me
            FROM posts p
            JOIN users u ON u.id = p.author_id
            LEFT JOIN posts parent ON parent.id = p.reply_to_id
            LEFT JOIN users pu ON pu.id = parent.author_id
            WHERE NOT EXISTS (SELECT 1 FROM blocks b
                               WHERE (b.blocker_id = p.author_id AND b.blocked_id = :viewer)
                                  OR (b.blocker_id = :viewer AND b.blocked_id = p.author_id))
            """;

    private final JdbcClient jdbc;
    private final SnowflakeIds ids;

    PostRepository(JdbcClient jdbc, SnowflakeIds ids) {
        this.jdbc = jdbc;
        this.ids = ids;
    }

    /** Inserts a post under a fresh Snowflake id, which is time-ordered like the old sequence. */
    long insert(long authorId, String content, Long replyToId, Long repostOfId) {
        long id = ids.next();
        jdbc.sql("""
                INSERT INTO posts (id, author_id, content, reply_to_id, repost_of_id)
                VALUES (:id, :author, :content, :replyTo, :repostOf)
                """)
                .param("id", id)
                .param("author", authorId)
                .param("content", content, Types.VARCHAR)
                .param("replyTo", replyToId, Types.BIGINT)
                .param("repostOf", repostOfId, Types.BIGINT)
                .update();
        return id;
    }

    Optional<PostCore> findCore(long id) {
        return jdbc.sql("SELECT id, author_id, reply_to_id, repost_of_id FROM posts WHERE id = :id")
                .param("id", id)
                .query(PostCore.class)
                .optional();
    }

    Optional<Long> findRepostId(long authorId, long originalId) {
        return jdbc.sql("SELECT id FROM posts WHERE author_id = :author AND repost_of_id = :original")
                .param("author", authorId)
                .param("original", originalId)
                .query(Long.class)
                .optional();
    }

    void delete(long id) {
        jdbc.sql("DELETE FROM posts WHERE id = :id").param("id", id).update();
    }

    void adjust(long postId, Counter counter, int delta) {
        jdbc.sql("UPDATE posts SET " + counter.column + " = GREATEST(" + counter.column + " + :delta, 0)"
                        + " WHERE id = :id")
                .param("delta", delta)
                .param("id", postId)
                .update();
    }

    /** @return true when a like was recorded, false if it already existed */
    boolean like(long userId, long postId) {
        return jdbc.sql("INSERT INTO likes (user_id, post_id) VALUES (:user, :post) ON CONFLICT DO NOTHING")
                .param("user", userId)
                .param("post", postId)
                .update() == 1;
    }

    boolean unlike(long userId, long postId) {
        return jdbc.sql("DELETE FROM likes WHERE user_id = :user AND post_id = :post")
                .param("user", userId)
                .param("post", postId)
                .update() == 1;
    }

    List<PostRow> findRows(Collection<Long> ids, long viewer) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.sql(SELECT_VIEW + " AND p.id IN (:ids)")
                .param("viewer", viewer)
                .param("ids", ids)
                .query(PostRow.class)
                .list();
    }

    /** Which of an author's posts a profile tab shows. */
    enum AuthorFilter {
        /** Original posts and reposts, no replies. */
        POSTS("AND p.reply_to_id IS NULL"),
        /** Everything the author wrote or reposted. */
        REPLIES(""),
        /** The author's own posts and replies that carry photos. */
        MEDIA("AND p.repost_of_id IS NULL AND EXISTS (SELECT 1 FROM media m WHERE m.post_id = p.id)");

        final String sql;

        AuthorFilter(String sql) {
            this.sql = sql;
        }
    }

    /** An author's posts, newest first, narrowed by profile tab. */
    List<PostRow> byAuthor(long authorId, long viewer, AuthorFilter filter, Long cursor, int fetchSize) {
        return jdbc.sql(SELECT_VIEW + """
                  AND p.author_id = :author AND (:cursor IS NULL OR p.id < :cursor)
                """ + filter.sql + """

                ORDER BY p.id DESC
                LIMIT :limit
                """)
                .param("viewer", viewer)
                .param("author", authorId)
                .param("cursor", cursor, Types.BIGINT)
                .param("limit", fetchSize)
                .query(PostRow.class)
                .list();
    }

    /**
     * The chain of posts a reply answers, root first, excluding the post itself. Depth is capped
     * so a pathological chain cannot make one request walk the whole table.
     */
    List<Long> ancestorIds(long postId, int maxDepth) {
        return jdbc.sql("""
                WITH RECURSIVE chain (id, reply_to_id, depth) AS (
                    SELECT id, reply_to_id, 0 FROM posts WHERE id = :id
                    UNION ALL
                    SELECT p.id, p.reply_to_id, c.depth + 1
                    FROM posts p JOIN chain c ON p.id = c.reply_to_id
                    WHERE c.depth < :max
                )
                SELECT id FROM chain WHERE depth > 0 ORDER BY depth DESC
                """)
                .param("id", postId)
                .param("max", maxDepth)
                .query(Long.class)
                .list();
    }

    /** Direct replies to a post, oldest first so a thread reads top to bottom. */
    List<PostRow> replies(long postId, long viewer, Long cursor, int fetchSize) {
        return jdbc.sql(SELECT_VIEW + """
                  AND p.reply_to_id = :post AND (:cursor IS NULL OR p.id > :cursor)
                ORDER BY p.id ASC
                LIMIT :limit
                """)
                .param("viewer", viewer)
                .param("post", postId)
                .param("cursor", cursor, Types.BIGINT)
                .param("limit", fetchSize)
                .query(PostRow.class)
                .list();
    }
}
