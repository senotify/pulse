package com.senotify.pulse.recommendation;

import java.util.List;
import java.util.Optional;

/**
 * Holds a ranked feed between page requests. Ranking is not deterministic over time (recency,
 * new engagement), so paging through a re-ranked list would skip or repeat posts. Instead the first
 * page ranks once and later pages read from the snapshot named in the cursor.
 */
public interface FeedSnapshotStore {

    void save(long viewer, String token, List<RankedEntry> entries);

    Optional<List<RankedEntry>> load(long viewer, String token);
}
