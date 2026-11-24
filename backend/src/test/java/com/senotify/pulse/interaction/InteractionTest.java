package com.senotify.pulse.interaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.senotify.pulse.common.outbox.OutboxRelay;
import com.senotify.pulse.support.Api;
import com.senotify.pulse.support.Api.User;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InteractionTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    OutboxRelay relay;

    Api api;

    @BeforeEach
    void setUp() {
        api = new Api(mvc, json);
    }

    @Test
    void validatesClientBatches() throws Exception {
        User user = api.user("client");
        assertThat(api.interactions(user, """
                {"events":[{"postId":1,"type":"CLICK"},{"postId":1,"type":"DWELL","dwellMs":4200}]}
                """)).isEqualTo(202);
        assertThat(count(user.id(), "DWELL")).isEqualTo(1);

        assertThat(api.interactions(user, "{\"events\":[]}")).isEqualTo(400);
        assertThat(api.interactions(user, "{\"events\":[{\"postId\":1,\"type\":\"LIKE\"}]}")).isEqualTo(400);
        assertThat(api.interactions(user, "{\"events\":[{\"type\":\"CLICK\"}]}")).isEqualTo(400);
        assertThat(api.interactions(user, "{\"events\":[{\"postId\":1,\"type\":\"DWELL\",\"dwellMs\":-1}]}"))
                .isEqualTo(400);
        String tooMany = IntStream.range(0, 101).mapToObj(i -> "{\"postId\":1,\"type\":\"CLICK\"}")
                .collect(Collectors.joining(",", "{\"events\":[", "]}"));
        assertThat(api.interactions(user, tooMany)).isEqualTo(400);
    }

    @Test
    void recordsServerEngagementExactlyOnce() throws Exception {
        User author = api.user("author");
        User fan = api.user("fan");
        long post = api.post(author, "engage with me");
        api.like(fan, post);
        api.repost(fan, post);
        api.reply(fan, post, "nice");
        relay.drain();

        assertThat(count(fan.id(), "LIKE")).isEqualTo(1);
        assertThat(count(fan.id(), "REPOST")).isEqualTo(1);
        assertThat(count(fan.id(), "REPLY")).isEqualTo(1);

        // Simulate redelivery of every event: the log must not grow.
        jdbc.sql("UPDATE outbox_events SET published_at = NULL WHERE event_type IN ('PostLiked', 'PostReposted')")
                .update();
        relay.drain();
        assertThat(count(fan.id(), "LIKE")).isEqualTo(1);
        assertThat(count(fan.id(), "REPOST")).isEqualTo(1);
    }

    private long count(long userId, String type) {
        return jdbc.sql("SELECT COUNT(*) FROM interaction_events WHERE user_id = :u AND type = :t")
                .param("u", userId)
                .param("t", type)
                .query(Long.class)
                .single();
    }
}
