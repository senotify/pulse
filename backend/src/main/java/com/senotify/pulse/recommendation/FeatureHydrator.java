package com.senotify.pulse.recommendation;

import com.senotify.pulse.recommendation.Candidate.CandidatePost;
import com.senotify.pulse.recommendation.RecommendationRepository.SocialProof;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Computes features for all candidates with a fixed number of batched queries, independent of candidate count. */
@Component
class FeatureHydrator {

    record Hydrated(Map<Long, Features> features, Map<Long, SocialProof> socialProof) {
    }

    private static final double REPUTATION_SCALE = Math.log1p(10_000);
    private static final int PROFILE_SIZE = 100;

    private final RecommendationRepository repository;
    private final RecommendationProperties properties;

    FeatureHydrator(RecommendationRepository repository, RecommendationProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    Hydrated hydrate(long viewer, List<Candidate> candidates, OffsetDateTime now) {
        Set<Long> authors = candidates.stream().map(Candidate::authorId).collect(Collectors.toSet());
        List<Long> postIds = candidates.stream().map(Candidate::postId).toList();

        Map<Long, Double> followers = repository.followerCounts(authors);
        Map<Long, Double> affinity = repository.authorAffinity(viewer, authors, now.minus(properties.affinityWindow()));
        Map<Long, SocialProof> proof = repository.socialProof(viewer, postIds);
        ContentProfile profile = ContentProfile.of(repository.likedContents(viewer, PROFILE_SIZE));

        Map<Long, Features> features = new HashMap<>();
        for (Candidate candidate : candidates) {
            CandidatePost post = candidate.post();
            SocialProof p = proof.get(post.postId());
            features.put(post.postId(), compute(candidate, now,
                    followers.getOrDefault(post.authorId(), 0.0),
                    affinity.getOrDefault(post.authorId(), 0.0),
                    p == null ? 0 : p.proofCount(),
                    profile));
        }
        return new Hydrated(features, proof);
    }

    Features compute(Candidate candidate, OffsetDateTime now, double followerCount, double affinity, long proofCount,
                     ContentProfile profile) {
        CandidatePost post = candidate.post();
        double ageHours = Math.max(0, Duration.between(post.createdAt(), now).toSeconds() / 3600.0);
        double engagement = post.likeCount() + 2.0 * post.repostCount() + 1.5 * post.replyCount();
        double velocity = engagement / Math.pow(ageHours + 2, 1.5);
        return new Features(
                candidate.inNetwork() ? 1 : 0,
                affinity / (affinity + 3),
                1 - 1 / (1.0 + proofCount),
                velocity / (velocity + 1),
                Math.exp(-Math.log(2) * ageHours / properties.recencyHalfLifeHours()),
                Math.min(1, Math.log1p(followerCount) / REPUTATION_SCALE),
                profile.similarity(post.content()));
    }
}
