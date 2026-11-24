package com.senotify.pulse.recommendation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.senotify.pulse.common.ApiException;
import com.senotify.pulse.common.PageParams;
import com.senotify.pulse.post.PostService;
import com.senotify.pulse.post.PostView;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * Serves For You pages. The cursor is {@code <snapshot token>.<offset>}. A missing snapshot (expired,
 * or Redis down) is re-ranked rather than failed, so the feed degrades to slightly less stable paging.
 */
@Service
class ForYouService {

    record ForYouItem(PostView post, String source, String reason, double score,
                      @JsonInclude(JsonInclude.Include.NON_NULL) Features features) {
    }

    record ForYouPage(List<ForYouItem> items, String nextCursor, String modelVersion) {
    }

    private record Cursor(String token, int offset) {
    }

    private static final Logger log = LoggerFactory.getLogger(ForYouService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ForYouPipeline pipeline;
    private final FeedSnapshotStore snapshots;
    private final PostService posts;

    ForYouService(ForYouPipeline pipeline, FeedSnapshotStore snapshots, PostService posts) {
        this.pipeline = pipeline;
        this.snapshots = snapshots;
        this.posts = posts;
    }

    ForYouPage page(long viewer, String rawCursor, Integer limit, boolean explain) {
        int size = PageParams.of(null, limit).limit();
        Cursor cursor = rawCursor == null || rawCursor.isBlank() ? new Cursor(newToken(), 0) : parse(rawCursor);

        List<RankedEntry> ranked = (cursor.offset() == 0 ? Optional.<List<RankedEntry>>empty()
                : load(viewer, cursor.token()))
                .orElseGet(() -> {
                    List<RankedEntry> fresh = pipeline.rank(viewer);
                    save(viewer, cursor.token(), fresh);
                    return fresh;
                });

        int from = Math.min(cursor.offset(), ranked.size());
        int to = Math.min(from + size, ranked.size());
        List<RankedEntry> slice = ranked.subList(from, to);

        // Rendering re-applies visibility, so a block or deletion since ranking still takes effect.
        Map<Long, PostView> views = posts.render(slice.stream().map(RankedEntry::postId).toList(), viewer).stream()
                .collect(Collectors.toMap(PostView::id, Function.identity()));
        List<ForYouItem> items = slice.stream()
                .filter(e -> views.containsKey(e.postId()))
                .map(e -> new ForYouItem(views.get(e.postId()), e.source().name(), e.reason(), e.score(),
                        explain ? e.features() : null))
                .toList();

        String next = to < ranked.size() ? cursor.token() + "." + to : null;
        return new ForYouPage(items, next, pipeline.modelVersion());
    }

    private Optional<List<RankedEntry>> load(long viewer, String token) {
        try {
            return snapshots.load(viewer, token);
        } catch (DataAccessException e) {
            log.warn("Feed snapshot unavailable for user {}, re-ranking: {}", viewer, e.getMessage());
            return Optional.empty();
        }
    }

    private void save(long viewer, String token, List<RankedEntry> entries) {
        try {
            snapshots.save(viewer, token, entries);
        } catch (DataAccessException e) {
            log.warn("Could not store feed snapshot for user {}: {}", viewer, e.getMessage());
        }
    }

    private static Cursor parse(String raw) {
        int dot = raw.lastIndexOf('.');
        try {
            String token = raw.substring(0, dot);
            int offset = Integer.parseInt(raw.substring(dot + 1));
            if (!token.matches("[0-9a-f]{16}") || offset < 0) {
                throw new IllegalArgumentException();
            }
            return new Cursor(token, offset);
        } catch (RuntimeException e) {
            throw ApiException.badRequest("Invalid cursor");
        }
    }

    private static String newToken() {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
