package com.senotify.pulse.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.senotify.pulse.support.Api;
import com.senotify.pulse.support.Api.User;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MediaPostTest {

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
    void postsPhotosWithOptionalTextAndServesThem() throws Exception {
        User user = api.user("snapper");
        JsonNode uploaded = read(upload(user, png(2000, 1000)).andExpect(status().isCreated()));
        assertThat(uploaded.path("width").asInt()).isEqualTo(1600); // longest side capped, aspect kept
        assertThat(uploaded.path("height").asInt()).isEqualTo(800);
        long mediaId = uploaded.path("id").asLong();

        JsonNode created = read(createPost(user, "", List.of(mediaId)).andExpect(status().isCreated()));
        assertThat(created.path("media")).hasSize(1);
        assertThat(created.path("media").get(0).path("id").asLong()).isEqualTo(mediaId);

        mvc.perform(get("/api/media/" + mediaId))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"));

        // A claimed upload cannot be attached again.
        createPost(user, "again", List.of(mediaId)).andExpect(status().isBadRequest());

        // Deleting the post deletes its photos.
        mvc.perform(delete("/api/posts/" + created.path("id").asLong()).header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/media/" + mediaId)).andExpect(status().isNotFound());
    }

    @Test
    void rejectsBlankPostsForeignUploadsAndTooManyPhotos() throws Exception {
        User owner = api.user("owner");
        User thief = api.user("thief");
        long mine = read(upload(owner, png(100, 100))).path("id").asLong();

        createPost(owner, "  ", List.of()).andExpect(status().isBadRequest());
        createPost(thief, "stolen", List.of(mine)).andExpect(status().isBadRequest());
        // The failed post rolled back entirely.
        mvc.perform(get("/api/users/" + thief.username() + "/posts?filter=replies"))
                .andExpect(jsonPath("$.items").isEmpty());

        List<Long> five = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            five.add(read(upload(owner, png(64, 64))).path("id").asLong());
        }
        createPost(owner, "too many", five).andExpect(status().isBadRequest());
        // The owner can still use the original upload.
        createPost(owner, "mine", List.of(mine)).andExpect(status().isCreated());
    }

    @Test
    void profileTabsFilterPostsRepliesAndMedia() throws Exception {
        User author = api.user("tabs");
        User other = api.user("other");
        long plain = api.post(author, "plain post");
        long photo = read(createPost(author, "with photo", List.of(read(upload(author, png(80, 80))).path("id").asLong())))
                .path("id").asLong();
        long reply = api.reply(author, plain, "a reply");
        long othersPhoto = read(createPost(other, "", List.of(read(upload(other, png(80, 80))).path("id").asLong())))
                .path("id").asLong();
        long repost = api.repost(author, othersPhoto);

        assertThat(ids(author, "posts")).containsExactly(repost, photo, plain);
        assertThat(ids(author, "replies")).containsExactly(repost, reply, photo, plain);
        assertThat(ids(author, "media")).containsExactly(photo); // own photos only, not reposts
        mvc.perform(get("/api/users/" + author.username() + "/posts?filter=likes")).andExpect(status().isBadRequest());
    }

    private List<Long> ids(User user, String filter) throws Exception {
        List<Long> ids = new ArrayList<>();
        read(mvc.perform(get("/api/users/" + user.username() + "/posts?filter=" + filter)))
                .path("items").forEach(item -> ids.add(item.path("id").asLong()));
        return ids;
    }

    private ResultActions upload(User user, byte[] png) throws Exception {
        return mvc.perform(multipart("/api/media")
                .file(new MockMultipartFile("file", "p.png", "image/png", png))
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private ResultActions createPost(User user, String content, List<Long> mediaIds) throws Exception {
        return mvc.perform(post("/api/posts")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("content", content, "mediaIds", mediaIds))));
    }

    private JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static String bearer(User user) {
        return "Bearer " + user.token();
    }

    private static byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(200, 80, 40));
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
