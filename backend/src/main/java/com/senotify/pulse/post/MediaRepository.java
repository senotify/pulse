package com.senotify.pulse.post;

import com.senotify.pulse.common.Images.Jpeg;
import com.senotify.pulse.common.SnowflakeIds;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class MediaRepository {

    private record MediaRow(long postId, long id, int width, int height) {
    }

    private final JdbcClient jdbc;
    private final SnowflakeIds ids;

    MediaRepository(JdbcClient jdbc, SnowflakeIds ids) {
        this.jdbc = jdbc;
        this.ids = ids;
    }

    MediaView insert(long ownerId, Jpeg jpeg) {
        long id = ids.next();
        jdbc.sql("""
                INSERT INTO media (id, owner_id, image, width, height)
                VALUES (:id, :owner, :image, :width, :height)
                """)
                .param("id", id)
                .param("owner", ownerId)
                .param("image", jpeg.bytes())
                .param("width", jpeg.width())
                .param("height", jpeg.height())
                .update();
        return new MediaView(id, jpeg.width(), jpeg.height());
    }

    long unclaimedCount(long ownerId) {
        return jdbc.sql("SELECT COUNT(*) FROM media WHERE owner_id = :owner AND post_id IS NULL")
                .param("owner", ownerId)
                .query(Long.class)
                .single();
    }

    /**
     * Attaches the owner's unclaimed uploads to a post in the given order.
     *
     * @return how many were attached; fewer than requested means some ids were not claimable
     */
    int claim(long ownerId, long postId, List<Long> mediaIds) {
        int claimed = 0;
        for (int i = 0; i < mediaIds.size(); i++) {
            claimed += jdbc.sql("""
                    UPDATE media SET post_id = :post, position = :position
                    WHERE id = :id AND owner_id = :owner AND post_id IS NULL
                    """)
                    .param("post", postId)
                    .param("position", i)
                    .param("id", mediaIds.get(i))
                    .param("owner", ownerId)
                    .update();
        }
        return claimed;
    }

    Optional<byte[]> image(long id) {
        return jdbc.sql("SELECT image FROM media WHERE id = :id").param("id", id).query(byte[].class).optional();
    }

    /** Media for many posts in one query, keyed by post id, in display order. */
    Map<Long, List<MediaView>> forPosts(Collection<Long> postIds) {
        if (postIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<MediaView>> byPost = new LinkedHashMap<>();
        jdbc.sql("""
                SELECT post_id, id, width, height FROM media
                WHERE post_id IN (:ids)
                ORDER BY post_id, position
                """)
                .param("ids", postIds)
                .query(MediaRow.class)
                .list()
                .forEach(row -> byPost.computeIfAbsent(row.postId(), k -> new java.util.ArrayList<>())
                        .add(new MediaView(row.id(), row.width(), row.height())));
        return byPost;
    }

    int deleteUnclaimedBefore(OffsetDateTime cutoff) {
        return jdbc.sql("DELETE FROM media WHERE post_id IS NULL AND created_at < :cutoff")
                .param("cutoff", cutoff)
                .update();
    }
}
