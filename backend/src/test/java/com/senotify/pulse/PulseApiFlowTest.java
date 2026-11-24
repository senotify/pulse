package com.senotify.pulse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class PulseApiFlowTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcClient jdbc;

    @Test
    void identityRules() throws Exception {
        register("carol");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"CAROL","email":"other@example.com","displayName":"C","password":"password123"}
                        """))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"no spaces","email":"x@example.com","displayName":"X","password":"short"}
                        """))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"login":"carol","password":"wrong-password"}
                        """))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"login":"carol@example.com","password":"password123"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("carol"));

        mvc.perform(post("/api/posts").contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"hi\"}"))
                .andExpect(status().isUnauthorized());

        String token = login("carol");
        mvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/me"), token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"bio\":\"hello world\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value("hello world"))
                .andExpect(jsonPath("$.displayName").value("carol display"));
    }

    @Test
    void socialAndPostFlow() throws Exception {
        String alice = register("alice");
        String bob = register("bob");

        long postId = id(mvc.perform(auth(post("/api/posts"), alice)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"  hello pulse  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("hello pulse"))
                .andExpect(jsonPath("$.author.username").value("alice"))
                .andReturn().getResponse().getContentAsString());

        // Follow is idempotent.
        mvc.perform(auth(put("/api/users/alice/follow"), bob)).andExpect(status().isNoContent());
        mvc.perform(auth(put("/api/users/alice/follow"), bob)).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/users/alice"), bob))
                .andExpect(jsonPath("$.followers").value(1))
                .andExpect(jsonPath("$.followedByMe").value(true));
        mvc.perform(get("/api/users/alice/followers"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].username").value("bob"));
        mvc.perform(auth(put("/api/users/bob/follow"), bob)).andExpect(status().isBadRequest());

        // Likes are idempotent and counted once.
        mvc.perform(auth(put("/api/posts/" + postId + "/like"), bob)).andExpect(status().isOk());
        mvc.perform(auth(put("/api/posts/" + postId + "/like"), bob))
                .andExpect(jsonPath("$.likeCount").value(1))
                .andExpect(jsonPath("$.likedByMe").value(true));

        // A repost embeds the original; liking the repost likes the original.
        long repostId = id(mvc.perform(auth(put("/api/posts/" + postId + "/repost"), bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").doesNotExist())
                .andExpect(jsonPath("$.repostOf.id").value(postId))
                .andReturn().getResponse().getContentAsString());
        mvc.perform(auth(put("/api/posts/" + repostId + "/repost"), bob))
                .andExpect(jsonPath("$.id").value(repostId));
        mvc.perform(get("/api/posts/" + postId)).andExpect(jsonPath("$.repostCount").value(1));
        mvc.perform(get("/api/users/bob/posts"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].repostOf.content").value("hello pulse"));

        // Replies.
        mvc.perform(auth(post("/api/posts"), bob).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"nice\",\"replyToId\":" + postId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.replyToId").value(postId));
        mvc.perform(get("/api/posts/" + postId))
                .andExpect(jsonPath("$.replyCount").value(1));
        mvc.perform(get("/api/posts/" + postId + "/replies"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].content").value("nice"));

        // Only the author may delete.
        mvc.perform(auth(delete("/api/posts/" + postId), bob)).andExpect(status().isForbidden());

        // Blocking severs the follow and hides content both ways.
        mvc.perform(auth(put("/api/users/bob/block"), alice)).andExpect(status().isNoContent());
        mvc.perform(get("/api/users/alice")).andExpect(jsonPath("$.followers").value(0));
        mvc.perform(auth(get("/api/posts/" + postId), bob)).andExpect(status().isNotFound());
        mvc.perform(auth(put("/api/users/alice/follow"), bob)).andExpect(status().isForbidden());
        mvc.perform(auth(put("/api/posts/" + postId + "/like"), bob)).andExpect(status().isForbidden());
        mvc.perform(get("/api/posts/" + postId)).andExpect(status().isOk());

        // Unblock, then the author deletes the post; the repost goes with it.
        mvc.perform(auth(delete("/api/users/bob/block"), alice)).andExpect(status().isNoContent());
        mvc.perform(auth(delete("/api/posts/" + postId), alice)).andExpect(status().isNoContent());
        mvc.perform(get("/api/posts/" + postId)).andExpect(status().isNotFound());
        mvc.perform(get("/api/posts/" + repostId)).andExpect(status().isNotFound());

        long events = jdbc.sql("SELECT COUNT(*) FROM outbox_events WHERE event_type IN "
                        + "('PostCreated','UserFollowed','PostLiked','PostReposted','UserBlocked','PostDeleted')")
                .query(Long.class).single();
        assertThat(events).isGreaterThanOrEqualTo(7);
    }

    @Test
    void pagination() throws Exception {
        String dave = register("dave");
        for (int i = 0; i < 5; i++) {
            mvc.perform(auth(post("/api/posts"), dave).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"content\":\"post " + i + "\"}")).andExpect(status().isCreated());
        }
        JsonNode first = json.readTree(mvc.perform(get("/api/users/dave/posts?limit=3"))
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.items[0].content").value("post 4"))
                .andReturn().getResponse().getContentAsString());
        String cursor = first.get("nextCursor").asText();
        mvc.perform(get("/api/users/dave/posts?limit=3&cursor=" + cursor))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[1].content").value("post 0"))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void threadsExposeParentAuthorAndAncestors() throws Exception {
        String erin = register("erin");
        String frank = register("frank");
        long root = id(mvc.perform(auth(post("/api/posts"), erin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"root\"}")).andReturn().getResponse().getContentAsString());
        long middle = id(mvc.perform(auth(post("/api/posts"), frank).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"middle\",\"replyToId\":" + root + "}"))
                .andExpect(jsonPath("$.replyTo.username").value("erin"))
                .andReturn().getResponse().getContentAsString());
        long leaf = id(mvc.perform(auth(post("/api/posts"), erin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"leaf\",\"replyToId\":" + middle + "}"))
                .andExpect(jsonPath("$.replyTo.username").value("frank"))
                .andReturn().getResponse().getContentAsString());

        mvc.perform(get("/api/posts/" + root)).andExpect(jsonPath("$.replyTo").doesNotExist());
        mvc.perform(get("/api/posts/" + leaf + "/ancestors"))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].content").value("root"))
                .andExpect(jsonPath("$[1].content").value("middle"));
        mvc.perform(get("/api/posts/" + root + "/ancestors")).andExpect(jsonPath("$", hasSize(0)));

        // A blocked participant drops out of the chain for the blocker.
        mvc.perform(auth(put("/api/users/frank/block"), erin)).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/posts/" + leaf + "/ancestors"), erin))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].content").value("root"));
        mvc.perform(get("/api/posts/999999/ancestors")).andExpect(status().isNotFound());
    }

    private String register(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"%s","email":"%s@example.com","displayName":"%s display","password":"password123"}
                        """.formatted(username, username, username)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    private String login(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + username + "\",\"password\":\"password123\"}"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    private long id(String body) throws Exception {
        return json.readTree(body).get("id").asLong();
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
