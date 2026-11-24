package com.senotify.pulse.common;

import java.util.List;
import java.util.function.ToLongFunction;

public record CursorPage<T>(List<T> items, String nextCursor) {

    /** Builds a page from a result fetched with {@link PageParams#fetchSize()} rows. */
    public static <T> CursorPage<T> of(List<T> fetched, PageParams page, ToLongFunction<T> key) {
        if (fetched.size() <= page.limit()) {
            return new CursorPage<>(List.copyOf(fetched), null);
        }
        List<T> items = List.copyOf(fetched.subList(0, page.limit()));
        return new CursorPage<>(items, Long.toString(key.applyAsLong(items.getLast())));
    }
}
