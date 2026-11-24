package com.senotify.pulse.social;

import com.senotify.pulse.common.ApiException;
import com.senotify.pulse.common.CursorPage;
import com.senotify.pulse.common.outbox.OutboxWriter;
import com.senotify.pulse.common.PageParams;
import com.senotify.pulse.identity.UserRepository;
import com.senotify.pulse.identity.UserSummary;
import com.senotify.pulse.social.SocialRepository.Edge;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SocialService {

    private final SocialRepository graph;
    private final UserRepository users;
    private final OutboxWriter outbox;

    SocialService(SocialRepository graph, UserRepository users, OutboxWriter outbox) {
        this.graph = graph;
        this.users = users;
        this.outbox = outbox;
    }

    @Transactional
    void follow(long viewer, String username) {
        long target = otherUser(viewer, username);
        if (graph.isBlockedEitherWay(viewer, target)) {
            throw ApiException.forbidden("You cannot follow this user");
        }
        if (graph.add(Edge.FOLLOW, viewer, target)) {
            outbox.append("user", viewer, "UserFollowed", Map.of("followerId", viewer, "followeeId", target));
        }
    }

    @Transactional
    void unfollow(long viewer, String username) {
        long target = otherUser(viewer, username);
        if (graph.remove(Edge.FOLLOW, viewer, target)) {
            outbox.append("user", viewer, "UserUnfollowed", Map.of("followerId", viewer, "followeeId", target));
        }
    }

    /** Blocking also severs follows in both directions, matching common social-network semantics. */
    @Transactional
    void block(long viewer, String username) {
        long target = otherUser(viewer, username);
        graph.remove(Edge.FOLLOW, viewer, target);
        graph.remove(Edge.FOLLOW, target, viewer);
        if (graph.add(Edge.BLOCK, viewer, target)) {
            outbox.append("user", viewer, "UserBlocked", Map.of("blockerId", viewer, "blockedId", target));
        }
    }

    @Transactional
    void unblock(long viewer, String username) {
        long target = otherUser(viewer, username);
        if (graph.remove(Edge.BLOCK, viewer, target)) {
            outbox.append("user", viewer, "UserUnblocked", Map.of("blockerId", viewer, "blockedId", target));
        }
    }

    @Transactional
    void mute(long viewer, String username) {
        long target = otherUser(viewer, username);
        if (graph.add(Edge.MUTE, viewer, target)) {
            outbox.append("user", viewer, "UserMuted", Map.of("muterId", viewer, "mutedId", target));
        }
    }

    @Transactional
    void unmute(long viewer, String username) {
        long target = otherUser(viewer, username);
        if (graph.remove(Edge.MUTE, viewer, target)) {
            outbox.append("user", viewer, "UserUnmuted", Map.of("muterId", viewer, "mutedId", target));
        }
    }

    @Transactional(readOnly = true)
    CursorPage<UserSummary> followers(String username, PageParams page) {
        long user = existingUser(username);
        return CursorPage.of(graph.followers(user, page.cursor(), page.fetchSize()), page, UserSummary::id);
    }

    @Transactional(readOnly = true)
    CursorPage<UserSummary> following(String username, PageParams page) {
        long user = existingUser(username);
        return CursorPage.of(graph.following(user, page.cursor(), page.fetchSize()), page, UserSummary::id);
    }

    private long otherUser(long viewer, String username) {
        long target = existingUser(username);
        if (target == viewer) {
            throw ApiException.badRequest("You cannot do that to yourself");
        }
        return target;
    }

    private long existingUser(String username) {
        return users.findIdByUsername(username).orElseThrow(() -> ApiException.notFound("User not found"));
    }
}
