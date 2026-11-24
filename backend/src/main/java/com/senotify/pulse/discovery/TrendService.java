package com.senotify.pulse.discovery;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Trending hashtags: tags used in the most distinct posts over the last 24 hours. The result is
 * cached briefly because every page load asks for it.
 */
@Service
class TrendService {

    record Trend(String tag, long posts) {
    }

    private record Cached(Instant at, List<Trend> trends) {
    }

    static final Pattern HASHTAG = Pattern.compile("(?<![\\p{L}\\p{N}_])#([\\p{L}\\p{N}_]{2,50})");
    private static final int SCAN_LIMIT = 5000;
    private static final int TOP = 5;

    private final DiscoveryRepository repository;
    private final Duration cacheTtl;
    private final Clock clock = Clock.systemUTC();
    private volatile Cached cached;

    TrendService(DiscoveryRepository repository,
                 @Value("${pulse.discovery.trends-cache-ttl:PT1M}") Duration cacheTtl) {
        this.repository = repository;
        this.cacheTtl = cacheTtl;
    }

    List<Trend> trending() {
        Cached snapshot = cached;
        Instant now = clock.instant();
        if (snapshot != null && snapshot.at().plus(cacheTtl).isAfter(now)) {
            return snapshot.trends();
        }
        List<Trend> trends = rank(repository.recentContents(OffsetDateTime.now(clock).minusDays(1), SCAN_LIMIT));
        cached = new Cached(now, trends);
        return trends;
    }

    static List<Trend> rank(List<String> contents) {
        Map<String, Long> counts = new HashMap<>();
        for (String content : contents) {
            Set<String> tags = new HashSet<>();
            Matcher m = HASHTAG.matcher(content);
            while (m.find()) {
                tags.add("#" + m.group(1).toLowerCase(Locale.ROOT));
            }
            tags.forEach(tag -> counts.merge(tag, 1L, Long::sum));
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey(Comparator.naturalOrder())))
                .limit(TOP)
                .map(e -> new Trend(e.getKey(), e.getValue()))
                .toList();
    }
}
