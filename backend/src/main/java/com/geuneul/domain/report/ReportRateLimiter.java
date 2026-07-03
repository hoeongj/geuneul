package com.geuneul.domain.report;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 익명 제보 남용 방어 — 클라이언트(IP)별 고정창(fixed-window) 2단 리밋.
 * 분당 {@value #PER_MINUTE}(버스트 억제) + 시간당 {@value #PER_HOUR}(지속 남용 억제).
 *
 * 의도적으로 인메모리·무의존성이다: 프로덕션이 단일 Fargate 태스크(수평 확장 전)이고,
 * 2026 관례도 "단일 인스턴스는 인메모리로 시작, 수평 확장 시 Redis(Bucket4j 등)로 이전"이다
 * (WORKLOG 2026-07-03 근거). 다중 인스턴스가 되면 이 클래스만 Redis 구현으로 교체한다.
 */
@Component
public class ReportRateLimiter {

    static final int PER_MINUTE = 3;
    static final int PER_HOUR = 10;
    /** 맵 폭주 방지 상한(창 맵별). 초과 시 만료 창 정리 → 그래도 넘으면 전체 비움(OOM 방지). */
    static final int MAX_TRACKED_CLIENTS = 10_000;

    private final Clock clock;
    private final Map<String, Window> minuteWindows = new ConcurrentHashMap<>();
    private final Map<String, Window> hourWindows = new ConcurrentHashMap<>();

    public ReportRateLimiter() {
        this(Clock.systemUTC());
    }

    ReportRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** 허용되면 true(카운트 소모), 초과면 false. 두 창 모두 여유가 있어야 허용된다. */
    public boolean tryAcquire(String clientKey) {
        long epochSecond = clock.instant().getEpochSecond();
        long minuteBucket = epochSecond / 60;
        long hourBucket = epochSecond / 3_600;

        evictIfOversized(minuteWindows, minuteBucket);
        evictIfOversized(hourWindows, hourBucket);

        // 확인만 먼저(둘 다 여유가 있을 때만 소모) — 한쪽만 카운트가 새는 것을 방지.
        Window minute = minuteWindows.compute(clientKey, (k, w) -> fresh(w, minuteBucket));
        Window hour = hourWindows.compute(clientKey, (k, w) -> fresh(w, hourBucket));
        if (minute.count.get() >= PER_MINUTE || hour.count.get() >= PER_HOUR) {
            return false;
        }
        minute.count.incrementAndGet();
        hour.count.incrementAndGet();
        return true;
    }

    private static Window fresh(Window current, long bucket) {
        return (current == null || current.bucket != bucket) ? new Window(bucket) : current;
    }

    private static void evictIfOversized(Map<String, Window> windows, long currentBucket) {
        if (windows.size() <= MAX_TRACKED_CLIENTS) {
            return;
        }
        // 1차: 만료된(다른 버킷) 창 정리.
        windows.values().removeIf(w -> w.bucket != currentBucket);
        // 2차: 여전히 상한 초과면(= 동일 버킷 고유키 폭주 — 위조 XFF 회전 공격) 전체 비운다.
        // 카운트 리셋을 감수하더라도 힙 상한을 우선한다(무한 증가 → OOM 방지). 남용 방어 관점에서 허용.
        // 리뷰(TS 확정): 이전 구현은 동일 버킷 폭주 시 removeIf가 아무것도 못 지워 no-op였다.
        if (windows.size() > MAX_TRACKED_CLIENTS) {
            windows.clear();
        }
    }

    /** 테스트용 — 추적 중인 클라이언트 창 수(맵 상한 검증). */
    int trackedWindows() {
        return minuteWindows.size() + hourWindows.size();
    }

    private static final class Window {
        final long bucket;
        final AtomicInteger count = new AtomicInteger();

        Window(long bucket) {
            this.bucket = bucket;
        }
    }
}
