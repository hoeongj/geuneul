package com.geuneul.domain.ingest.geocode;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * IT용 페이크 지오코더 — 실제 카카오 API 호출 없이 지오코딩 경로(성공/실패/재사용)를 검증한다.
 * 주소→좌표 맵을 테스트가 심고, 호출 횟수로 "재사용 시 재호출 없음"을 단언한다.
 */
@TestConfiguration
public class FakeGeocodingConfig {

    public static class FakeGeocodingClient implements GeocodingClient {

        private final Map<String, LatLng> known = new ConcurrentHashMap<>();
        private final AtomicInteger calls = new AtomicInteger();

        public void willReturn(String address, double lat, double lng) {
            known.put(address, new LatLng(lat, lng));
        }

        public int calls() {
            return calls.get();
        }

        public void reset() {
            known.clear();
            calls.set(0);
        }

        @Override
        public Optional<LatLng> geocode(String address) {
            calls.incrementAndGet();
            return Optional.ofNullable(known.get(address));
        }
    }

    @Bean
    @Primary
    public FakeGeocodingClient fakeGeocodingClient() {
        return new FakeGeocodingClient();
    }
}
