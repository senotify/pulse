package com.senotify.pulse.ratelimit;

import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/** One Redis counter per key, expiring with its window ({@code rl:{key}}). */
@Component
class RedisRateLimiter implements RateLimiter {

    /** INCR and arm the expiry atomically; returns {count, remaining ms}. */
    private static final RedisScript<List> HIT = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 or redis.call('PTTL', KEYS[1]) < 0 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return {count, redis.call('PTTL', KEYS[1])}
            """, List.class);

    private final StringRedisTemplate redis;

    RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Duration hit(String key, int limit, Duration window) {
        List<?> result = redis.execute(HIT, List.of("rl:" + key), Long.toString(window.toMillis()));
        if (result == null || result.size() < 2) {
            return null;
        }
        long count = ((Number) result.get(0)).longValue();
        long remainingMs = ((Number) result.get(1)).longValue();
        return count > limit ? Duration.ofMillis(Math.max(remainingMs, 1)) : null;
    }
}
