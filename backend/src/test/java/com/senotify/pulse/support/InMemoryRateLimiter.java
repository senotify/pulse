package com.senotify.pulse.support;

import com.senotify.pulse.ratelimit.RateLimiter;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Same contract as the Redis limiter; {@link #failing} simulates a Redis outage. */
public class InMemoryRateLimiter implements RateLimiter {

    private record Window(long startedAtMs, int count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    public volatile boolean failing;

    @Override
    public Duration hit(String key, int limit, Duration window) {
        if (failing) {
            throw new org.springframework.data.redis.RedisConnectionFailureException("redis down");
        }
        long now = System.currentTimeMillis();
        long[] remaining = new long[1];
        boolean[] over = new boolean[1];
        windows.compute(key, (k, w) -> {
            Window current = w == null || now - w.startedAtMs() >= window.toMillis() ? new Window(now, 0) : w;
            Window next = new Window(current.startedAtMs(), current.count() + 1);
            over[0] = next.count() > limit;
            remaining[0] = window.toMillis() - (now - next.startedAtMs());
            return next;
        });
        return over[0] ? Duration.ofMillis(remaining[0]) : null;
    }
}
