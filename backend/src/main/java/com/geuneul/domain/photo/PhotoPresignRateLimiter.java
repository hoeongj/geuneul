package com.geuneul.domain.photo;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * presign 발급 남용 방어 — 클라이언트(IP)별 고정창(fixed-window) 2단 리밋.
 * {@code report}는 비로그인도 허용하므로(§9), presign 자체가 실질적으로 "S3에 최대 8MB를 쓸 권한"을
 * 찍어주는 셈이라 익명 제보(ReportRateLimiter)와 별개로 방어가 필요하다. {@code review}는 로그인이
 * 이미 걸려 있지만 탈취 계정 남용 방어를 위해 동일하게 적용한다.
 *
 * <p>ReportRateLimiter(TS-008 하드닝, 원자적 compute·맵 상한 evict)와 설계·구현이 동일하다 —
 * 소비자가 report/photo 둘뿐이라 "rule of three"에 따라 지금은 추상화하지 않고 나란히 둔다.
 * 세 번째 소비자가 생기면 그때 {@code global.web}으로 공통 추출한다(WORKLOG 근거).
 */
@Component
public class PhotoPresignRateLimiter {

    /** 사진 1장당 presign 1회가 정상 경로 — 재시도 여유를 감안해 report(3/10)보다 살짝 넉넉하게 잡는다. */
    static final int PER_MINUTE = 5;
    static final int PER_HOUR = 20;
    static final int MAX_TRACKED_CLIENTS = 10_000;

    private final Clock clock;
    private final Map<String, ClientWindow> windows = new ConcurrentHashMap<>();

    public PhotoPresignRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public boolean tryAcquire(String clientKey) {
        long epochSecond = clock.instant().getEpochSecond();
        long minuteBucket = epochSecond / 60;
        long hourBucket = epochSecond / 3_600;

        evictIfOversized(minuteBucket);

        boolean[] allowed = {false};
        windows.compute(clientKey, (key, existing) -> {
            ClientWindow w = (existing == null) ? new ClientWindow() : existing;
            w.rollIfNeeded(minuteBucket, hourBucket);
            if (w.minuteCount < PER_MINUTE && w.hourCount < PER_HOUR) {
                w.minuteCount++;
                w.hourCount++;
                allowed[0] = true;
            }
            return w;
        });
        return allowed[0];
    }

    private void evictIfOversized(long currentMinuteBucket) {
        if (windows.size() <= MAX_TRACKED_CLIENTS) {
            return;
        }
        windows.values().removeIf(w -> w.minuteBucket != currentMinuteBucket);
        if (windows.size() > MAX_TRACKED_CLIENTS) {
            windows.clear();
        }
    }

    /** 테스트용 — 추적 중인 클라이언트 창 수(맵 상한 검증). */
    int trackedWindows() {
        return windows.size();
    }

    private static final class ClientWindow {
        private long minuteBucket = Long.MIN_VALUE;
        private long hourBucket = Long.MIN_VALUE;
        private int minuteCount;
        private int hourCount;

        void rollIfNeeded(long minuteBucket, long hourBucket) {
            if (this.minuteBucket != minuteBucket) {
                this.minuteBucket = minuteBucket;
                this.minuteCount = 0;
            }
            if (this.hourBucket != hourBucket) {
                this.hourBucket = hourBucket;
                this.hourCount = 0;
            }
        }
    }
}
