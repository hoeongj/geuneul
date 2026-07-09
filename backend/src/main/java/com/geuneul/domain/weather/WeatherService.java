package com.geuneul.domain.weather;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * "지금 날씨" 조회 진입점 — 좌표를 기상청 격자로 바꾸고, 현재 발표시각(base slot)을 계산해 실황을 가져온다.
 *
 * 캐싱은 WeatherClient의 HTTP 경계에서 일어난다(같은 격자·같은 발표시각 재조회 안 함). 여기서는
 * 발표시각을 결정적으로 계산하는 게 핵심 — 그래야 캐시 키가 매시각 한 번만 바뀐다.
 *
 * base slot 규칙(초단기실황): 매시각 정시(HH00) 생성, API 제공은 매시각 40분 이후. 그래서 KST 기준
 * "지금 −40분"을 정시로 내림한 시각을 쓴다(아직 안 나온 슬롯을 조회해 빈 응답 받는 걸 방지).
 * Clock은 systemUTC(TimeConfig)라 KST로 변환해 계산한다 — 발표시각은 한국 표준시 기준이므로.
 */
@Service
public class WeatherService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmm");
    private static final int PUBLISH_DELAY_MIN = 40; // 실황 제공 지연(정시 +40분)

    private final WeatherClient client;
    private final Clock clock;

    public WeatherService(WeatherClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    /** 좌표의 지금 날씨. 키/네트워크/응답 결측 시 Optional.empty(호출부는 날씨 없이도 동작). */
    public Optional<Weather> getWeather(double lat, double lon) {
        KmaGrid.Grid grid = KmaGrid.toGrid(lat, lon);
        ZonedDateTime slot = ZonedDateTime.now(clock)
                .withZoneSameInstant(KST)
                .minusMinutes(PUBLISH_DELAY_MIN)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
        return client.fetchNowcast(grid.nx(), grid.ny(), slot.format(DATE), slot.format(TIME));
    }
}
