package com.geuneul.domain.report;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 공개 외부 API 프록시 남용 방어 — 클라이언트별 분당 고정창 리밋. */
@Component
public class ExternalApiRateLimiter {

    static final int MAX_TRACKED_CLIENTS = 10_000;

    private final Clock clock;
    private final Map<String, ClientWindow> windows = new ConcurrentHashMap<>();

    public ExternalApiRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public boolean tryAcquire(String scope, String clientKey, int perMinute) {
        long minuteBucket = clock.instant().getEpochSecond() / 60;
        evictIfOversized(minuteBucket);

        String key = scope + ":" + clientKey;
        boolean[] allowed = {false};
        windows.compute(key, (ignored, existing) -> {
            ClientWindow w = existing == null ? new ClientWindow() : existing;
            w.rollIfNeeded(minuteBucket);
            if (w.count < perMinute) {
                w.count++;
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

    int trackedWindows() {
        return windows.size();
    }

    private static final class ClientWindow {
        private long minuteBucket = Long.MIN_VALUE;
        private int count;

        void rollIfNeeded(long minuteBucket) {
            if (this.minuteBucket != minuteBucket) {
                this.minuteBucket = minuteBucket;
                this.count = 0;
            }
        }
    }
}
