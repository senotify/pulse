package com.senotify.pulse.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** A small client over MockMvc for tests that act as several users. */
public class Api {

    private static final AtomicInteger SEQ = new AtomicInteger();

    public record User(long id, String username, String token) {
    }

    private final MockMvc mvc;
    private final ObjectMapper json;

    public Api(MockMvc mvc, ObjectMapper json) {
        this.mvc = mvc;
        this.json = json;
    }

    /** Registers a user with a unique handle; tests share one database. */
    public User user(String prefix) throws Exception {
        String username = prefix + SEQ.incrementAndGet();
        JsonNode body = read(mvc.perform(MockMvcRequestBuilders.post("/api/auth/register")
                        .header("X-Forwarded-For", "198.51.100." + SEQ.get() % 250) // distinct clients, so per-IP limits stay out of the way
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s@example.com","displayName":"%s","password":"password123"}
                                """.formatted(username, username, username)))
                .andExpect(status().isCreated()));
        return new User(body.path("user").path("id").asLong(), username, body.path("accessToken").asText());
    }

    public long post(User user, String content) throws Exception {
        return send(MockMvcRequestBuilders.post("/api/posts"), user, "{\"content\":\"" + content + "\"}").path("id").asLong();
    }

    public long reply(User user, long parentId, String content) throws Exception {
        return send(MockMvcRequestBuilders.post("/api/posts"), user,
                "{\"content\":\"" + content + "\",\"replyToId\":" + parentId + "}").path("id").asLong();
    }

    public long repost(User user, long postId) throws Exception {
        return send(put("/api/posts/" + postId + "/repost"), user, null).path("id").asLong();
    }

    public void like(User user, long postId) throws Exception {
        send(put("/api/posts/" + postId + "/like"), user, null);
    }

    public JsonNode forYou(User user, String cursor, int limit, boolean explain) throws Exception {
        String query = "?limit=" + limit + (cursor == null ? "" : "&cursor=" + cursor) + (explain ? "&explain=true" : "");
        return read(mvc.perform(auth(get("/api/timeline/for-you" + query), user)).andExpect(status().isOk()));
    }

    /** Sends a raw interaction batch and returns the HTTP status. */
    public int interactions(User user, String body) throws Exception {
        return mvc.perform(auth(MockMvcRequestBuilders.post("/api/interactions"), user)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getStatus();
    }

    public void follow(User user, User target) throws Exception {
        edge(put("/api/users/" + target.username() + "/follow"), user);
    }

    public void unfollow(User user, User target) throws Exception {
        edge(delete("/api/users/" + target.username() + "/follow"), user);
    }

    public void mute(User user, User target) throws Exception {
        edge(put("/api/users/" + target.username() + "/mute"), user);
    }

    public void unmute(User user, User target) throws Exception {
        edge(delete("/api/users/" + target.username() + "/mute"), user);
    }

    public void block(User user, User target) throws Exception {
        edge(put("/api/users/" + target.username() + "/block"), user);
    }

    public JsonNode timeline(User user, String cursor, int limit) throws Exception {
        String query = "?limit=" + limit + (cursor == null ? "" : "&cursor=" + cursor);
        return read(mvc.perform(auth(get("/api/timeline/following" + query), user)).andExpect(status().isOk()));
    }

    /** Contents of the first timeline page; a repost shows as {@code RT:<original content>}. */
    public List<String> timelineContents(User user) throws Exception {
        List<String> contents = new ArrayList<>();
        for (JsonNode item : timeline(user, null, 50).path("items")) {
            contents.add(item.hasNonNull("repostOf")
                    ? "RT:" + item.path("repostOf").path("content").asText()
                    : item.path("content").asText());
        }
        return contents;
    }

    private JsonNode send(MockHttpServletRequestBuilder request, User user, String body) throws Exception {
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return read(mvc.perform(auth(request, user)).andExpect(status().is2xxSuccessful()));
    }

    private void edge(MockHttpServletRequestBuilder request, User user) throws Exception {
        mvc.perform(auth(request, user)).andExpect(status().isNoContent());
    }

    private JsonNode read(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, User user) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token());
    }
}
