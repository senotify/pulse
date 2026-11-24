package com.senotify.pulse.timeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.senotify.pulse.common.outbox.OutboxRelay;
import com.senotify.pulse.support.Api;
import com.senotify.pulse.support.Api.User;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TimelineTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    OutboxRelay relay;

    @Autowired
    TimelineStore store;

    Api api;

    @BeforeEach
    void setUp() {
        api = new Api(mvc, json);
    }

    @AfterEach
    void restoreStore() {
        ((InMemoryTimelineStore) store).failing = false;
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/timeline/following")).andExpect(status().isUnauthorized());
    }

    @Test
    void fansOutPostsAndBackfillsOnFollow() throws Exception {
        User reader = api.user("reader");
        User author = api.user("author");
        api.post(author, "before follow");

        assertThat(api.timelineContents(reader)).isEmpty();
        assertThat(store.exists(reader.id())).isTrue();

        api.follow(reader, author);
        relay.drain();
        assertThat(api.timelineContents(reader)).containsExactly("before follow");

        long latest = api.post(author, "after follow");
        api.reply(author, latest, "a reply");
        api.post(reader, "my own");
        relay.drain();

        assertThat(api.timelineContents(reader)).containsExactly("my own", "after follow", "before follow");
        assertThat(store.page(reader.id(), null, 10)).hasSize(3);
    }

    @Test
    void unfollowRemovesAndMuteHides() throws Exception {
        User reader = api.user("reader");
        User loud = api.user("loud");
        User other = api.user("other");
        api.follow(reader, loud);
        api.follow(reader, other);
        api.timelineContents(reader);
        api.post(loud, "loud post");
        api.post(other, "other post");
        relay.drain();
        assertThat(api.timelineContents(reader)).containsExactly("other post", "loud post");

        api.mute(reader, loud);
        assertThat(api.timelineContents(reader)).containsExactly("other post");
        api.unmute(reader, loud);
        assertThat(api.timelineContents(reader)).containsExactly("other post", "loud post");

        api.unfollow(reader, other);
        relay.drain();
        assertThat(api.timelineContents(reader)).containsExactly("loud post");
    }

    @Test
    void showsEachPostOnceAndMutesApplyToReposts() throws Exception {
        User reader = api.user("reader");
        User writer = api.user("writer");
        User booster = api.user("booster");
        api.follow(reader, writer);
        api.follow(reader, booster);
        api.timelineContents(reader);

        long original = api.post(writer, "worth sharing");
        relay.drain();
        api.repost(booster, original);
        relay.drain();

        assertThat(api.timelineContents(reader)).containsExactly("RT:worth sharing");

        api.mute(reader, writer);
        assertThat(api.timelineContents(reader)).isEmpty();
    }

    @Test
    void blockingHidesAndPurgesEntries() throws Exception {
        User reader = api.user("reader");
        User author = api.user("author");
        api.follow(reader, author);
        api.timelineContents(reader);
        api.post(author, "soon hidden");
        relay.drain();

        api.block(author, reader);
        relay.drain();
        assertThat(api.timelineContents(reader)).isEmpty();
        assertThat(store.page(reader.id(), null, 10)).isEmpty();
    }

    @Test
    void rebuildsAfterEvictionAndSurvivesStoreOutage() throws Exception {
        User reader = api.user("reader");
        User author = api.user("author");
        api.follow(reader, author);
        api.post(author, "first");
        api.post(author, "second");
        relay.drain();

        store.evict(reader.id());
        assertThat(api.timelineContents(reader)).containsExactly("second", "first");

        ((InMemoryTimelineStore) store).failing = true;
        assertThat(api.timelineContents(reader)).containsExactly("second", "first");
    }

    @Test
    void mergesHighReachAuthorsAtReadTime() throws Exception {
        User star = api.user("star");
        User fan = api.user("fan");
        api.follow(fan, star);
        api.follow(api.user("fan"), star);
        api.follow(api.user("fan"), star); // 3 followers reaches the test threshold
        api.timelineContents(fan);

        long post = api.post(star, "to the masses");
        relay.drain();

        assertThat(store.page(fan.id(), null, 10)).extracting(TimelineEntry::postId).doesNotContain(post);
        assertThat(api.timelineContents(fan)).containsExactly("to the masses");
        // The star's own timeline still gets the post.
        assertThat(api.timelineContents(star)).containsExactly("to the masses");
    }

    @Test
    void paginatesWithoutGapsOrDuplicates() throws Exception {
        User reader = api.user("reader");
        User author = api.user("author");
        api.follow(reader, author);
        for (int i = 1; i <= 5; i++) {
            api.post(author, "post " + i);
        }
        relay.drain();

        List<String> seen = new ArrayList<>();
        List<Integer> pageSizes = new ArrayList<>();
        String cursor = null;
        do {
            JsonNode page = api.timeline(reader, cursor, 2);
            page.path("items").forEach(item -> seen.add(item.path("content").asText()));
            pageSizes.add(page.path("items").size());
            cursor = page.path("nextCursor").isNull() ? null : page.path("nextCursor").asText();
        } while (cursor != null);

        assertThat(seen).containsExactly("post 5", "post 4", "post 3", "post 2", "post 1");
        assertThat(pageSizes).containsExactly(2, 2, 1);
    }
}
