package com.senotify.pulse.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.OffsetDateTime;

/** Request and response shapes for the identity module. */
public final class IdentityDtos {

    private IdentityDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]{3,20}$",
                    message = "must be 3-20 letters, digits or underscores") String username,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 50) String displayName,
            @NotBlank @Size(min = 8, max = 72) String password) {
    }

    /** {@code login} accepts either a username or an email address. */
    public record LoginRequest(@NotBlank String login, @NotBlank String password) {
    }

    public record UpdateProfileRequest(
            @Size(min = 1, max = 50) String displayName,
            @Size(max = 160) String bio) {
    }

    public record Me(long id, String username, String email, String displayName, String bio,
                     OffsetDateTime createdAt) {
    }

    public record AuthResponse(String accessToken, String tokenType, Instant expiresAt, Me user) {
    }

    public record UserProfile(long id, String username, String displayName, String bio,
                              OffsetDateTime createdAt, long followers, long following, long posts,
                              boolean followedByMe, boolean followsMe, boolean blockedByMe,
                              boolean mutedByMe) {
    }
}
