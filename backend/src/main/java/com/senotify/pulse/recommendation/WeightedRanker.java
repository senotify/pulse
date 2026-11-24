package com.senotify.pulse.recommendation;

import com.senotify.pulse.recommendation.RecommendationProperties.Weights;
import org.springframework.stereotype.Component;

/** A transparent linear model: score = Σ weight × feature. Weights come from configuration. */
@Component
public class WeightedRanker implements Ranker {

    private final Weights w;

    public WeightedRanker(RecommendationProperties properties) {
        this.w = properties.weights();
    }

    @Override
    public String version() {
        return "weighted-v1";
    }

    @Override
    public double score(Features f) {
        return w.inNetwork() * f.inNetwork()
                + w.authorAffinity() * f.authorAffinity()
                + w.socialProof() * f.socialProof()
                + w.engagementVelocity() * f.engagementVelocity()
                + w.recency() * f.recency()
                + w.authorReputation() * f.authorReputation()
                + w.contentAffinity() * f.contentAffinity();
    }
}
