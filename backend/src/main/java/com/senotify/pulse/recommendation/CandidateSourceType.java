package com.senotify.pulse.recommendation;

/** Where a candidate came from, in priority order for picking the reason shown to the viewer. */
public enum CandidateSourceType {
    /** Recent posts by accounts the viewer follows. */
    IN_NETWORK,
    /** Posts liked or reposted by accounts the viewer follows. */
    ENGAGED_BY_FOLLOWING,
    /** Posts by accounts that the viewer's follows follow (second-degree graph). */
    FOLLOWED_BY_FOLLOWING,
    /** Posts with the most engagement in the trending window; the cold-start source. */
    TRENDING;

    public boolean inNetwork() {
        return this == IN_NETWORK;
    }
}
