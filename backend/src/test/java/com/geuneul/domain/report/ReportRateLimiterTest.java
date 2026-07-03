package com.geuneul.domain.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 고정창 2단(분당 3·시간당 10) 리밋 동작 검증 — 시간은 가짜 Clock으로 결정적으로 제어한다.
 */
class ReportRateLimiterTest {

    /** 테스트에서 임의로 전진시킬 수 있는 시계. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-07-03T12:00:00Z");

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
    private final ReportRateLimiter limiter = new ReportRateLimiter(clock);

    @Test
    @DisplayName("같은 분 안에서 3회까지 허용, 4번째는 거부")
    void perMinuteBurstLimit() {
        assertThat(limiter.tryAcquire("ip-1")).isTrue();
        assertThat(limiter.tryAcquire("ip-1")).isTrue();
        assertThat(limiter.tryAcquire("ip-1")).isTrue();
        assertThat(limiter.tryAcquire("ip-1")).isFalse();
    }

    @Test
    @DisplayName("분이 넘어가면 분당 창은 리셋된다")
    void minuteWindowResets() {
        for (int i = 0; i < 3; i++) limiter.tryAcquire("ip-1");
        assertThat(limiter.tryAcquire("ip-1")).isFalse();

        clock.advance(Duration.ofMinutes(1));
        assertThat(limiter.tryAcquire("ip-1")).isTrue();
    }

    @Test
    @DisplayName("분당 창을 피해도 시간당 10회를 넘기면 거부된다")
    void perHourCapAcrossMinutes() {
        // 4분에 걸쳐 3+3+3+1 = 10회 소진
        for (int minute = 0; minute < 3; minute++) {
            for (int i = 0; i < 3; i++) {
                assertThat(limiter.tryAcquire("ip-1")).isTrue();
            }
            clock.advance(Duration.ofMinutes(1));
        }
        assertThat(limiter.tryAcquire("ip-1")).isTrue();   // 10번째

        clock.advance(Duration.ofMinutes(1));
        assertThat(limiter.tryAcquire("ip-1")).isFalse();  // 11번째 — 시간당 상한

        clock.advance(Duration.ofHours(1));
        assertThat(limiter.tryAcquire("ip-1")).isTrue();   // 시간 창 리셋
    }

    @Test
    @DisplayName("클라이언트 키가 다르면 서로 영향을 주지 않는다")
    void independentPerClient() {
        for (int i = 0; i < 3; i++) limiter.tryAcquire("ip-1");
        assertThat(limiter.tryAcquire("ip-1")).isFalse();
        assertThat(limiter.tryAcquire("ip-2")).isTrue();
    }

    @Test
    @DisplayName("거부된 호출은 카운트를 소모하지 않는다 (분 거부가 시간 창을 갉아먹지 않음)")
    void deniedCallsDoNotConsume() {
        for (int i = 0; i < 3; i++) limiter.tryAcquire("ip-1");
        for (int i = 0; i < 20; i++) limiter.tryAcquire("ip-1"); // 전부 거부 — 소모 없어야 함

        // 분을 넘기면: 시간당 잔여 7회가 그대로 남아 있어야 한다 (3+3+1)
        clock.advance(Duration.ofMinutes(1));
        for (int i = 0; i < 3; i++) assertThat(limiter.tryAcquire("ip-1")).isTrue();
        clock.advance(Duration.ofMinutes(1));
        for (int i = 0; i < 3; i++) assertThat(limiter.tryAcquire("ip-1")).isTrue();
        clock.advance(Duration.ofMinutes(1));
        assertThat(limiter.tryAcquire("ip-1")).isTrue();  // 10번째
        assertThat(limiter.tryAcquire("ip-1")).isFalse(); // 분당은 남았지만 시간당 소진
    }
}
