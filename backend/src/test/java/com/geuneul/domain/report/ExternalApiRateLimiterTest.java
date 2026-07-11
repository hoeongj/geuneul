package com.geuneul.domain.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalApiRateLimiterTest {

    private final MutableClock clock = new MutableClock();
    private final ExternalApiRateLimiter limiter = new ExternalApiRateLimiter(clock);

    @Test
    @DisplayName("지정된 분당 횟수까지만 허용한다")
    void perMinuteLimit() {
        assertThat(limiter.tryAcquire("routes", "x:1", 2)).isTrue();
        assertThat(limiter.tryAcquire("routes", "x:1", 2)).isTrue();
        assertThat(limiter.tryAcquire("routes", "x:1", 2)).isFalse();

        clock.advance(Duration.ofMinutes(1));
        assertThat(limiter.tryAcquire("routes", "x:1", 2)).isTrue();
    }

    @Test
    @DisplayName("scope가 다르면 같은 클라이언트도 서로 영향을 주지 않는다")
    void scopesAreIndependent() {
        assertThat(limiter.tryAcquire("routes", "x:1", 1)).isTrue();
        assertThat(limiter.tryAcquire("routes", "x:1", 1)).isFalse();
        assertThat(limiter.tryAcquire("places-search", "x:1", 1)).isTrue();
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-07-03T12:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
