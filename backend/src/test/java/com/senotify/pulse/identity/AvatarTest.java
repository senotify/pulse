package com.senotify.pulse.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AvatarTest {

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
    void uploadsNormalizesServesAndRemoves() throws Exception {
        User user = api.user("photo");
        long post = api.post(user, "with a face now");

        JsonNode me = json.readTree(mvc.perform(upload(user, png(800, 600, true)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        String key = me.path("avatarKey").asText();
        assertThat(key).matches("[0-9a-f]{16}");

        // Served as a 400x400 JPEG with a long-lived, immutable cache policy.
        byte[] served = mvc.perform(get("/api/avatars/" + user.id() + "?v=" + key))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=31536000, public, immutable"))
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(served));
        assertThat(image.getWidth()).isEqualTo(400);
        assertThat(image.getHeight()).isEqualTo(400);
        // Transparent pixels were flattened onto white, not black.
        assertThat(new Color(image.getRGB(2, 2)).getRed()).isGreaterThan(240);

        // The key reaches every place a user is embedded.
        mvc.perform(get("/api/users/" + user.username())).andExpect(jsonPath("$.avatarKey").value(key));
        mvc.perform(get("/api/posts/" + post)).andExpect(jsonPath("$.author.avatarKey").value(key));

        // A new upload changes the key, so cached URLs never show a stale photo.
        String second = json.readTree(mvc.perform(upload(user, png(300, 300, false)))
                .andReturn().getResponse().getContentAsString()).path("avatarKey").asText();
        assertThat(second).isNotEqualTo(key);

        mvc.perform(delete("/api/me/avatar").header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarKey").doesNotExist());
        mvc.perform(get("/api/avatars/" + user.id())).andExpect(status().isNotFound());
    }

    @Test
    void rejectsNonImagesAndBadDimensions() throws Exception {
        User user = api.user("photo");
        mvc.perform(upload(user, "definitely not an image".getBytes())).andExpect(status().isBadRequest());
        mvc.perform(upload(user, png(6001, 40, false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Image is too large (max 6000 px per side)"));
        mvc.perform(upload(user, png(20, 20, false))).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/me/avatar").file(new MockMultipartFile("file", "a.png", "image/png", png(64, 64, false)))
                        .with(r -> {
                            r.setMethod("PUT");
                            return r;
                        }))
                .andExpect(status().isUnauthorized());
    }

    private static MockMultipartHttpServletRequestBuilder upload(User user, byte[] bytes) {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/me/avatar")
                .file(new MockMultipartFile("file", "photo.png", "image/png", bytes));
        request.with(r -> {
            r.setMethod("PUT");
            return r;
        });
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token());
        return request;
    }

    private static byte[] png(int width, int height, boolean transparentCorner) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(30, 120, 220));
        g.fillRect(0, 0, width, height);
        if (transparentCorner) {
            g.setComposite(java.awt.AlphaComposite.Clear);
            g.fillRect(0, 0, width, height / 3);
        }
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
