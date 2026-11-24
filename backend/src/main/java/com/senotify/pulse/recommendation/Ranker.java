package com.senotify.pulse.recommendation;

/**
 * Scores a candidate from its features. The seam for a learned model: an ONNX Runtime implementation
 * can replace the weighted ranker without touching retrieval, filtering or mixing. The version is
 * returned with every feed page and echoed back in interaction events, so outcomes are attributable.
 */
public interface Ranker {

    String version();

    double score(Features features);
}
