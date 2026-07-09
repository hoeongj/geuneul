package com.geuneul.domain.weather;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 발표시각(base slot) 계산 검증 — KST 변환 + "지금 −40분" 정시 내림이 결정적인지 못 박는다.
 * (슬롯이 틀리면 아직 안 나온 시각을 조회해 빈 응답을 받으므로 조용한 오답이 된다.)
 */
class WeatherServiceTest {

    // 서울시청 → 격자 (60, 127)
    private static final double LAT = 37.5665;
    private static final double LON = 126.9780;

    @Test
    @DisplayName("14:20 KST → 13:40으로 40분 내리고 정시로 내림 → base_time 1300")
    void slotSubtracts40AndTruncates() {
        WeatherClient client = mock(WeatherClient.class);
        when(client.fetchNowcast(anyInt(), anyInt(), anyString(), anyString())).thenReturn(Optional.empty());
        // 2026-07-09 05:20 UTC = 14:20 KST
        Clock clock = Clock.fixed(Instant.parse("2026-07-09T05:20:00Z"), ZoneOffset.UTC);

        new WeatherService(client, clock).getWeather(LAT, LON);

        verify(client).fetchNowcast(60, 127, "20260709", "1300");
    }

    @Test
    @DisplayName("00:20 KST → −40분이면 전날 23:40 → base_date 전날, base_time 2300 (날짜 롤오버)")
    void slotRollsOverToPreviousDay() {
        WeatherClient client = mock(WeatherClient.class);
        when(client.fetchNowcast(anyInt(), anyInt(), anyString(), anyString())).thenReturn(Optional.empty());
        // 2026-07-08 15:10 UTC = 2026-07-09 00:10 KST
        Clock clock = Clock.fixed(Instant.parse("2026-07-08T15:10:00Z"), ZoneOffset.UTC);

        new WeatherService(client, clock).getWeather(LAT, LON);

        verify(client).fetchNowcast(60, 127, "20260708", "2300");
    }
}
