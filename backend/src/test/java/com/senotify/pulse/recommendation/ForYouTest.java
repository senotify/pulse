package com.senotify.pulse.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.senotify.pulse.support.Api;
import com.senotify.pulse.support.Api.User;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end For You behaviour. Tests share one database with other test classes, so assertions are
 * about the posts each test creates, never about the whole feed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ForYouTest {

    private static final int LIMIT = 50;

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    Api api;

    @BeforeEach
    void setUp() {
        api = new Api(mvc, json);
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/timeline/for-you")).andExpect(status().isUnauthorized());
    }

    @Test
    void coldStartUserGetsTrendingPosts() throws Exception {
        User newcomer = api.user("newcomer");
        User creator = api.user("creator");
        long popular = api.post(creator, "a popular post");
        for (int i = 0; i < 3; i++) {
            api.like(api.user("fan"), popular);
        }

        Map<Long, JsonNode> feed = items(api.forYou(newcomer, null, LIMIT, false));
        assertThat(feed).containsKey(popular);
        assertThat(feed.get(popular).path("source").asText()).isEqualTo("TRENDING");
        assertThat(feed.get(popular).path("reason").asText()).isEqualTo("Popular right now");
    }

    @Test
    void retrievesFromEverySourceWithReasons() throws Exception {
        User viewer = api.user("viewer");
        User friend = api.user("friend");
        User friendOfFriend = api.user("fof");
        User stranger = api.user("stranger");
        api.follow(viewer, friend);
        api.follow(friend, friendOfFriend);

        long inNetwork = api.post(friend, "from a friend");
        long secondDegree = api.post(friendOfFriend, "second degree");
        long engaged = api.post(stranger, "liked by a friend");
        api.like(friend, engaged);
        long own = api.post(viewer, "my own post");
        long reply = api.reply(friend, inNetwork, "a friend reply");

        Map<Long, JsonNode> feed = items(api.forYou(viewer, null, LIMIT, false));

        assertThat(feed.get(inNetwork).path("source").asText()).isEqualTo("IN_NETWORK");
        assertThat(feed.get(secondDegree).path("source").asText()).isEqualTo("FOLLOWED_BY_FOLLOWING");
        assertThat(feed.get(engaged).path("source").asText()).isEqualTo("ENGAGED_BY_FOLLOWING");
        assertThat(feed.get(engaged).path("reason").asText()).isEqualTo("Liked or reposted by @" + friend.username());
        assertThat(feed).doesNotContainKeys(own, reply);
    }

    @Test
    void excludesMutedBlockedAndSeenPosts() throws Exception {
        User viewer = api.user("viewer");
        User muted = api.user("muted");
        User blocker = api.user("blocker");
        User friend = api.user("friend");
        api.follow(viewer, muted);
        api.follow(viewer, blocker);
        api.follow(viewer, friend);
        long mutedPost = api.post(muted, "muted post");
        long blockerPost = api.post(blocker, "blocker post");
        long seenPost = api.post(friend, "already seen");
        long freshPost = api.post(friend, "still fresh");

        assertThat(items(api.forYou(viewer, null, LIMIT, false)))
                .containsKeys(mutedPost, blockerPost, seenPost, freshPost);

        api.mute(viewer, muted);
        api.block(blocker, viewer);
        assertThat(api.interactions(viewer, """
                {"events":[{"postId":%d,"type":"IMPRESSION","surface":"FOR_YOU","position":0,
                            "modelVersion":"weighted-v1"}]}
                """.formatted(seenPost))).isEqualTo(202);

        Map<Long, JsonNode> feed = items(api.forYou(viewer, null, LIMIT, false));
        assertThat(feed).containsKey(freshPost).doesNotContainKeys(mutedPost, blockerPost, seenPost);
    }

    @Test
    void diversifiesAuthors() throws Exception {
        User viewer = api.user("viewer");
        User prolific = api.user("prolific");
        User quiet = api.user("quiet");
        api.follow(viewer, prolific);
        api.follow(viewer, quiet);
        long quietPost = api.post(quiet, "the only one");
        for (int i = 0; i < 4; i++) {
            api.post(prolific, "prolific " + i);
        }

        List<Long> inNetworkOrder = new ArrayList<>();
        for (JsonNode item : api.forYou(viewer, null, LIMIT, false).path("items")) {
            if (item.path("source").asText().equals("IN_NETWORK")) {
                inNetworkOrder.add(item.path("post").path("id").asLong());
            }
        }
        assertThat(inNetworkOrder).hasSize(5);
        assertThat(inNetworkOrder.indexOf(quietPost)).isLessThanOrEqualTo(1);
    }

    @Test
    void pagesFromAStableSnapshot() throws Exception {
        User viewer = api.user("viewer");
        User author = api.user("author");
        api.follow(viewer, author);
        for (int i = 0; i < 4; i++) {
            api.post(author, "paged " + i);
        }

        JsonNode first = api.forYou(viewer, null, 2, false);
        String cursor = first.path("nextCursor").asText();
        assertThat(cursor).matches("[0-9a-f]{16}\\.2");
        assertThat(first.path("modelVersion").asText()).isEqualTo("weighted-v1");

        long postedLater = api.post(author, "posted after page one");
        JsonNode second = api.forYou(viewer, cursor, 2, false);

        assertThat(items(second)).isNotEmpty().doesNotContainKey(postedLater);
        assertThat(items(second).keySet()).doesNotContainAnyElementsOf(items(first).keySet());
        assertThat(items(api.forYou(viewer, null, LIMIT, false))).containsKey(postedLater);
    }

    @Test
    void explainsScoresOnRequest() throws Exception {
        User viewer = api.user("viewer");
        User friend = api.user("friend");
        api.follow(viewer, friend);
        long post = api.post(friend, "explain me");

        assertThat(items(api.forYou(viewer, null, LIMIT, false)).get(post).has("features")).isFalse();

        JsonNode explained = items(api.forYou(viewer, null, LIMIT, true)).get(post);
        assertThat(explained.path("features").path("inNetwork").asDouble()).isEqualTo(1.0);
        assertThat(explained.path("features").path("recency").asDouble()).isGreaterThan(0.9);
        assertThat(explained.path("score").asDouble()).isPositive();
    }

    @Test
    void rejectsMalformedCursor() throws Exception {
        User viewer = api.user("viewer");
        mvc.perform(get("/api/timeline/for-you?cursor=nope")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewer.token()))
                .andExpect(status().isBadRequest());
    }

    private static Map<Long, JsonNode> items(JsonNode page) {
        Map<Long, JsonNode> byId = new HashMap<>();
        page.path("items").forEach(item -> byId.put(item.path("post").path("id").asLong(), item));
        return byId;
    }
}
