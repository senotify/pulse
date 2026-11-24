package com.senotify.pulse.recommendation;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Retrieves candidates for one viewer. Sources are independent and cheap; ranking decides what
 * survives. Adding a retrieval strategy (for example embedding similarity) means adding a bean.
 */
public interface CandidateSource {

    List<Candidate> fetch(long viewer, OffsetDateTime now);
}
