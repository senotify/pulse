package com.senotify.pulse.social;

import com.senotify.pulse.common.CursorPage;
import com.senotify.pulse.common.PageParams;
import com.senotify.pulse.common.Viewer;
import com.senotify.pulse.identity.UserSummary;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Graph edges are idempotent resources: PUT creates, DELETE removes, both return 204. */
@RestController
@RequestMapping("/api/users/{username}")
class SocialController {

    private final SocialService social;

    SocialController(SocialService social) {
        this.social = social;
    }

    @PutMapping("/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void follow(@PathVariable String username, @AuthenticationPrincipal Jwt jwt) {
        social.follow(Viewer.id(jwt), username);
    }

    @DeleteMapping("/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unfollow(@PathVariable String username, @AuthenticationPrincipal Jwt jwt) {
        social.unfollow(Viewer.id(jwt), username);
    }

    @PutMapping("/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void block(@PathVariable String username, @AuthenticationPrincipal Jwt jwt) {
        social.block(Viewer.id(jwt), username);
    }

    @DeleteMapping("/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unblock(@PathVariable String username, @AuthenticationPrincipal Jwt jwt) {
        social.unblock(Viewer.id(jwt), username);
    }

    @PutMapping("/mute")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void mute(@PathVariable String username, @AuthenticationPrincipal Jwt jwt) {
        social.mute(Viewer.id(jwt), username);
    }

    @DeleteMapping("/mute")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unmute(@PathVariable String username, @AuthenticationPrincipal Jwt jwt) {
        social.unmute(Viewer.id(jwt), username);
    }

    @GetMapping("/followers")
    CursorPage<UserSummary> followers(@PathVariable String username,
                                      @RequestParam(required = false) String cursor,
                                      @RequestParam(required = false) Integer limit) {
        return social.followers(username, PageParams.of(cursor, limit));
    }

    @GetMapping("/following")
    CursorPage<UserSummary> following(@PathVariable String username,
                                      @RequestParam(required = false) String cursor,
                                      @RequestParam(required = false) Integer limit) {
        return social.following(username, PageParams.of(cursor, limit));
    }
}
