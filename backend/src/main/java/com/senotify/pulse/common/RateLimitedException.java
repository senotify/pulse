package com.senotify.pulse.common;

import java.time.Duration;
import org.springframework.http.HttpStatus;

/** A 429 that tells the client how long to wait. */
public class RateLimitedException extends ApiException {

    private final Duration retryAfter;

    public RateLimitedException(String message, Duration retryAfter) {
        super(HttpStatus.TOO_MANY_REQUESTS, message);
        this.retryAfter = retryAfter;
    }

    public long retryAfterSeconds() {
        return Math.max(1, (retryAfter.toMillis() + 999) / 1000);
    }
}
