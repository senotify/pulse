package com.senotify.pulse.identity;

import com.senotify.pulse.identity.IdentityDtos.Me;
import com.senotify.pulse.identity.IdentityDtos.UserProfile;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    record Credentials(long id, String username, String passwordHash) {
    }

    private final JdbcClient jdbc;

    UserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Lookup used by other modules to turn a handle from a URL into an id. */
    public Optional<Long> findIdByUsername(String username) {
        return jdbc.sql("SELECT id FROM users WHERE username = :u")
                .param("u", normalize(username))
                .query(Long.class)
                .optional();
    }

    boolean existsByUsername(String username) {
        return jdbc.sql("SELECT COUNT(*) FROM users WHERE username = :u")
                .param("u", username).query(Long.class).single() > 0;
    }

    boolean existsByEmail(String email) {
        return jdbc.sql("SELECT COUNT(*) FROM users WHERE email = :e")
                .param("e", email).query(Long.class).single() > 0;
    }

    long insert(String username, String email, String displayName, String passwordHash) {
        var keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO users (username, email, display_name, password_hash)
                VALUES (:u, :e, :d, :p)
                """)
                .param("u", username)
                .param("e", email)
                .param("d", displayName)
                .param("p", passwordHash)
                .update(keys, "id");
        return keys.getKeyAs(Long.class);
    }

    Optional<Credentials> findCredentials(String login) {
        return jdbc.sql("""
                SELECT id, username, password_hash FROM users
                WHERE username = :login OR email = :login
                """)
                .param("login", normalize(login))
                .query(Credentials.class)
                .optional();
    }

    Optional<Me> findMe(long id) {
        return jdbc.sql("""
                SELECT id, username, email, display_name, bio, created_at FROM users WHERE id = :id
                """)
                .param("id", id)
                .query(Me.class)
                .optional();
    }

    void updateProfile(long id, String displayName, String bio) {
        jdbc.sql("""
                UPDATE users
                SET display_name = COALESCE(:d, display_name), bio = COALESCE(:b, bio)
                WHERE id = :id
                """)
                .param("d", displayName)
                .param("b", bio)
                .param("id", id)
                .update();
    }

    Optional<UserProfile> findProfile(String username, long viewer) {
        return jdbc.sql("""
                SELECT u.id, u.username, u.display_name, u.bio, u.created_at,
                       (SELECT COUNT(*) FROM follows f WHERE f.followee_id = u.id) AS followers,
                       (SELECT COUNT(*) FROM follows f WHERE f.follower_id = u.id) AS following,
                       (SELECT COUNT(*) FROM posts p
                         WHERE p.author_id = u.id AND p.repost_of_id IS NULL) AS posts,
                       EXISTS (SELECT 1 FROM follows f
                                WHERE f.follower_id = :viewer AND f.followee_id = u.id) AS followed_by_me,
                       EXISTS (SELECT 1 FROM follows f
                                WHERE f.follower_id = u.id AND f.followee_id = :viewer) AS follows_me,
                       EXISTS (SELECT 1 FROM blocks b
                                WHERE b.blocker_id = :viewer AND b.blocked_id = u.id) AS blocked_by_me,
                       EXISTS (SELECT 1 FROM mutes m
                                WHERE m.muter_id = :viewer AND m.muted_id = u.id) AS muted_by_me
                FROM users u
                WHERE u.username = :u
                """)
                .param("u", normalize(username))
                .param("viewer", viewer)
                .query(UserProfile.class)
                .optional();
    }

    static String normalize(String value) {
        return value == null ? null : value.strip().toLowerCase(java.util.Locale.ROOT);
    }
}
