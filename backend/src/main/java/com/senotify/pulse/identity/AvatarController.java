package com.senotify.pulse.identity;

import com.senotify.pulse.common.ApiException;
import com.senotify.pulse.common.Images;
import com.senotify.pulse.common.Viewer;
import com.senotify.pulse.identity.IdentityDtos.Me;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Upload, remove and serve profile photos. */
@RestController
class AvatarController {

    static final long MAX_UPLOAD_BYTES = 5 * 1024 * 1024;
    static final int AVATAR_SIZE = 400;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcClient jdbc;
    private final IdentityService identity;

    AvatarController(JdbcClient jdbc, IdentityService identity) {
        this.jdbc = jdbc;
        this.identity = identity;
    }

    @PutMapping(path = "/api/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public Me upload(@AuthenticationPrincipal Jwt jwt, @RequestParam("file") MultipartFile file) throws IOException {
        long userId = Viewer.id(jwt);
        if (file.isEmpty()) {
            throw ApiException.badRequest("Choose an image to upload");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw ApiException.badRequest("Image must be 5 MB or smaller");
        }
        byte[] jpeg = Images.square(file.getBytes(), AVATAR_SIZE).bytes();

        jdbc.sql("DELETE FROM avatars WHERE user_id = :id").param("id", userId).update();
        jdbc.sql("INSERT INTO avatars (user_id, image) VALUES (:id, :image)")
                .param("id", userId)
                .param("image", jpeg)
                .update();
        jdbc.sql("UPDATE users SET avatar_key = :key WHERE id = :id")
                .param("key", newKey())
                .param("id", userId)
                .update();
        return identity.me(userId);
    }

    @DeleteMapping("/api/me/avatar")
    @Transactional
    public Me remove(@AuthenticationPrincipal Jwt jwt) {
        long userId = Viewer.id(jwt);
        jdbc.sql("DELETE FROM avatars WHERE user_id = :id").param("id", userId).update();
        jdbc.sql("UPDATE users SET avatar_key = NULL WHERE id = :id").param("id", userId).update();
        return identity.me(userId);
    }

    /**
     * Clients request {@code /api/avatars/{id}?v={avatarKey}}. The key changes on every upload, so the
     * response can be cached as immutable.
     */
    @GetMapping("/api/avatars/{userId}")
    ResponseEntity<byte[]> image(@PathVariable long userId) {
        byte[] image = jdbc.sql("SELECT image FROM avatars WHERE user_id = :id")
                .param("id", userId)
                .query(byte[].class)
                .optional()
                .orElseThrow(() -> ApiException.notFound("No profile photo"));
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(image);
    }

    private static String newKey() {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
