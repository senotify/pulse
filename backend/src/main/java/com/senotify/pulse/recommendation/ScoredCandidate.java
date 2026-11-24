package com.senotify.pulse.recommendation;

public record ScoredCandidate(Candidate candidate, Features features, double score) {

    public long authorId() {
        return candidate.authorId();
    }

    public boolean inNetwork() {
        return candidate.inNetwork();
    }
}
