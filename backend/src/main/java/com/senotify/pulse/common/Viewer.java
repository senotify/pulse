package com.senotify.pulse.common;

import org.springframework.security.oauth2.jwt.Jwt;

/** Resolves the calling user from the bearer token. */
public final class Viewer {

    /** Used in queries for anonymous callers; user ids start at 1, so it never matches a row. */
    public static final long ANONYMOUS = 0L;

    private Viewer() {
    }

    public static long id(Jwt jwt) {
        if (jwt == null) {
            throw ApiException.unauthorized("Authentication required");
        }
        return Long.parseLong(jwt.getSubject());
    }

    public static long idOrAnonymous(Jwt jwt) {
        return jwt == null ? ANONYMOUS : Long.parseLong(jwt.getSubject());
    }
}
