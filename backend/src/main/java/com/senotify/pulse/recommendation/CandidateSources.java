package com.senotify.pulse.recommendation;

import com.senotify.pulse.recommendation.Candidate.CandidatePost;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The four retrieval strategies, mirroring the in-network / out-of-network split in X's home-mixer. */
@Configuration
class CandidateSources {

    @Bean
    CandidateSource inNetworkSource(RecommendationRepository repository, RecommendationProperties props) {
        return (viewer, now) -> tag(CandidateSourceType.IN_NETWORK,
                repository.inNetwork(viewer, now.minus(props.candidateWindow()), props.sourceLimit()));
    }

    @Bean
    CandidateSource engagedByFollowingSource(RecommendationRepository repository, RecommendationProperties props) {
        return (viewer, now) -> tag(CandidateSourceType.ENGAGED_BY_FOLLOWING,
                repository.engagedByFollowing(viewer, now.minus(props.candidateWindow()), props.sourceLimit()));
    }

    @Bean
    CandidateSource followedByFollowingSource(RecommendationRepository repository, RecommendationProperties props) {
        return (viewer, now) -> tag(CandidateSourceType.FOLLOWED_BY_FOLLOWING,
                repository.followedByFollowing(viewer, now.minus(props.candidateWindow()), props.sourceLimit()));
    }

    @Bean
    CandidateSource trendingSource(RecommendationRepository repository, RecommendationProperties props) {
        return (viewer, now) -> tag(CandidateSourceType.TRENDING,
                repository.trending(viewer, now.minus(props.trendingWindow()), props.sourceLimit()));
    }

    private static List<Candidate> tag(CandidateSourceType type, List<CandidatePost> posts) {
        return posts.stream().map(post -> Candidate.of(post, type)).toList();
    }
}
