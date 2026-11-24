package com.senotify.pulse.post;

import com.senotify.pulse.common.ApiException;
import com.senotify.pulse.common.CursorPage;
import com.senotify.pulse.common.OutboxWriter;
import com.senotify.pulse.common.PageParams;
import com.senotify.pulse.identity.UserRepository;
import com.senotify.pulse.post.PostRepository.Counter;
import com.senotify.pulse.post.PostRepository.PostCore;
import com.senotify.pulse.post.PostRepository.PostRow;
import com.senotify.pulse.social.SocialRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostService {

    private final PostRepository posts;
    private final UserRepository users;
    private final SocialRepository graph;
    private final OutboxWriter outbox;

    PostService(PostRepository posts, UserRepository users, SocialRepository graph, OutboxWriter outbox) {
        this.posts = posts;
        this.users = users;
        this.graph = graph;
        this.outbox = outbox;
    }

    @Transactional
    PostView create(long viewer, String content, Long replyToId) {
        String text = content.strip();
        if (text.isEmpty()) {
            throw ApiException.badRequest("Post cannot be blank");
        }
        Long parentId = null;
        if (replyToId != null) {
            PostCore parent = original(existing(replyToId));
            requireNotBlocked(viewer, parent.authorId(), "You cannot reply to this post");
            parentId = parent.id();
        }
        long id = posts.insert(viewer, text, parentId, null);
        if (parentId != null) {
            posts.adjust(parentId, Counter.REPLIES, 1);
        }
        var payload = new HashMap<String, Object>();
        payload.put("postId", id);
        payload.put("authorId", viewer);
        payload.put("replyToId", parentId);
        outbox.append("post", id, "PostCreated", payload);
        return get(id, viewer);
    }

    @Transactional(readOnly = true)
    public PostView get(long id, long viewer) {
        return hydrate(posts.findRows(List.of(id), viewer), viewer).stream()
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Post not found"));
    }

    @Transactional
    void delete(long viewer, long id) {
        PostCore post = existing(id);
        if (post.authorId() != viewer) {
            throw ApiException.forbidden("You can only delete your own posts");
        }
        if (post.replyToId() != null) {
            posts.adjust(post.replyToId(), Counter.REPLIES, -1);
        }
        if (post.repostOfId() != null) {
            posts.adjust(post.repostOfId(), Counter.REPOSTS, -1);
        }
        posts.delete(id);
        outbox.append("post", id, "PostDeleted", Map.of("postId", id, "authorId", viewer));
    }

    /** Liking a repost likes the original, as the original is what the viewer is reacting to. */
    @Transactional
    PostView like(long viewer, long id) {
        PostCore target = original(existing(id));
        requireNotBlocked(viewer, target.authorId(), "You cannot like this post");
        if (posts.like(viewer, target.id())) {
            posts.adjust(target.id(), Counter.LIKES, 1);
            outbox.append("post", target.id(), "PostLiked", Map.of("postId", target.id(), "userId", viewer));
        }
        return get(target.id(), viewer);
    }

    @Transactional
    PostView unlike(long viewer, long id) {
        PostCore target = original(existing(id));
        if (posts.unlike(viewer, target.id())) {
            posts.adjust(target.id(), Counter.LIKES, -1);
            outbox.append("post", target.id(), "PostUnliked", Map.of("postId", target.id(), "userId", viewer));
        }
        return get(target.id(), viewer);
    }

    /** Idempotent: reposting twice returns the existing repost. */
    @Transactional
    PostView repost(long viewer, long id) {
        PostCore target = original(existing(id));
        requireNotBlocked(viewer, target.authorId(), "You cannot repost this post");
        long repostId = posts.findRepostId(viewer, target.id()).orElseGet(() -> {
            long created = posts.insert(viewer, null, null, target.id());
            posts.adjust(target.id(), Counter.REPOSTS, 1);
            outbox.append("post", created, "PostReposted",
                    Map.of("postId", created, "originalId", target.id(), "userId", viewer));
            return created;
        });
        return get(repostId, viewer);
    }

    @Transactional
    PostView unrepost(long viewer, long id) {
        PostCore target = original(existing(id));
        posts.findRepostId(viewer, target.id()).ifPresent(repostId -> {
            posts.delete(repostId);
            posts.adjust(target.id(), Counter.REPOSTS, -1);
            outbox.append("post", repostId, "PostDeleted", Map.of("postId", repostId, "authorId", viewer));
        });
        return get(target.id(), viewer);
    }

    @Transactional(readOnly = true)
    CursorPage<PostView> byAuthor(String username, long viewer, PageParams page) {
        long author = users.findIdByUsername(username).orElseThrow(() -> ApiException.notFound("User not found"));
        List<PostRow> rows = posts.byAuthor(author, viewer, page.cursor(), page.fetchSize());
        return page(rows, viewer, page);
    }

    @Transactional(readOnly = true)
    CursorPage<PostView> replies(long postId, long viewer, PageParams page) {
        PostView parent = get(postId, viewer); // 404s for missing or hidden posts
        List<PostRow> rows = posts.replies(parent.id(), viewer, page.cursor(), page.fetchSize());
        return page(rows, viewer, page);
    }

    /**
     * Renders post ids in the given order with the same visibility rules as every other read,
     * silently skipping posts the viewer may not see. The timeline module renders its candidates with it.
     */
    public List<PostView> render(List<Long> ids, long viewer) {
        Map<Long, PostView> byId = hydrate(posts.findRows(ids, viewer), viewer).stream()
                .collect(Collectors.toMap(PostView::id, Function.identity()));
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    /** Paginates on the raw rows, before hydration, so a dropped repost never shifts the cursor. */
    private CursorPage<PostView> page(List<PostRow> rows, long viewer, PageParams page) {
        CursorPage<PostRow> raw = CursorPage.of(rows, page, PostRow::id);
        return new CursorPage<>(hydrate(raw.items(), viewer), raw.nextCursor());
    }

    /** Attaches originals to reposts; drops reposts whose original the viewer may not see. */
    private List<PostView> hydrate(List<PostRow> rows, long viewer) {
        List<Long> originalIds = rows.stream().map(PostRow::repostOfId).filter(Objects::nonNull).distinct().toList();
        Map<Long, PostView> originals = posts.findRows(originalIds, viewer).stream()
                .collect(Collectors.toMap(PostRow::id, row -> row.toView(null)));
        return rows.stream()
                .filter(row -> row.repostOfId() == null || originals.containsKey(row.repostOfId()))
                .map(row -> row.toView(row.repostOfId() == null ? null : originals.get(row.repostOfId())))
                .toList();
    }

    private PostCore existing(long id) {
        return posts.findCore(id).orElseThrow(() -> ApiException.notFound("Post not found"));
    }

    private PostCore original(PostCore post) {
        return post.repostOfId() == null ? post : existing(post.repostOfId());
    }

    private void requireNotBlocked(long viewer, long author, String message) {
        if (graph.isBlockedEitherWay(viewer, author)) {
            throw ApiException.forbidden(message);
        }
    }
}
