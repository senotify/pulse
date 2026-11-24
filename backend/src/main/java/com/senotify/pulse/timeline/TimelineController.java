package com.senotify.pulse.timeline;

import com.senotify.pulse.common.CursorPage;
import com.senotify.pulse.common.PageParams;
import com.senotify.pulse.common.Viewer;
import com.senotify.pulse.post.PostView;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/timeline")
class TimelineController {

    private final TimelineService timelines;

    TimelineController(TimelineService timelines) {
        this.timelines = timelines;
    }

    /** Reverse-chronological posts from the viewer and the accounts they follow. */
    @GetMapping("/following")
    CursorPage<PostView> following(@AuthenticationPrincipal Jwt jwt,
                                   @RequestParam(required = false) String cursor,
                                   @RequestParam(required = false) Integer limit) {
        return timelines.following(Viewer.id(jwt), PageParams.of(cursor, limit));
    }
}
