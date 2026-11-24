package com.senotify.pulse.timeline;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("pulse.timeline")
public record TimelineProperties(int capacity, int fanoutThreshold, int followBackfill, Duration idleTtl) {

    public TimelineProperties {
        if (capacity <= 0) {
            capacity = 800;
        }
        if (fanoutThreshold <= 0) {
            fanoutThreshold = 5000;
        }
        if (followBackfill <= 0) {
            followBackfill = 50;
        }
        if (idleTtl == null) {
            idleTtl = Duration.ofDays(7);
        }
    }
}
