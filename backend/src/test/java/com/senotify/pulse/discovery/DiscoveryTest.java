package com.senotify.pulse.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.senotify.pulse.discovery.TrendService.Trend;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DiscoveryTest {

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
    void ranksHashtagsByDistinctPosts() {
        List<Trend> trends = TrendService.rank(List.of(
                "Loving #Java today #java", "more #java and #spring", "#spring boot", "#rust", "email a#b no"));
        assertThat(trends).extracting(Trend::tag).startsWith("#java", "#spring");
        assertThat(trends.getFirst().posts()).isEqualTo(2); // counted once per post
        assertThat(trends).extracting(Trend::tag).doesNotContain("#b");
    }

    @Test
    void trendsEndpointReflectsRecentPosts() throws Exception {
        User user = api.user("trender");
        for (int i = 0; i < 6; i++) {
            api.post(user, "talking about #zzdiscoverytrend number " + i);
        }
        JsonNode trends = read(mvc.perform(get("/api/discover/trends")).andExpect(status().isOk()));
        assertThat(trends.get(0).path("tag").asText()).isEqualTo("#zzdiscoverytrend");
        assertThat(trends.get(0).path("posts").asLong()).isEqualTo(6);
    }

    @Test
    void suggestsFriendsOfFriendsFirstAndExcludesKnownAccounts() throws Exception {
        User viewer = api.user("viewer");
        User friend = api.user("friend");
        User fof = api.user("fof");
        User blocked = api.user("blocked");
        api.follow(viewer, friend);
        api.follow(friend, fof);
        api.follow(friend, blocked);
        api.block(viewer, blocked);

        List<String> suggested = new ArrayList<>();
        read(mvc.perform(get("/api/discover/users?limit=20").header(HttpHeaders.AUTHORIZATION, bearer(viewer))))
                .forEach(s -> suggested.add(s.path("username").asText()));

        assertThat(suggested.getFirst()).isEqualTo(fof.username());
        assertThat(suggested).doesNotContain(viewer.username(), friend.username(), blocked.username());
        mvc.perform(get("/api/discover/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void searchesPostTextWithPagingAndLiteralWildcards() throws Exception {
        User author = api.user("searcher");
        for (int i = 0; i < 3; i++) {
            api.post(author, "unique zqxsearchterm post " + i);
        }
        api.post(author, "100% literal_match");

        JsonNode first = read(mvc.perform(get("/api/search/posts?q=ZQXSEARCHTERM&limit=2")));
        assertThat(first.path("items")).hasSize(2);
        assertThat(first.path("items").get(0).path("content").asText()).endsWith("post 2");
        JsonNode second = read(mvc.perform(
                get("/api/search/posts?q=zqxsearchterm&limit=2&cursor=" + first.path("nextCursor").asText())));
        assertThat(second.path("items")).hasSize(1);
        assertThat(second.path("nextCursor").isNull()).isTrue();

        assertThat(read(mvc.perform(get("/api/search/posts").param("q", "0% literal_"))).path("items")).hasSize(1);
        assertThat(read(mvc.perform(get("/api/search/posts").param("q", "0%%"))).path("items")).isEmpty();
        mvc.perform(get("/api/search/posts?q=a")).andExpect(status().isBadRequest());
    }

    private JsonNode read(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static String bearer(User user) {
        return "Bearer " + user.token();
    }
}
