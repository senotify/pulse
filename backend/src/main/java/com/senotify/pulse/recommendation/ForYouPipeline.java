package com.senotify.pulse.recommendation;

import com.senotify.pulse.recommendation.FeatureHydrator.Hydrated;
import com.senotify.pulse.recommendation.RecommendationRepository.SocialProof;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The For You pipeline, following the stages of X's home-mixer:
 * <ol>
 *   <li><b>retrieve</b> candidates from every {@link CandidateSource} and merge duplicates;</li>
 *   <li><b>filter</b> the viewer's own posts, blocked or muted authors, and posts already seen;</li>
 *   <li><b>hydrate</b> features in batched queries;</li>
 *   <li><b>score</b> with the {@link Ranker};</li>
 *   <li><b>mix</b> for diversity and network balance.</li>
 * </ol>
 * Filtering before hydration keeps feature queries proportional to what can actually be shown.
 */
@Component
class ForYouPipeline {

    private final List<CandidateSource> sources;
    private final RecommendationRepository repository;
    private final FeatureHydrator hydrator;
    private final Ranker ranker;
    private final FeedMixer mixer;
    private final RecommendationProperties properties;
    private final Clock clock = Clock.systemUTC();

    ForYouPipeline(List<CandidateSource> sources, RecommendationRepository repository, FeatureHydrator hydrator,
                   Ranker ranker, FeedMixer mixer, RecommendationProperties properties) {
        this.sources = sources;
        this.repository = repository;
        this.hydrator = hydrator;
        this.ranker = ranker;
        this.mixer = mixer;
        this.properties = properties;
    }

    List<RankedEntry> rank(long viewer) {
        OffsetDateTime now = OffsetDateTime.now(clock);

        Map<Long, Candidate> merged = new LinkedHashMap<>();
        for (CandidateSource source : sources) {
            for (Candidate candidate : source.fetch(viewer, now)) {
                merged.merge(candidate.postId(), candidate, Candidate::merge);
            }
        }

        Set<Long> excludedAuthors = repository.excludedAuthors(viewer);
        Set<Long> seen = repository.seen(viewer, merged.keySet(), now.minus(properties.seenWindow()));
        List<Candidate> eligible = merged.values().stream()
                .filter(c -> c.authorId() != viewer)
                .filter(c -> !excludedAuthors.contains(c.authorId()))
                .filter(c -> !seen.contains(c.postId()))
                .toList();

        Hydrated hydrated = hydrator.hydrate(viewer, eligible, now);
        List<ScoredCandidate> scored = eligible.stream()
                .map(c -> {
                    Features f = hydrated.features().get(c.postId());
                    return new ScoredCandidate(c, f, ranker.score(f));
                })
                .toList();

        return mixer.mix(scored, properties.snapshotSize()).stream()
                .map(s -> new RankedEntry(s.candidate().postId(), s.candidate().primarySource(),
                        reason(s.candidate(), hydrated.socialProof().get(s.candidate().postId())),
                        s.score(), s.features()))
                .toList();
    }

    String modelVersion() {
        return ranker.version();
    }

    private static String reason(Candidate candidate, SocialProof proof) {
        return switch (candidate.primarySource()) {
            case IN_NETWORK -> "From an account you follow";
            case ENGAGED_BY_FOLLOWING -> proof == null
                    ? "Liked or reposted by accounts you follow"
                    : proof.proofCount() == 1
                            ? "Liked or reposted by @" + proof.sampleUsername()
                            : "Liked or reposted by @" + proof.sampleUsername() + " and "
                                    + (proof.proofCount() - 1) + " more you follow";
            case FOLLOWED_BY_FOLLOWING -> "Followed by accounts you follow";
            case TRENDING -> "Popular right now";
        };
    }
}
