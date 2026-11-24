package com.senotify.pulse.timeline;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import org.springframework.data.redis.RedisConnectionFailureException;

/** Same contract as the Redis store, in memory; {@link #failing} simulates a Redis outage. */
public class InMemoryTimelineStore implements TimelineStore {

    private final Map<Long, ConcurrentSkipListMap<Long, Long>> timelines = new ConcurrentHashMap<>();
    private final int capacity;
    volatile boolean failing;

    public InMemoryTimelineStore(int capacity) {
        this.capacity = capacity;
    }

    @Override
    public boolean exists(long userId) {
        check();
        return timelines.containsKey(userId);
    }

    @Override
    public void addIfPresent(long userId, Collection<TimelineEntry> entries) {
        check();
        timelines.computeIfPresent(userId, (id, timeline) -> {
            entries.forEach(e -> timeline.put(e.postId(), e.authorId()));
            while (timeline.size() > capacity) {
                timeline.pollFirstEntry();
            }
            return timeline;
        });
    }

    @Override
    public void replace(long userId, List<TimelineEntry> entries) {
        check();
        var timeline = new ConcurrentSkipListMap<Long, Long>();
        entries.stream().limit(capacity).forEach(e -> timeline.put(e.postId(), e.authorId()));
        timelines.put(userId, timeline);
    }

    @Override
    public List<TimelineEntry> page(long userId, Long beforeId, int count) {
        check();
        NavigableMap<Long, Long> timeline = timelines.get(userId);
        if (timeline == null) {
            return List.of();
        }
        NavigableMap<Long, Long> view = beforeId == null ? timeline : timeline.headMap(beforeId, false);
        return view.descendingMap().entrySet().stream()
                .limit(count)
                .map(e -> new TimelineEntry(e.getKey(), e.getValue()))
                .toList();
    }

    @Override
    public void removeAuthor(long userId, long authorId) {
        check();
        NavigableMap<Long, Long> timeline = timelines.get(userId);
        if (timeline != null) {
            timeline.values().removeIf(author -> author == authorId);
        }
    }

    @Override
    public void evict(long userId) {
        check();
        timelines.remove(userId);
    }

    private void check() {
        if (failing) {
            throw new RedisConnectionFailureException("simulated outage");
        }
    }
}
