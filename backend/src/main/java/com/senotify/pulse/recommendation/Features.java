package com.senotify.pulse.recommendation;

/**
 * The feature vector for one (viewer, candidate) pair. Every value is normalized to [0, 1] so the
 * linear ranker's weights are directly comparable, and a learned model gets well-scaled inputs.
 */
public record Features(
        /* 1 when the viewer follows the author. */
        double inNetwork,
        /* Viewer's recent replies, reposts, likes and clicks on this author, squashed: a / (a + 3). */
        double authorAffinity,
        /* Followed accounts that liked or reposted the post: 1 - 1 / (1 + n). */
        double socialProof,
        /* Weighted engagement per hour with gravity, squashed: v / (v + 1). */
        double engagementVelocity,
        /* Exponential decay with the configured half-life. */
        double recency,
        /* log(1 + followers) / log(1 + 10000), capped at 1. */
        double authorReputation,
        /* Overlap between the post's terms and the viewer's liked-post terms. */
        double contentAffinity) {
}
