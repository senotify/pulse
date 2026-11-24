package com.senotify.pulse.recommendation;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A bag-of-terms profile built from posts the viewer liked. Deliberately simple: it stands in for
 * the embedding similarity used at scale (SimClusters / two-tower models) until there is data to
 * train one.
 */
final class ContentProfile {

    private static final Pattern SPLIT = Pattern.compile("[^\\p{L}\\p{N}#_]+");
    private static final Set<String> STOPWORDS = Set.of(
            "the", "and", "for", "are", "but", "not", "you", "all", "any", "can", "had", "her", "was", "one",
            "our", "out", "has", "have", "this", "that", "with", "from", "they", "will", "just", "what",
            "about", "your", "their", "there", "been", "were", "when", "which", "would", "into", "than",
            "then", "them", "these", "some", "its", "also", "very", "yang", "dan", "ini", "itu", "untuk",
            "dengan", "tidak", "ada", "aku", "kamu", "saya", "dari", "juga", "sudah", "bisa");

    static final ContentProfile EMPTY = new ContentProfile(Map.of());

    private final Map<String, Integer> termFrequency;
    private final int maxFrequency;

    private ContentProfile(Map<String, Integer> termFrequency) {
        this.termFrequency = termFrequency;
        this.maxFrequency = termFrequency.values().stream().mapToInt(Integer::intValue).max().orElse(1);
    }

    static ContentProfile of(List<String> texts) {
        Map<String, Integer> tf = new HashMap<>();
        for (String text : texts) {
            for (String term : terms(text)) {
                tf.merge(term, 1, Integer::sum);
            }
        }
        return tf.isEmpty() ? EMPTY : new ContentProfile(tf);
    }

    /** Mean of the candidate's terms' relative frequency in the profile, in [0, 1]. */
    double similarity(String text) {
        if (termFrequency.isEmpty() || text == null) {
            return 0;
        }
        Set<String> terms = terms(text);
        if (terms.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (String term : terms) {
            sum += termFrequency.getOrDefault(term, 0) / (double) maxFrequency;
        }
        return sum / terms.size();
    }

    static Set<String> terms(String text) {
        if (text == null) {
            return Set.of();
        }
        Set<String> terms = new java.util.HashSet<>();
        for (String raw : SPLIT.split(text.toLowerCase(Locale.ROOT))) {
            String term = raw.startsWith("#") ? raw : raw.replace("#", "");
            if (term.length() >= 3 && !STOPWORDS.contains(term)) {
                terms.add(term);
            }
        }
        return terms;
    }
}
