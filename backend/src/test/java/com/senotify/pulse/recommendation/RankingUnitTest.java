package com.senotify.pulse.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.senotify.pulse.recommendation.Candidate.CandidatePost;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pure unit tests for the ranking stages; no Spring context. */
class RankingUnitTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-24T12:00:00Z");
    private final RecommendationProperties props = RecommendationProperties.defaults();
    private final FeedMixer mixer = new FeedMixer(props);
    private final FeatureHydrator hydrator = new FeatureHydrator(null, props);

    @Test
    void mixerPenalizesRepeatedAuthors() {
        var a10 = scored(1, 1, true, 10);
        var a9 = scored(2, 1, true, 9);
        var a8 = scored(3, 1, true, 8);
        var b7 = scored(4, 2, true, 7);

        assertThat(mixer.mix(List.of(a10, a9, a8, b7), 10)).containsExactly(a10, b7, a9, a8);
    }

    @Test
    void mixerCapsOutOfNetworkWhileInNetworkRemains() {
        var in1 = scored(1, 1, true, 1);
        var in2 = scored(2, 2, true, 1);
        var out9 = scored(3, 3, false, 9);
        var out8 = scored(4, 4, false, 8);
        var out7 = scored(5, 5, false, 7);

        assertThat(mixer.mix(List.of(out9, out8, out7, in1, in2), 10))
                .extracting(ScoredCandidate::inNetwork)
                .containsExactly(true, false, true, false, false);
    }

    @Test
    void mixerHonoursSize() {
        assertThat(mixer.mix(List.of(scored(1, 1, true, 3), scored(2, 2, true, 2), scored(3, 3, true, 1)), 2))
                .hasSize(2);
    }

    @Test
    void contentProfileIgnoresStopwordsAndRewardsOverlap() {
        assertThat(ContentProfile.terms("The #Java and Spring is GREAT")).containsExactlyInAnyOrder(
                "#java", "spring", "great");

        ContentProfile profile = ContentProfile.of(List.of("spring boot tips", "spring security", "#java records"));
        assertThat(profile.similarity("new spring release")).isGreaterThan(profile.similarity("cooking pasta"));
        assertThat(profile.similarity("cooking pasta")).isZero();
        assertThat(ContentProfile.EMPTY.similarity("spring")).isZero();
    }

    @Test
    void featuresAreNormalizedAndBehaveAsDocumented() {
        Features fresh = hydrator.compute(candidate(NOW, 0, true), NOW, 0, 0, 0, ContentProfile.EMPTY);
        assertThat(fresh.recency()).isCloseTo(1.0, within(1e-9));
        assertThat(fresh.engagementVelocity()).isZero();
        assertThat(fresh.inNetwork()).isEqualTo(1);
        assertThat(fresh.socialProof()).isZero();

        Features halfLife = hydrator.compute(candidate(NOW.minusHours(6), 0, false), NOW, 0, 0, 1,
                ContentProfile.EMPTY);
        assertThat(halfLife.recency()).isCloseTo(0.5, within(1e-9));
        assertThat(halfLife.socialProof()).isCloseTo(0.5, within(1e-9));
        assertThat(halfLife.inNetwork()).isZero();

        Features popular = hydrator.compute(candidate(NOW.minusHours(1), 50, false), NOW, 10_000, 3, 3,
                ContentProfile.EMPTY);
        Features quiet = hydrator.compute(candidate(NOW.minusHours(1), 1, false), NOW, 10, 0, 0,
                ContentProfile.EMPTY);
        assertThat(popular.engagementVelocity()).isGreaterThan(quiet.engagementVelocity()).isLessThan(1);
        assertThat(popular.authorReputation()).isCloseTo(1.0, within(1e-9));
        assertThat(popular.authorAffinity()).isCloseTo(0.5, within(1e-9));

        var ranker = new WeightedRanker(props);
        assertThat(ranker.score(popular)).isGreaterThan(ranker.score(quiet));
    }

    private static Candidate candidate(OffsetDateTime createdAt, int likes, boolean inNetwork) {
        var post = new CandidatePost(1, 1, "hello", createdAt, likes, 0, 0);
        return Candidate.of(post, inNetwork ? CandidateSourceType.IN_NETWORK : CandidateSourceType.TRENDING);
    }

    private static ScoredCandidate scored(long postId, long authorId, boolean inNetwork, double score) {
        var post = new CandidatePost(postId, authorId, "x", NOW, 0, 0, 0);
        var source = inNetwork ? CandidateSourceType.IN_NETWORK : CandidateSourceType.TRENDING;
        return new ScoredCandidate(Candidate.of(post, source), null, score);
    }
}
