package com.senotify.pulse.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class LikesTabTest {

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
    void listsLikesByLikeTimeNotPostTimeAndPaginates() throws Exception {
        User author = api.user("author");
        User fan = api.user("fan");
        long a = api.post(author, "first");
        long b = api.post(author, "second");
        long c = api.post(author, "third");
        // Liked in a different order than the posts were written.
        api.like(fan, b);
        api.like(fan, c);
        api.like(fan, a);

        assertThat(ids(fan, fan, "")).containsExactly(a, c, b);

        JsonNode page1 = page(fan, null, "?limit=2");
        assertThat(page1.path("items")).hasSize(2);
        assertThat(page1.path("nextCursor").isNull()).isFalse();
        JsonNode page2 = page(fan, null, "?limit=2&cursor=" + page1.path("nextCursor").asText());
        assertThat(page2.path("items")).hasSize(1);
        assertThat(page2.path("items").get(0).path("id").asLong()).isEqualTo(b);
        assertThat(page2.path("nextCursor").isNull()).isTrue();

        // Unliking removes it; a user with no likes gets an empty page.
        assertThat(ids(author, null, "")).isEmpty();
        mvc.perform(get("/api/users/" + fan.username() + "/likes?cursor=garbage")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/users/nobody-here/likes")).andExpect(status().isNotFound());
    }

    @Test
    void hidesLikedPostsOfBlockedAuthorsAndShowsLikeStateToTheViewer() throws Exception {
        User author = api.user("writer");
        User blockedAuthor = api.user("blocked");
        User fan = api.user("liker");
        User viewer = api.user("viewer");
        long ok = api.post(author, "fine");
        long hidden = api.post(blockedAuthor, "hidden from viewer");
        api.like(fan, ok);
        api.like(fan, hidden);
        api.like(viewer, ok);
        api.block(viewer, blockedAuthor);

        assertThat(ids(fan, fan, "")).containsExactly(hidden, ok);
        assertThat(ids(fan, viewer, "")).containsExactly(ok);
        assertThat(ids(fan, null, "")).containsExactly(hidden, ok); // anonymous reads are allowed

        JsonNode item = page(fan, viewer, "").path("items").get(0);
        assertThat(item.path("likedByMe").asBoolean()).isTrue();
        JsonNode seenByOther = page(fan, author, "").path("items").get(0);
        assertThat(seenByOther.path("likedByMe").asBoolean()).isFalse();
    }

    private List<Long> ids(User owner, User viewer, String query) throws Exception {
        List<Long> ids = new ArrayList<>();
        page(owner, viewer, query).path("items").forEach(i -> ids.add(i.path("id").asLong()));
        return ids;
    }

    private JsonNode page(User owner, User viewer, String query) throws Exception {
        var request = get("/api/users/" + owner.username() + "/likes" + query);
        if (viewer != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + viewer.token());
        }
        return json.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}
