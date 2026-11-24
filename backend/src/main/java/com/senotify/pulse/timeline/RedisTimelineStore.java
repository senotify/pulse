package com.senotify.pulse.timeline;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Timelines as Redis sorted sets ({@code tl:home:{userId}}), scored by post id.
 *
 * <p>Every set holds a sentinel member at score 0 so that an empty timeline still exists as a key;
 * real post ids start at 1, so reads query scores from 1 up. The idle TTL is refreshed on reads only,
 * so fan-out to people who stopped reading never keeps their timelines alive.
 */
@Component
class RedisTimelineStore implements TimelineStore {

    private static final String SENTINEL = "0:0";

    /** ZADD and trim only when the key exists, atomically, so fan-out never creates a partial timeline. */
    private static final RedisScript<Long> ADD_IF_PRESENT = RedisScript.of("""
            if redis.call('EXISTS', KEYS[1]) == 0 then
              return 0
            end
            for i = 2, #ARGV, 2 do
              redis.call('ZADD', KEYS[1], ARGV[i], ARGV[i + 1])
            end
            -- rank 0 is the sentinel; keep it plus the newest ARGV[1] entries
            redis.call('ZREMRANGEBYRANK', KEYS[1], 1, -(tonumber(ARGV[1]) + 1))
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final TimelineProperties properties;

    RedisTimelineStore(StringRedisTemplate redis, TimelineProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    public boolean exists(long userId) {
        return Boolean.TRUE.equals(redis.hasKey(key(userId)));
    }

    @Override
    public void addIfPresent(long userId, Collection<TimelineEntry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        List<String> args = new ArrayList<>(entries.size() * 2 + 1);
        args.add(Integer.toString(properties.capacity()));
        for (TimelineEntry entry : entries) {
            args.add(Long.toString(entry.postId()));
            args.add(entry.member());
        }
        redis.execute(ADD_IF_PRESENT, List.of(key(userId)), args.toArray());
    }

    @Override
    public void replace(long userId, List<TimelineEntry> entries) {
        String key = key(userId);
        Set<TypedTuple<String>> tuples = new HashSet<>();
        tuples.add(TypedTuple.of(SENTINEL, 0.0));
        entries.stream()
                .limit(properties.capacity())
                .forEach(e -> tuples.add(TypedTuple.of(e.member(), (double) e.postId())));
        redis.delete(key);
        redis.opsForZSet().add(key, tuples);
        redis.expire(key, properties.idleTtl());
    }

    @Override
    public List<TimelineEntry> page(long userId, Long beforeId, int count) {
        String key = key(userId);
        double max = beforeId == null ? Double.POSITIVE_INFINITY : beforeId - 1;
        Set<String> members = redis.opsForZSet().reverseRangeByScore(key, 1, max, 0, count);
        redis.expire(key, properties.idleTtl());
        if (members == null) {
            return List.of();
        }
        return members.stream().map(TimelineEntry::parse).toList();
    }

    @Override
    public void removeAuthor(long userId, long authorId) {
        String key = key(userId);
        Set<String> members = redis.opsForZSet().range(key, 1, -1);
        if (members == null) {
            return;
        }
        String suffix = ":" + authorId;
        Object[] doomed = members.stream().filter(m -> m.endsWith(suffix)).toArray();
        if (doomed.length > 0) {
            redis.opsForZSet().remove(key, doomed);
        }
    }

    @Override
    public void evict(long userId) {
        redis.delete(key(userId));
    }

    private static String key(long userId) {
        return "tl:home:" + userId;
    }
}
