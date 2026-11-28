package com.senotify.pulse.ratelimit;

import com.senotify.pulse.common.RateLimitedException;
import com.senotify.pulse.ratelimit.RateLimitProperties.Rule;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The named limits applied by controllers. Each check throws {@link RateLimitedException} (429 with
 * {@code Retry-After}) when exceeded, and lets the request through when the limiter itself fails,
 * so a Redis outage never takes login or posting down with it.
 */
@Service
public class RateLimits {

    private static final Logger log = LoggerFactory.getLogger(RateLimits.class);

    private final RateLimiter limiter;
    private final RateLimitProperties properties;

    RateLimits(RateLimiter limiter, RateLimitProperties properties) {
        this.limiter = limiter;
        this.properties = properties;
    }

    /** Per client address and account, so one attacker cannot lock a victim out from elsewhere. */
    public void login(HttpServletRequest request, String login) {
        check("login", properties.login(), clientIp(request) + ":" + login.strip().toLowerCase(Locale.ROOT));
    }

    public void registration(HttpServletRequest request) {
        check("register", properties.registration(), clientIp(request));
    }

    public void createPost(long userId) {
        check("post", properties.posts(), Long.toString(userId));
    }

    public void upload(long userId) {
        check("upload", properties.uploads(), Long.toString(userId));
    }

    private void check(String name, Rule rule, String subject) {
        if (!properties.enabled()) {
            return;
        }
        Duration retryAfter;
        try {
            retryAfter = limiter.hit(name + ":" + subject, rule.limit(), rule.window());
        } catch (RuntimeException e) {
            log.warn("Rate limiter unavailable, allowing {} request: {}", name, e.toString());
            return;
        }
        if (retryAfter != null) {
            throw new RateLimitedException("Too many requests; try again later", retryAfter);
        }
    }

    /**
     * The client address. nginx and Caddy in front of the API put the real client first in
     * {@code X-Forwarded-For}; the API is not reachable except through them in production.
     */
    static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].strip();
        }
        return request.getRemoteAddr();
    }
}
