package com.senotify.pulse.recommendation;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * A post being considered for the feed. One post can arrive from several sources; they are merged
 * so every source counts as evidence and the post is scored once.
 */
public record Candidate(CandidatePost post, Set<CandidateSourceType> sources) {

    public record CandidatePost(long postId, long authorId, String content, OffsetDateTime createdAt,
                                int likeCount, int replyCount, int repostCount) {
    }

    public static Candidate of(CandidatePost post, CandidateSourceType source) {
        return new Candidate(post, EnumSet.of(source));
    }

    public Candidate merge(Candidate other) {
        EnumSet<CandidateSourceType> union = EnumSet.copyOf(sources);
        union.addAll(other.sources);
        return new Candidate(post, union);
    }

    public long postId() {
        return post.postId();
    }

    public long authorId() {
        return post.authorId();
    }

    public boolean inNetwork() {
        return sources.contains(CandidateSourceType.IN_NETWORK);
    }

    /** The highest-priority source, which decides the reason shown with the post. */
    public CandidateSourceType primarySource() {
        return sources.iterator().next(); // EnumSet iterates in declaration (priority) order
    }
}
