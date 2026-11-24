package com.senotify.pulse.recommendation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Turns scores into a feed order. Greedy selection: at each slot, take the best candidate after
 * <ul>
 *   <li><b>author diversity</b>: every post already picked from the same author multiplies the
 *       score by {@code authorPenalty}, so one prolific account cannot fill the page;</li>
 *   <li><b>network balance</b>: out-of-network posts may not exceed {@code maxOutOfNetworkShare} of
 *       the feed while in-network candidates remain.</li>
 * </ul>
 * O(size × candidates), fine for the few hundred candidates per request.
 */
@Component
public class FeedMixer {

    private final double authorPenalty;
    private final double maxOutOfNetworkShare;

    public FeedMixer(RecommendationProperties properties) {
        this.authorPenalty = properties.authorPenalty();
        this.maxOutOfNetworkShare = properties.maxOutOfNetworkShare();
    }

    public List<ScoredCandidate> mix(List<ScoredCandidate> scored, int size) {
        List<ScoredCandidate> remaining = new ArrayList<>(scored);
        remaining.sort(Comparator.comparingDouble(ScoredCandidate::score).reversed());
        long inNetworkLeft = remaining.stream().filter(ScoredCandidate::inNetwork).count();

        List<ScoredCandidate> picked = new ArrayList<>(Math.min(size, remaining.size()));
        Map<Long, Integer> perAuthor = new HashMap<>();
        int outOfNetwork = 0;

        while (picked.size() < size && !remaining.isEmpty()) {
            boolean outOfNetworkAllowed = inNetworkLeft == 0
                    || outOfNetwork + 1 <= maxOutOfNetworkShare * (picked.size() + 1);
            int bestIndex = -1;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < remaining.size(); i++) {
                ScoredCandidate c = remaining.get(i);
                if (!c.inNetwork() && !outOfNetworkAllowed) {
                    continue;
                }
                double adjusted = c.score() * Math.pow(authorPenalty, perAuthor.getOrDefault(c.authorId(), 0));
                if (adjusted > bestScore) {
                    bestScore = adjusted;
                    bestIndex = i;
                }
            }
            ScoredCandidate best = remaining.remove(bestIndex);
            picked.add(best);
            perAuthor.merge(best.authorId(), 1, Integer::sum);
            if (best.inNetwork()) {
                inNetworkLeft--;
            } else {
                outOfNetwork++;
            }
        }
        return picked;
    }
}
