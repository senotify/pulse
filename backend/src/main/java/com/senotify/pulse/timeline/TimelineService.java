package com.senotify.pulse.timeline;

import com.senotify.pulse.common.CursorPage;
import com.senotify.pulse.common.PageParams;
import com.senotify.pulse.post.PostService;
import com.senotify.pulse.post.PostView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * Reads the Following timeline: cached entries from the store, merged with recent posts from
 * high-reach accounts the viewer follows, then filtered for mutes, blocks and duplicate reposts.
 */
@Service
class TimelineService {

    private static final Logger log = LoggerFactory.getLogger(TimelineService.class);
    /** Candidate batches per request; bounds work when filtering discards a lot. */
    private static final int MAX_ROUNDS = 4;

    private final TimelineStore store;
    private final TimelineRepository repository;
    private final TimelineProperties properties;
    private final PostService posts;

    TimelineService(TimelineStore store, TimelineRepository repository, TimelineProperties properties,
                    PostService posts) {
        this.store = store;
        this.repository = repository;
        this.properties = properties;
        this.posts = posts;
    }

    CursorPage<PostView> following(long viewer, PageParams page) {
        CandidateSource source = candidateSource(viewer);
        Set<Long> muted = repository.mutedIds(viewer);
        Set<Long> shown = new HashSet<>();
        List<PostView> items = new ArrayList<>(page.limit());

        int batchSize = page.limit() * 2;
        Long cursor = page.cursor();
        boolean more = true;
        for (int round = 0; round < MAX_ROUNDS && more && items.size() < page.limit(); round++) {
            List<TimelineEntry> batch = source.next(cursor, batchSize);
            more = batch.size() == batchSize;
            Map<Long, PostView> rendered = posts.render(batch.stream().map(TimelineEntry::postId).toList(), viewer)
                    .stream().collect(Collectors.toMap(PostView::id, Function.identity()));
            for (int i = 0; i < batch.size(); i++) {
                cursor = batch.get(i).postId();
                PostView view = rendered.get(cursor);
                if (view != null && !isMuted(view, muted) && shown.add(contentId(view))) {
                    items.add(view);
                    if (items.size() == page.limit()) {
                        more = more || i < batch.size() - 1;
                        break;
                    }
                }
            }
        }
        return new CursorPage<>(items, more && cursor != null ? Long.toString(cursor) : null);
    }

    @FunctionalInterface
    private interface CandidateSource {
        List<TimelineEntry> next(Long beforeId, int count);
    }

    /**
     * Prefers the cache, building it first if absent. If Redis is unavailable the timeline is served
     * straight from PostgreSQL, which is slower but complete.
     */
    private CandidateSource candidateSource(long viewer) {
        CandidateSource database = (before, count) -> repository.home(viewer, before, count);
        List<Long> highReach = repository.followedHighReachAuthors(viewer, properties.fanoutThreshold());
        try {
            if (!store.exists(viewer)) {
                rebuild(viewer, highReach);
            }
        } catch (DataAccessException e) {
            return fallback(viewer, e, database);
        }
        return (before, count) -> {
            List<TimelineEntry> cached;
            try {
                cached = store.page(viewer, before, count);
            } catch (DataAccessException e) {
                return fallback(viewer, e, database).next(before, count);
            }
            return merge(cached, repository.recentByAuthors(highReach, before, count), count);
        };
    }

    private CandidateSource fallback(long viewer, DataAccessException e, CandidateSource database) {
        if (isDatabaseFailure(e)) {
            throw e;
        }
        log.warn("Timeline store unavailable, serving user {} from PostgreSQL: {}", viewer, e.getMessage());
        return database;
    }

    /** High-reach authors are left out of the cache; they are merged in on every read. */
    private void rebuild(long viewer, List<Long> highReach) {
        Set<Long> excluded = new HashSet<>(highReach);
        List<TimelineEntry> entries = repository.home(viewer, null, properties.capacity()).stream()
                .filter(e -> !excluded.contains(e.authorId()))
                .toList();
        store.replace(viewer, entries);
    }

    private static List<TimelineEntry> merge(List<TimelineEntry> cached, List<TimelineEntry> pulled, int count) {
        return Stream.concat(cached.stream(), pulled.stream())
                .sorted(Comparator.comparingLong(TimelineEntry::postId).reversed())
                .distinct()
                .limit(count)
                .toList();
    }

    private static boolean isMuted(PostView view, Set<Long> muted) {
        return muted.contains(view.author().id())
                || (view.repostOf() != null && muted.contains(view.repostOf().author().id()));
    }

    /** An original and reposts of it share this id, so the timeline shows the newest one only. */
    private static long contentId(PostView view) {
        return view.repostOf() != null ? view.repostOf().id() : view.id();
    }

    private static boolean isDatabaseFailure(DataAccessException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof java.sql.SQLException) {
                return true;
            }
        }
        return false;
    }
}
