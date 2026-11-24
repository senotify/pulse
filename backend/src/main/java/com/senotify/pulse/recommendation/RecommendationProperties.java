package com.senotify.pulse.recommendation;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Tuning for the For You pipeline. Defaults are chosen for a small network and live in one place. */
@ConfigurationProperties("pulse.recommendation")
public record RecommendationProperties(
        Duration candidateWindow,
        Duration trendingWindow,
        Duration seenWindow,
        Duration affinityWindow,
        int sourceLimit,
        int snapshotSize,
        Duration snapshotTtl,
        double recencyHalfLifeHours,
        double authorPenalty,
        double maxOutOfNetworkShare,
        Weights weights) {

    /** Weights of the transparent linear ranker; every feature is normalized to [0, 1]. */
    public record Weights(double inNetwork, double authorAffinity, double socialProof, double engagementVelocity,
                          double recency, double authorReputation, double contentAffinity) {

        public static Weights defaults() {
            return new Weights(1.0, 2.0, 1.2, 1.5, 1.0, 0.3, 0.8);
        }
    }

    public RecommendationProperties {
        candidateWindow = orDefault(candidateWindow, Duration.ofDays(3));
        trendingWindow = orDefault(trendingWindow, Duration.ofDays(1));
        seenWindow = orDefault(seenWindow, Duration.ofDays(7));
        affinityWindow = orDefault(affinityWindow, Duration.ofDays(30));
        snapshotTtl = orDefault(snapshotTtl, Duration.ofMinutes(30));
        sourceLimit = sourceLimit > 0 ? sourceLimit : 200;
        snapshotSize = snapshotSize > 0 ? snapshotSize : 200;
        recencyHalfLifeHours = recencyHalfLifeHours > 0 ? recencyHalfLifeHours : 6;
        authorPenalty = authorPenalty > 0 && authorPenalty <= 1 ? authorPenalty : 0.5;
        maxOutOfNetworkShare = maxOutOfNetworkShare > 0 && maxOutOfNetworkShare <= 1 ? maxOutOfNetworkShare : 0.5;
        weights = weights == null ? Weights.defaults() : weights;
    }

    public static RecommendationProperties defaults() {
        return new RecommendationProperties(null, null, null, null, 0, 0, null, 0, 0, 0, null);
    }

    private static Duration orDefault(Duration value, Duration fallback) {
        return value == null ? fallback : value;
    }
}
