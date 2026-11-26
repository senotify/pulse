package com.senotify.pulse.discovery;

import com.senotify.pulse.common.ApiException;
import com.senotify.pulse.common.CursorPage;
import com.senotify.pulse.common.PageParams;
import com.senotify.pulse.common.Viewer;
import com.senotify.pulse.discovery.DiscoveryRepository.Suggestion;
import com.senotify.pulse.discovery.TrendService.Trend;
import com.senotify.pulse.post.PostService;
import com.senotify.pulse.post.PostView;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Trends, who-to-follow and search: the surfaces that help people find content beyond their graph. */
@RestController
@RequestMapping("/api")
class DiscoveryController {

    private final TrendService trends;
    private final DiscoveryRepository repository;
    private final PostService posts;

    DiscoveryController(TrendService trends, DiscoveryRepository repository, PostService posts) {
        this.trends = trends;
        this.repository = repository;
        this.posts = posts;
    }

    @GetMapping("/discover/trends")
    List<Trend> trends() {
        return trends.trending();
    }

    @GetMapping("/discover/users")
    List<Suggestion> whoToFollow(@AuthenticationPrincipal Jwt jwt,
                                 @RequestParam(defaultValue = "3") int limit) {
        return repository.suggestions(Viewer.id(jwt), Math.clamp(limit, 1, 20));
    }

    /** People search for the typeahead and the People tab; a leading "@" is ignored. */
    @GetMapping("/search/users")
    List<DiscoveryRepository.UserResult> searchUsers(@AuthenticationPrincipal Jwt jwt,
                                                     @RequestParam String q,
                                                     @RequestParam(defaultValue = "10") int limit) {
        String term = q.strip();
        if (term.startsWith("@")) {
            term = term.substring(1);
        }
        if (term.isEmpty() || term.length() > 50) {
            throw ApiException.badRequest("Search term must be 1-50 characters");
        }
        return repository.searchUsers(term, Viewer.idOrAnonymous(jwt), Math.clamp(limit, 1, 20));
    }

    /** Case-insensitive substring search over post text; hashtags are searched as "#tag". */
    @GetMapping("/search/posts")
    CursorPage<PostView> search(@AuthenticationPrincipal Jwt jwt,
                                @RequestParam String q,
                                @RequestParam(required = false) String cursor,
                                @RequestParam(required = false) Integer limit) {
        String term = q.strip();
        if (term.length() < 2 || term.length() > 100) {
            throw ApiException.badRequest("Search term must be 2-100 characters");
        }
        PageParams page = PageParams.of(cursor, limit);
        List<Long> ids = repository.searchPostIds(term, page.cursor(), page.fetchSize());
        // Paginate on the raw ids, then render: hidden posts drop out without shifting the cursor.
        CursorPage<Long> raw = CursorPage.of(ids, page, Long::longValue);
        return new CursorPage<>(posts.render(raw.items(), Viewer.idOrAnonymous(jwt)), raw.nextCursor());
    }
}
