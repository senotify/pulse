package com.senotify.pulse.post;

/** A photo attached to a post; the image is served at /api/media/{id}. Dimensions let clients reserve layout space. */
public record MediaView(long id, int width, int height) {
}
