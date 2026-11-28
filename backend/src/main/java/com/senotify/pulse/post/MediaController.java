package com.senotify.pulse.post;

import com.senotify.pulse.common.ApiException;
import com.senotify.pulse.common.Images;
import com.senotify.pulse.common.Viewer;
import com.senotify.pulse.ratelimit.RateLimits;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Photo uploads for posts. Clients upload each photo as it is picked, then pass the returned ids
 * when creating the post, so the upload happens while the user is still typing.
 */
@RestController
class MediaController {

    static final int MAX_SIDE = 1600;
    static final int MAX_UNCLAIMED = 20;
    private static final long MAX_UPLOAD_BYTES = 5 * 1024 * 1024;
    private static final Logger log = LoggerFactory.getLogger(MediaController.class);

    private final MediaRepository media;
    private final RateLimits rateLimits;

    MediaController(MediaRepository media, RateLimits rateLimits) {
        this.media = media;
        this.rateLimits = rateLimits;
    }

    @PostMapping(path = "/api/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MediaView upload(@AuthenticationPrincipal Jwt jwt, @RequestParam("file") MultipartFile file)
            throws IOException {
        long owner = Viewer.id(jwt);
        rateLimits.upload(owner);
        if (file.isEmpty()) {
            throw ApiException.badRequest("Choose an image to upload");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw ApiException.badRequest("Image must be 5 MB or smaller");
        }
        if (media.unclaimedCount(owner) >= MAX_UNCLAIMED) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many unposted uploads; post or wait an hour");
        }
        return media.insert(owner, Images.fit(file.getBytes(), MAX_SIDE));
    }

    /** Media content never changes for an id, so it is cacheable forever. */
    @GetMapping("/api/media/{id}")
    ResponseEntity<byte[]> image(@PathVariable long id) {
        byte[] image = media.image(id).orElseThrow(() -> ApiException.notFound("Media not found"));
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(image);
    }

    @Scheduled(fixedDelayString = "PT30M", initialDelayString = "PT5M")
    void deleteAbandonedUploads() {
        int deleted = media.deleteUnclaimedBefore(OffsetDateTime.now().minusHours(1));
        if (deleted > 0) {
            log.info("Deleted {} unposted media uploads", deleted);
        }
    }
}
