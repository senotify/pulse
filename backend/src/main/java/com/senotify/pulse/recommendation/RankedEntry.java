package com.senotify.pulse.recommendation;

/** One slot of a ranked For You feed, as stored in a snapshot and explained to the client. */
public record RankedEntry(long postId, CandidateSourceType source, String reason, double score, Features features) {
}
