package com.senotify.pulse.recommendation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Snapshots as JSON strings under {@code fy:{viewer}:{token}}, expiring after the snapshot TTL. */
@Component
class RedisFeedSnapshotStore implements FeedSnapshotStore {

    private static final TypeReference<List<RankedEntry>> TYPE = new TypeReference<>() {
    };

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final RecommendationProperties properties;

    RedisFeedSnapshotStore(StringRedisTemplate redis, ObjectMapper mapper, RecommendationProperties properties) {
        this.redis = redis;
        this.mapper = mapper;
        this.properties = properties;
    }

    @Override
    public void save(long viewer, String token, List<RankedEntry> entries) {
        try {
            redis.opsForValue().set(key(viewer, token), mapper.writeValueAsString(entries), properties.snapshotTtl());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize feed snapshot", e);
        }
    }

    @Override
    public Optional<List<RankedEntry>> load(long viewer, String token) {
        String json = redis.opsForValue().get(key(viewer, token));
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(json, TYPE));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    private static String key(long viewer, String token) {
        return "fy:" + viewer + ":" + token;
    }
}
