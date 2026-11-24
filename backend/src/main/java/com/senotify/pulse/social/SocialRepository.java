package com.senotify.pulse.social;

import com.senotify.pulse.identity.UserSummary;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SocialRepository {

    /** Edge tables share a shape; the enum keeps table and column names out of user input. */
    enum Edge {
        FOLLOW("follows", "follower_id", "followee_id"),
        BLOCK("blocks", "blocker_id", "blocked_id"),
        MUTE("mutes", "muter_id", "muted_id");

        final String table;
        final String from;
        final String to;

        Edge(String table, String from, String to) {
            this.table = table;
            this.from = from;
            this.to = to;
        }
    }

    private final JdbcClient jdbc;

    SocialRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** True when either user has blocked the other; used by other modules before interactions. */
    public boolean isBlockedEitherWay(long a, long b) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM blocks
                WHERE (blocker_id = :a AND blocked_id = :b) OR (blocker_id = :b AND blocked_id = :a)
                """)
                .param("a", a)
                .param("b", b)
                .query(Long.class)
                .single() > 0;
    }

    /** @return true when the edge was created, false when it already existed */
    boolean add(Edge edge, long from, long to) {
        return jdbc.sql("INSERT INTO " + edge.table + " (" + edge.from + ", " + edge.to + ") "
                        + "VALUES (:from, :to) ON CONFLICT DO NOTHING")
                .param("from", from)
                .param("to", to)
                .update() == 1;
    }

    /** @return true when an edge was removed */
    boolean remove(Edge edge, long from, long to) {
        return jdbc.sql("DELETE FROM " + edge.table + " WHERE " + edge.from + " = :from AND " + edge.to + " = :to")
                .param("from", from)
                .param("to", to)
                .update() == 1;
    }

    List<UserSummary> followers(long userId, Long cursor, int fetchSize) {
        return jdbc.sql("""
                SELECT u.id, u.username, u.display_name
                FROM follows f JOIN users u ON u.id = f.follower_id
                WHERE f.followee_id = :user AND (:cursor IS NULL OR u.id < :cursor)
                ORDER BY u.id DESC
                LIMIT :limit
                """)
                .param("user", userId)
                .param("cursor", cursor, java.sql.Types.BIGINT)
                .param("limit", fetchSize)
                .query(UserSummary.class)
                .list();
    }

    List<UserSummary> following(long userId, Long cursor, int fetchSize) {
        return jdbc.sql("""
                SELECT u.id, u.username, u.display_name
                FROM follows f JOIN users u ON u.id = f.followee_id
                WHERE f.follower_id = :user AND (:cursor IS NULL OR u.id < :cursor)
                ORDER BY u.id DESC
                LIMIT :limit
                """)
                .param("user", userId)
                .param("cursor", cursor, java.sql.Types.BIGINT)
                .param("limit", fetchSize)
                .query(UserSummary.class)
                .list();
    }
}
