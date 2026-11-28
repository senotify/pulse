package com.senotify.pulse.ratelimit;

import java.time.Duration;

/** Fixed-window counter keyed by an arbitrary string. */
public interface RateLimiter {

    /**
     * Records one hit.
     *
     * @return the time left in the window when this hit is over {@code limit}, or {@code null} when allowed
     */
    Duration hit(String key, int limit, Duration window);
}
