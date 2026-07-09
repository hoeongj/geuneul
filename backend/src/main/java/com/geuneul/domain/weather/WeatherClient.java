package com.geuneul.domain.weather;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

/**
 * 기상청 초단기실황(getUltraSrtNcst) 조회 — 한 격자(nx,ny)의 "지금" 관측값을 가져온다.
 * (docs: 공공데이터포털 "기상청_단기예보 조회서비스", 데이터ID 15084084 / VilageFcstInfoService_2.0)
 *
 * - 예보(Fcst)가 아니라 실황(Ncst)을 쓰는 이유: 앱 테제가 "지금 상태"라 관측 기온·습도·강수가
 *   survival_score의 기온(comfort) 성분에 더 정확하다. 예보는 향후 "곧 비 옴" 알림용으로 남긴다.
 * - 캐시는 외부 HTTP 호출 경계에 둔다(@Cacheable "weather"): 같은 격자·같은 발표시각(base_date+time)은
 *   재조회하지 않음 → 기상청 rate limit 회피 + 지연 절감. TTL은 RedisCacheConfig에서 30분.
 *   실패(빈 결과)는 캐시하지 않는다(unless) — 일시 장애가 30분간 굳지 않게.
 * - serviceKey는 **디코딩 키**를 주입받아 UriBuilder가 한 번만 인코딩하게 한다(data.go.kr의 이중 인코딩 함정 회피).
 *   값은 환경변수 KMA_SERVICE_KEY로만(규칙 D: 코드/레포에 하드코딩 금지).
 * - 응답은 타입 있는 record로 역직렬화한다(Boot 4=Jackson 3에서 JsonNode는 실패 — TS-004와 동일 사유).
 */
@Component
public class WeatherClient {

    private static final Logger log = LoggerFactory.getLogger(WeatherClient.class);
    private static final String OK = "00"; // 기상청 정상 resultCode

    private final RestClient restClient;
    private final String serviceKey;
    private final boolean keyPresent;

    @Autowired
    public WeatherClient(@Value("${weather.service-key:}") String serviceKey) {
        this(serviceKey, RestClient.builder());
    }

    /** 테스트용 — MockRestServiceServer를 바인딩한 builder를 주입해 실제 파싱 경로를 검증한다. */
    WeatherClient(String serviceKey, RestClient.Builder builder) {
        this.serviceKey = serviceKey == null ? "" : serviceKey;
        this.keyPresent = !this.serviceKey.isBlank();
        this.restClient = builder
                .baseUrl("https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0")
                .build();
    }

    /**
     * 격자(nx,ny)의 발표시각(baseDate yyyyMMdd, baseTime HHmm) 실황을 조회한다.
     * 네 인자가 모두 캐시 키 — 발표시각이 바뀌면(매시각) 키가 달라져 자연히 새로 조회된다.
     */
    @Cacheable(cacheNames = "weather", key = "#nx + ':' + #ny + ':' + #baseDate + ':' + #baseTime",
            unless = "#result == null || #result.isEmpty()")
    public Optional<Weather> fetchNowcast(int nx, int ny, String baseDate, String baseTime) {
        if (!keyPresent) {
            log.warn("[weather] KMA_SERVICE_KEY 미설정 — 날씨 조회를 건너뜁니다(규칙 D: .env/SSM으로만).");
            return Optional.empty();
        }
        try {
            KmaResponse body = restClient.get()
                    .uri(uri -> uri.path("/getUltraSrtNcst")
                            .queryParam("serviceKey", serviceKey)
                            .queryParam("dataType", "JSON")
                            .queryParam("numOfRows", 100)
                            .queryParam("pageNo", 1)
                            .queryParam("base_date", baseDate)
                            .queryParam("base_time", baseTime)
                            .queryParam("nx", nx)
                            .queryParam("ny", ny)
                            .build())
                    .retrieve()
                    .body(KmaResponse.class);

            List<KmaResponse.Item> items = body == null ? null : body.items();
            if (items == null || items.isEmpty()) {
                String code = body == null ? null : body.resultCode();
                if (code != null && !OK.equals(code)) {
                    log.warn("[weather] 기상청 오류 resultCode={} (nx={}, ny={})", code, nx, ny);
                }
                return Optional.empty();
            }
            return Optional.of(toWeather(items, baseDate + baseTime));
        } catch (Exception e) {
            log.warn("[weather] 조회 실패 (nx={}, ny={}): {}", nx, ny, e.getMessage());
            return Optional.empty();
        }
    }

    private static Weather toWeather(List<KmaResponse.Item> items, String observedAt) {
        Double temp = null;
        Integer humidity = null;
        Double rain = null;
        PrecipitationType pty = PrecipitationType.NONE;
        for (KmaResponse.Item it : items) {
            if (it == null || it.category() == null) {
                continue;
            }
            String v = it.obsrValue();
            switch (it.category()) {
                case "T1H" -> temp = parseDouble(v);
                case "REH" -> humidity = parseInt(v);
                case "RN1" -> rain = parseRain(v);
                case "PTY" -> pty = PrecipitationType.fromCode(v);
                default -> {
                }
            }
        }
        return new Weather(temp, humidity, rain, pty, observedAt);
    }

    private static Double parseDouble(String v) {
        try {
            return v == null ? null : Double.valueOf(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseInt(String v) {
        try {
            return v == null ? null : Integer.valueOf(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** RN1은 "강수없음"·"1.0mm" 등 문자열로 올 수 있다. 숫자만 뽑고, 없으면 0.0. */
    private static Double parseRain(String v) {
        if (v == null || v.isBlank() || v.contains("없음")) {
            return 0.0;
        }
        String num = v.replaceAll("[^0-9.]", "");
        if (num.isBlank()) {
            return 0.0;
        }
        try {
            return Double.valueOf(num);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /** 기상청 응답(필요 필드만). 관측값은 obsrValue. */
    record KmaResponse(Response response) {
        String resultCode() {
            return response == null || response.header() == null ? null : response.header().resultCode();
        }

        List<Item> items() {
            if (response == null || response.body() == null || response.body().items() == null) {
                return null;
            }
            return response.body().items().item();
        }

        record Response(Header header, Body body) {
        }

        record Header(String resultCode, String resultMsg) {
        }

        record Body(Items items) {
        }

        record Items(List<Item> item) {
        }

        record Item(String category, String obsrValue) {
        }
    }
}
