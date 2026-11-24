package com.senotify.pulse.interaction;

import com.senotify.pulse.common.Viewer;
import com.senotify.pulse.interaction.InteractionRepository.Event;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Client-observed engagement. Clients batch events and echo back the {@code modelVersion} and
 * {@code position} they received with the feed, which lets a future learned ranker attribute
 * outcomes to the model and slot that produced them.
 */
@RestController
@RequestMapping("/api/interactions")
class InteractionController {

    enum ClientEventType { IMPRESSION, CLICK, DWELL }

    enum Surface { FOR_YOU, FOLLOWING, PROFILE, POST }

    record ClientEvent(@NotNull Long postId,
                       @NotNull ClientEventType type,
                       Surface surface,
                       @Min(0) Integer position,
                       @Min(0) @Max(3_600_000) Integer dwellMs,
                       @Size(max = 40) String modelVersion) {
    }

    record Batch(@NotEmpty @Size(max = 100) List<@Valid @NotNull ClientEvent> events) {
    }

    private final InteractionRepository interactions;

    InteractionController(InteractionRepository interactions) {
        this.interactions = interactions;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Transactional
    public void record(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Batch batch) {
        long viewer = Viewer.id(jwt);
        for (ClientEvent e : batch.events()) {
            interactions.insert(new Event(viewer, e.postId(), e.type().name(),
                    e.surface() == null ? null : e.surface().name(), e.position(), e.dwellMs(),
                    e.modelVersion(), null));
        }
    }
}
