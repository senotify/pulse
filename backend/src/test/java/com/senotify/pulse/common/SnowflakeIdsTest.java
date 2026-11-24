package com.senotify.pulse.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SnowflakeIdsTest {

    private static final long MAX_SAFE_JS_INTEGER = (1L << 53) - 1;

    @Test
    void idsIncreaseAndStayWithinJavaScriptSafeIntegers() {
        SnowflakeIds ids = new SnowflakeIds(0);
        long previous = 0;
        for (int i = 0; i < 10_000; i++) {
            long id = ids.next();
            assertThat(id).isGreaterThan(previous);
            previous = id;
        }
        // Even at the end of the 41-bit timestamp range the id fits in 53 bits.
        long last = ((1L << 41) - 1) << 12 | (3L << 10) | 1023;
        assertThat(last).isLessThanOrEqualTo(MAX_SAFE_JS_INTEGER);
    }

    @Test
    void encodesTheCreationTime() {
        Instant now = Instant.parse("2026-11-24T10:00:00.123Z");
        SnowflakeIds ids = new SnowflakeIds(2, Clock.fixed(now, ZoneOffset.UTC));
        assertThat(SnowflakeIds.timestampOf(ids.next())).isEqualTo(now);
    }

    @Test
    void neverGoesBackwardsWhenTheClockDoes() {
        AtomicLong millis = new AtomicLong(Instant.parse("2026-06-01T00:00:00Z").toEpochMilli());
        SnowflakeIds ids = new SnowflakeIds(0, clock(millis));
        long before = ids.next();
        millis.addAndGet(-5_000); // NTP correction moves the clock back five seconds
        assertThat(ids.next()).isGreaterThan(before);
    }

    @Test
    void borrowsTheNextMillisecondWhenTheSequenceIsExhausted() {
        AtomicLong millis = new AtomicLong(Instant.parse("2026-06-01T00:00:00Z").toEpochMilli());
        SnowflakeIds ids = new SnowflakeIds(1, clock(millis)); // frozen clock
        List<Long> generated = new ArrayList<>();
        for (int i = 0; i < 3000; i++) {
            generated.add(ids.next());
        }
        assertThat(generated).doesNotHaveDuplicates().isSorted();
    }

    @Test
    void isUniqueAcrossThreads() {
        SnowflakeIds ids = new SnowflakeIds(0);
        Set<Long> seen = ConcurrentHashMap.newKeySet();
        IntStream.range(0, 8).parallel().forEach(t -> {
            for (int i = 0; i < 5_000; i++) {
                assertThat(seen.add(ids.next())).isTrue();
            }
        });
        assertThat(seen).hasSize(40_000);
    }

    @Test
    void rejectsOutOfRangeWorkers() {
        assertThatThrownBy(() -> new SnowflakeIds(4)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SnowflakeIds(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    private static Clock clock(AtomicLong millis) {
        return new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return Instant.ofEpochMilli(millis.get());
            }
        };
    }
}
