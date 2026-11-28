package com.senotify.pulse.ratelimit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Fixed-window limits; see {@code pulse.ratelimit} in application.yml. */
@ConfigurationProperties("pulse.ratelimit")
public record RateLimitProperties(boolean enabled, Rule login, Rule registration, Rule posts, Rule uploads) {

    /** At most {@code limit} hits per {@code window}. */
    public record Rule(int limit, Duration window) {
    }

    public RateLimitProperties {
        login = login == null || login.limit() <= 0 ? new Rule(10, Duration.ofMinutes(15)) : login;
        registration = registration == null || registration.limit() <= 0
                ? new Rule(5, Duration.ofHours(1)) : registration;
        posts = posts == null || posts.limit() <= 0 ? new Rule(30, Duration.ofMinutes(10)) : posts;
        uploads = uploads == null || uploads.limit() <= 0 ? new Rule(40, Duration.ofMinutes(10)) : uploads;
    }
}
