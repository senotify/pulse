package com.senotify.pulse.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.senotify.pulse.support.Api;
import com.senotify.pulse.support.Api.User;
import com.senotify.pulse.support.InMemoryRateLimiter;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest(properties = {
        "pulse.ratelimit.enabled=true",
        "pulse.ratelimit.login.limit=3",
        "pulse.ratelimit.registration.limit=3",
        "pulse.ratelimit.posts.limit=2",
        "pulse.ratelimit.uploads.limit=1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    InMemoryRateLimiter limiter;

    Api api;

    @BeforeEach
    void setUp() {
        api = new Api(mvc, json);
        limiter.failing = false;
    }

    private int login(String ip, String login) throws Exception {
        return mvc.perform(post("/api/auth/login").header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + login + "\",\"password\":\"wrong-password\"}"))
                .andReturn().getResponse().getStatus();
    }

    private int register(String ip, String name) throws Exception {
        return mvc.perform(post("/api/auth/register").header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"email\":\"%s@example.com\",\"displayName\":\"%s\",\"password\":\"password123\"}"
                                .formatted(name, name, name)))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void loginIsLimitedPerAddressAndAccountWithRetryAfter() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(login("10.1.0.1", "victim_a")).isEqualTo(401);
        }
        mvc.perform(post("/api/auth/login").header("X-Forwarded-For", "10.1.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"VICTIM_A\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.status").value(429));
        // Another address, or another account from the same address, is unaffected.
        assertThat(login("10.1.0.2", "victim_a")).isEqualTo(401);
        assertThat(login("10.1.0.1", "victim_b")).isEqualTo(401);
    }

    @Test
    void registrationIsLimitedPerAddress() throws Exception {
        assertThat(register("10.2.0.1", "regone")).isEqualTo(201);
        assertThat(register("10.2.0.1", "regtwo")).isEqualTo(201);
        assertThat(register("10.2.0.1", "regthree")).isEqualTo(201);
        assertThat(register("10.2.0.1", "regfour")).isEqualTo(429);
        assertThat(register("10.2.0.2", "regfive")).isEqualTo(201);
    }

    @Test
    void postCreationIsLimitedPerUser() throws Exception {
        User alice = api.user("rlalice");
        User bob = api.user("rlbob");
        api.post(alice, "one");
        api.post(alice, "two");
        mvc.perform(authed(post("/api/posts"), alice).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"three\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
        api.post(bob, "bob is fine");
    }

    @Test
    void uploadsAreLimitedPerUser() throws Exception {
        User alice = api.user("rlupload");
        MockMultipartFile file = new MockMultipartFile("file", "x.png", "image/png", new byte[0]);
        // The empty file is rejected after the limiter has counted it: first 400, then 429.
        mvc.perform(authed(MockMvcRequestBuilders.multipart("/api/media").file(file), alice))
                .andExpect(status().isBadRequest());
        mvc.perform(authed(MockMvcRequestBuilders.multipart("/api/media").file(file), alice))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void failsOpenWhenTheLimiterBreaks() throws Exception {
        User alice = api.user("rlopen");
        limiter.failing = true;
        for (int i = 0; i < 5; i++) {
            api.post(alice, "still posting " + i);
        }
        assertThat(login("10.3.0.1", "anyone")).isEqualTo(401);
    }

    private static MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder, User user) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token());
    }
}
