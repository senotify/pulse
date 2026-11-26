package com.senotify.pulse.notification;

import com.senotify.pulse.common.SnowflakeIds;
import com.senotify.pulse.identity.UserSummary;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class NotificationRepository {

    /** Rows of the viewer's notifications whose actor is blocked in either direction are hidden. */
    private static final String VISIBLE = """
            FROM notifications n
            JOIN users a ON a.id = n.actor_id
            LEFT JOIN posts p ON p.id = n.post_id
            WHERE n.user_id = :user
              AND NOT EXISTS (SELECT 1 FROM blocks b
                               WHERE (b.blocker_id = :user AND b.blocked_id = n.actor_id)
                                  OR (b.blocker_id = n.actor_id AND b.blocked_id = :user))
            """;

    private record Row(long id, String type, long actorId, String username, String displayName, String avatarKey,
                       int actorCount, Long postId, String content, boolean read, OffsetDateTime createdAt) {

        NotificationView toView() {
            return new NotificationView(id, type, new UserSummary(actorId, username, displayName, avatarKey),
                    actorCount, postId, content == null ? null : preview(content), read, createdAt);
        }

        private static String preview(String text) {
            return text.length() <= 140 ? text : text.substring(0, 139) + "…";
        }
    }

    private final JdbcClient jdbc;
    private final SnowflakeIds ids;

    NotificationRepository(JdbcClient jdbc, SnowflakeIds ids) {
        this.jdbc = jdbc;
        this.ids = ids;
    }

    List<NotificationView> page(long user, Long cursor, int fetchSize) {
        return jdbc.sql("""
                SELECT n.id, n.type, n.actor_id, a.username, a.display_name, a.avatar_key, n.actor_count,
                       n.post_id, p.content, (n.read_at IS NOT NULL) AS read, n.created_at
                """ + VISIBLE + """
                  AND (:cursor IS NULL OR n.id < :cursor)
                ORDER BY n.id DESC
                LIMIT :limit
                """)
                .param("user", user)
                .param("cursor", cursor, java.sql.Types.BIGINT)
                .param("limit", fetchSize)
                .query(Row.class)
                .list().stream().map(Row::toView).toList();
    }

    long unreadCount(long user) {
        return jdbc.sql("SELECT COUNT(*) " + VISIBLE + " AND n.read_at IS NULL")
                .param("user", user)
                .query(Long.class)
                .single();
    }

    /** Marks the given notifications (or all of them when {@code only} is empty) as read. */
    void markRead(long user, Collection<Long> only) {
        if (only.isEmpty()) {
            jdbc.sql("UPDATE notifications SET read_at = CURRENT_TIMESTAMP WHERE user_id = :user AND read_at IS NULL")
                    .param("user", user)
                    .update();
        } else {
            jdbc.sql("""
                    UPDATE notifications SET read_at = CURRENT_TIMESTAMP
                    WHERE user_id = :user AND read_at IS NULL AND id IN (:ids)
                    """)
                    .param("user", user)
                    .param("ids", only)
                    .update();
        }
    }

    // ---- writes used by the outbox handler ----

    boolean exists(long eventId, long user) {
        return jdbc.sql("SELECT COUNT(*) FROM notifications WHERE outbox_event_id = :e AND user_id = :u")
                .param("e", eventId).param("u", user).query(Long.class).single() > 0;
    }

    boolean existsFollow(long user, long actor) {
        return jdbc.sql("SELECT COUNT(*) FROM notifications WHERE user_id = :u AND type = 'FOLLOW' AND actor_id = :a")
                .param("u", user).param("a", actor).query(Long.class).single() > 0;
    }

    void insert(long eventId, long user, String type, long actor, Long postId) {
        jdbc.sql("""
                INSERT INTO notifications (id, user_id, type, actor_id, post_id, outbox_event_id)
                VALUES (:id, :user, :type, :actor, :post, :event)
                """)
                .param("id", ids.next())
                .param("user", user)
                .param("type", type)
                .param("actor", actor)
                .param("post", postId, java.sql.Types.BIGINT)
                .param("event", eventId)
                .update();
    }

    /**
     * Creates or refreshes the single LIKE notification for a post: the id is re-issued so it sorts
     * first, and it becomes unread again.
     */
    void upsertLike(long eventId, long owner, long postId, long actor, int count) {
        Optional<Long> existing = jdbc.sql("SELECT id FROM notifications WHERE user_id = :u AND type = 'LIKE' AND post_id = :p")
                .param("u", owner).param("p", postId).query(Long.class).optional();
        if (existing.isEmpty()) {
            insert(eventId, owner, "LIKE", actor, postId);
            jdbc.sql("UPDATE notifications SET actor_count = :c WHERE outbox_event_id = :e AND user_id = :u")
                    .param("c", count).param("e", eventId).param("u", owner).update();
            return;
        }
        jdbc.sql("""
                UPDATE notifications
                SET id = :newId, actor_id = :actor, actor_count = :count, outbox_event_id = :event,
                    read_at = NULL, created_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """)
                .param("newId", ids.next())
                .param("actor", actor)
                .param("count", count)
                .param("event", eventId)
                .param("id", existing.get())
                .update();
    }

    Optional<Long> postAuthor(long postId) {
        return jdbc.sql("SELECT author_id FROM posts WHERE id = :id").param("id", postId).query(Long.class).optional();
    }

    Optional<String> postContent(long postId) {
        return jdbc.sql("SELECT content FROM posts WHERE id = :id").param("id", postId).query(String.class).optional();
    }

    /** Likes of a post by anyone other than its author. */
    int likeCount(long postId, long author) {
        return jdbc.sql("SELECT COUNT(*) FROM likes WHERE post_id = :p AND user_id <> :a")
                .param("p", postId).param("a", author).query(Long.class).single().intValue();
    }

    List<Long> userIdsByUsernames(Collection<String> usernames) {
        if (usernames.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("SELECT id FROM users WHERE username IN (:names)")
                .param("names", usernames).query(Long.class).list();
    }

    boolean blockedEitherWay(long a, long b) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM blocks
                WHERE (blocker_id = :a AND blocked_id = :b) OR (blocker_id = :b AND blocked_id = :a)
                """)
                .param("a", a).param("b", b).query(Long.class).single() > 0;
    }
}
