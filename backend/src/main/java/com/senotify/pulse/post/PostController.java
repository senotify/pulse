package com.senotify.pulse.post;

import com.senotify.pulse.common.CursorPage;
import com.senotify.pulse.common.PageParams;
import com.senotify.pulse.common.Viewer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class PostController {

    /** {@code content} may be empty when {@code mediaIds} (from POST /api/media) are given. */
    record CreatePostRequest(@Size(max = 280) String content, Long replyToId, @Size(max = 4) List<Long> mediaIds) {
    }

    private final PostService posts;

    PostController(PostService posts) {
        this.posts = posts;
    }

    @PostMapping("/posts")
    @ResponseStatus(HttpStatus.CREATED)
    PostView create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreatePostRequest request) {
        return posts.create(Viewer.id(jwt), request.content(), request.replyToId(), request.mediaIds());
    }

    @GetMapping("/posts/{id}")
    PostView get(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return posts.get(id, Viewer.idOrAnonymous(jwt));
    }

    @DeleteMapping("/posts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        posts.delete(Viewer.id(jwt), id);
    }

    @GetMapping("/posts/{id}/ancestors")
    java.util.List<PostView> ancestors(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return posts.ancestors(id, Viewer.idOrAnonymous(jwt));
    }

    @GetMapping("/posts/{id}/replies")
    CursorPage<PostView> replies(@PathVariable long id, @AuthenticationPrincipal Jwt jwt,
                                 @RequestParam(required = false) String cursor,
                                 @RequestParam(required = false) Integer limit) {
        return posts.replies(id, Viewer.idOrAnonymous(jwt), PageParams.of(cursor, limit));
    }

    @PutMapping("/posts/{id}/like")
    PostView like(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return posts.like(Viewer.id(jwt), id);
    }

    @DeleteMapping("/posts/{id}/like")
    PostView unlike(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return posts.unlike(Viewer.id(jwt), id);
    }

    @PutMapping("/posts/{id}/repost")
    PostView repost(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return posts.repost(Viewer.id(jwt), id);
    }

    @DeleteMapping("/posts/{id}/repost")
    PostView unrepost(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return posts.unrepost(Viewer.id(jwt), id);
    }

    /** Profile tabs: {@code filter=posts} (default, no replies), {@code replies} (everything) or {@code media}. */
    @GetMapping("/users/{username}/posts")
    CursorPage<PostView> byAuthor(@PathVariable String username, @AuthenticationPrincipal Jwt jwt,
                                  @RequestParam(defaultValue = "posts") String filter,
                                  @RequestParam(required = false) String cursor,
                                  @RequestParam(required = false) Integer limit) {
        PostRepository.AuthorFilter tab;
        try {
            tab = PostRepository.AuthorFilter.valueOf(filter.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw com.senotify.pulse.common.ApiException.badRequest("filter must be posts, replies or media");
        }
        return posts.byAuthor(username, Viewer.idOrAnonymous(jwt), tab, PageParams.of(cursor, limit));
    }

    /** The Likes profile tab: posts the user liked, most recently liked first. */
    @GetMapping("/users/{username}/likes")
    CursorPage<PostView> likes(@PathVariable String username, @AuthenticationPrincipal Jwt jwt,
                               @RequestParam(required = false) String cursor,
                               @RequestParam(required = false) Integer limit) {
        return posts.likedBy(username, Viewer.idOrAnonymous(jwt), cursor, limit);
    }
}
