package com.senotify.pulse.common;

/** Keyset pagination input: an opaque cursor (the last seen id) and a clamped page size. */
public record PageParams(Long cursor, int limit) {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 50;

    public static PageParams of(String cursor, Integer limit) {
        int size = limit == null ? DEFAULT_LIMIT : Math.clamp(limit, 1, MAX_LIMIT);
        if (cursor == null || cursor.isBlank()) {
            return new PageParams(null, size);
        }
        try {
            return new PageParams(Long.parseLong(cursor), size);
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("Invalid cursor");
        }
    }

    /** Repositories fetch one extra row to learn whether another page exists. */
    public int fetchSize() {
        return limit + 1;
    }
}
