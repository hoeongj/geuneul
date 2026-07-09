package com.geuneul.domain.photo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 고정창 2단(분당 5·시간당 20) 리밋 동작 검증 — ReportRateLimiterTest와 동형(설계 재사용, 클래스 주석 참고).
 */
class PhotoPresignRateLimiterTest {

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-07-09T12:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final PhotoPresignRateLimiter limiter = new PhotoPresignRateLimiter(clock);

    @Test
    @DisplayName("같은 분 안에서 5회까지 허용, 6번째는 거부")
    void perMinuteBurstLimit() {
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("ip-1")).isTrue();
        }
        assertThat(limiter.tryAcquire("ip-1")).isFalse();
    }

    @Test
    @DisplayName("분이 넘어가면 분당 창은 리셋된다")
    void minuteWindowResets() {
        for (int i = 0; i < 5; i++) limiter.tryAcquire("ip-1");
        assertThat(limiter.tryAcquire("ip-1")).isFalse();

        clock.advance(Duration.ofMinutes(1));
        assertThat(limiter.tryAcquire("ip-1")).isTrue();
    }

    @Test
    @DisplayName("분당 창을 피해도 시간당 20회를 넘기면 거부된다")
    void perHourCapAcrossMinutes() {
        // 4분에 걸쳐 5+5+5+5 = 20회 소진
        for (int minute = 0; minute < 4; minute++) {
            for (int i = 0; i < 5; i++) {
                assertThat(limiter.tryAcquire("ip-1")).isTrue();
            }
            clock.advance(Duration.ofMinutes(1));
        }
        assertThat(limiter.tryAcquire("ip-1")).isFalse(); // 시간당 상한 소진 후

        clock.advance(Duration.ofHours(1));
        assertThat(limiter.tryAcquire("ip-1")).isTrue();  // 시간 창 리셋
    }

    @Test
    @DisplayName("클라이언트 키가 다르면 서로 영향을 주지 않는다")
    void independentPerClient() {
        for (int i = 0; i < 5; i++) limiter.tryAcquire("ip-1");
        assertThat(limiter.tryAcquire("ip-1")).isFalse();
        assertThat(limiter.tryAcquire("ip-2")).isTrue();
    }

    @Test
    @DisplayName("동일 버킷 고유키 폭주에도 맵이 상한을 넘어 무한 증가하지 않는다 (OOM 방지, ReportRateLimiter와 동일 하드닝)")
    void mapStaysBoundedUnderUniqueKeyFlood() {
        int flood = PhotoPresignRateLimiter.MAX_TRACKED_CLIENTS * 5;
        for (int i = 0; i < flood; i++) {
            limiter.tryAcquire("flood-" + i);
        }
        assertThat(limiter.trackedWindows())
                .isLessThanOrEqualTo(PhotoPresignRateLimiter.MAX_TRACKED_CLIENTS + 1);
    }
}
