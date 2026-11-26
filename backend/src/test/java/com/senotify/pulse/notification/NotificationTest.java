package com.senotify.pulse.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.senotify.pulse.common.outbox.OutboxRelay;
import com.senotify.pulse.support.Api;
import com.senotify.pulse.support.Api.User;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    OutboxRelay relay;

    @Autowired
    JdbcClient jdbc;

    Api api;

    @BeforeEach
    void setUp() {
        api = new Api(mvc, json);
    }

    @Test
    void notifiesAboutRepostsRepliesFollowsAndMentionsButNeverAboutYourself() throws Exception {
        User alice = api.user("alice");
        User bob = api.user("bob");
        User carol = api.user("carol");
        long post = api.post(alice, "hello world");
        api.follow(bob, alice);
        api.repost(bob, post);
        api.reply(carol, post, "nice one @" + alice.username() + " and @" + bob.username());
        api.reply(alice, post, "talking to myself @" + alice.username());
        api.post(carol, "mentioning @" + bob.username() + " and the unknown @nobody_here_x");
        relay.drain();

        List<JsonNode> mine = list(alice, "");
        assertThat(types(mine)).containsExactly("REPLY", "REPOST", "FOLLOW"); // newest first, no MENTION duplicate
        assertThat(mine.get(0).path("actor").path("username").asText()).isEqualTo(carol.username());
        assertThat(mine.get(0).path("postPreview").asText()).startsWith("nice one");
        assertThat(mine.get(1).path("postId").asLong()).isEqualTo(post);
        assertThat(mine.get(1).path("postPreview").asText()).isEqualTo("hello world");
        assertThat(mine.get(2).path("postId").isNull()).isTrue();

        // Bob is mentioned twice (in the reply and in the standalone post) and nothing else.
        assertThat(types(list(bob, ""))).containsExactly("MENTION", "MENTION");
        assertThat(list(carol, "")).isEmpty();
    }

    @Test
    void groupsLikesPerPostAndBumpsTheGroupBackToUnread() throws Exception {
        User author = api.user("author");
        User one = api.user("fanone");
        User two = api.user("fantwo");
        long a = api.post(author, "first");
        long b = api.post(author, "second");
        api.like(one, a);
        api.like(author, a); // own likes never count
        api.like(one, b);
        relay.drain();
        assertThat(types(list(author, ""))).containsExactly("LIKE", "LIKE");
        assertThat(count(author)).isEqualTo(2);

        api.like(two, a);
        relay.drain();
        List<JsonNode> items = list(author, "");
        assertThat(items).hasSize(2);
        assertThat(items.get(0).path("postId").asLong()).isEqualTo(a); // moved to the top
        assertThat(items.get(0).path("actorCount").asInt()).isEqualTo(2);
        assertThat(items.get(0).path("actor").path("username").asText()).isEqualTo(two.username());

        markRead(author, null);
        assertThat(count(author)).isZero();
        api.like(api.user("fanthree"), a);
        relay.drain();
        assertThat(count(author)).isEqualTo(1);
        assertThat(list(author, "").get(0).path("actorCount").asInt()).isEqualTo(3);
    }

    @Test
    void isIdempotentWhenEventsAreRedelivered() throws Exception {
        User author = api.user("idem");
        User fan = api.user("idemfan");
        long post = api.post(author, "once");
        api.follow(fan, author);
        api.like(fan, post);
        api.reply(fan, post, "reply");
        relay.drain();
        jdbc.sql("UPDATE outbox_events SET published_at = NULL").update();
        relay.drain();
        assertThat(types(list(author, ""))).containsExactly("REPLY", "LIKE", "FOLLOW");
    }

    @Test
    void paginatesMarksSelectedAsReadAndHidesBlockedActors() throws Exception {
        User owner = api.user("owner");
        User blocked = api.user("pest");
        List<User> fans = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            User fan = api.user("follower");
            api.follow(fan, owner);
            fans.add(fan);
        }
        api.follow(blocked, owner);
        relay.drain();
        assertThat(count(owner)).isEqualTo(4);

        api.block(owner, blocked);
        assertThat(count(owner)).isEqualTo(3);
        assertThat(list(owner, "").stream().map(n -> n.path("actor").path("username").asText()))
                .doesNotContain(blocked.username());

        JsonNode page1 = page(owner, "?limit=2");
        assertThat(page1.path("items")).hasSize(2);
        JsonNode page2 = page(owner, "?limit=2&cursor=" + page1.path("nextCursor").asText());
        assertThat(page2.path("items")).hasSize(1);
        assertThat(page2.path("nextCursor").isNull()).isTrue();

        markRead(owner, List.of(page1.path("items").get(0).path("id").asLong()));
        assertThat(count(owner)).isEqualTo(2);
        assertThat(page1.path("items").get(0).path("read").asBoolean()).isFalse();
        assertThat(list(owner, "").get(0).path("read").asBoolean()).isTrue();
        markRead(owner, List.of()); // an empty list marks nothing
        assertThat(count(owner)).isEqualTo(2);
        markRead(owner, null);
        assertThat(count(owner)).isZero();
    }

    @Test
    void requiresAuthenticationAndRejectsBadCursors() throws Exception {
        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/notifications/unread-count")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/notifications/read")).andExpect(status().isUnauthorized());
        User user = api.user("curious");
        mvc.perform(auth(get("/api/notifications?cursor=junk"), user)).andExpect(status().isBadRequest());
    }

    private long count(User user) throws Exception {
        return read(mvc.perform(auth(get("/api/notifications/unread-count"), user)).andExpect(status().isOk()))
                .path("count").asLong();
    }

    private void markRead(User user, List<Long> ids) throws Exception {
        var request = post("/api/notifications/read");
        if (ids != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of("ids", ids)));
        }
        mvc.perform(auth(request, user)).andExpect(status().isNoContent());
    }

    private List<JsonNode> list(User user, String query) throws Exception {
        List<JsonNode> items = new ArrayList<>();
        page(user, query).path("items").forEach(items::add);
        return items;
    }

    private JsonNode page(User user, String query) throws Exception {
        return read(mvc.perform(auth(get("/api/notifications" + query), user)).andExpect(status().isOk()));
    }

    private static List<String> types(List<JsonNode> items) {
        return items.stream().map(n -> n.path("type").asText()).toList();
    }

    private JsonNode read(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, User user) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token());
    }
}
