package com.geuneul.domain.place;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geuneul.AbstractIntegrationTest;
import com.geuneul.domain.report.Report;
import com.geuneul.domain.report.ReportRepository;
import com.geuneul.domain.report.ReportType;
import com.geuneul.domain.weather.PrecipitationType;
import com.geuneul.domain.weather.Weather;
import com.geuneul.domain.weather.WeatherClient;
import com.geuneul.global.geo.GeoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 날씨 comfort additive 복원 엔드투엔드 IT (P3 날씨 2부, ADR-0009).
 *
 * 실 PostGIS의 place_report_signals 뷰(제보 comfort, ADR-0007)와 WeatherClient(모킹 — KMA 네트워크
 * 호출 없이 관측값만 주입)를 함께 태워, WeatherService→HeatComfort→SurvivalScore 배선이
 * {@code /places/{id}} 응답까지 관통해 점수를 움직이는지 검증한다.
 *
 * WeatherClient <b>빈만</b> 모킹한다({@code @MockitoBean}은 AOP 프록시가 아닌 순수 목이라 @Cacheable을
 * 우회한다) — 이는 캐시 자체가 아니라 "날씨 신호가 comfort_score에 additive로 반영되는지"가 이 IT의
 * 관심사이기 때문이다(캐시 계층은 RedisCacheConfigTest·WeatherCacheProxyTest가 이미 검증).
 */
@AutoConfigureMockMvc
class WeatherComfortIT extends AbstractIntegrationTest {

    private static final double LAT = 37.4986;
    private static final double LNG = 126.9531;

    // 앱 컨텍스트의 ObjectMapper 빈에 의존하지 않는다 — 이 IT의 관심사는 응답 JSON의 단순 필드 파싱뿐이라
    // 커스텀 모듈이 필요 없고, @SpringBootTest(웹 MVC 슬라이스 없이) 컨텍스트엔 JacksonAutoConfiguration이
    // 항상 ObjectMapper 빈을 등록해 주지 않는다(WebMvcTest와 달리).
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    MockMvc mvc;

    @Autowired
    PlaceRepository placeRepository;

    @Autowired
    ReportRepository reportRepository;

    @MockitoBean
    WeatherClient weatherClient;

    private Long placeId;

    @BeforeEach
    void setUp() {
        reportRepository.deleteAll();
        placeRepository.deleteAll();
        Place p = placeRepository.save(Place.of(
                "상도1동 무더위쉼터", PlaceCategory.COOLING_SHELTER, "서울 동작구 성대로 100",
                GeoUtils.point(LAT, LNG), "test", "weather-comfort-1"));
        placeId = p.getId();
        reportRepository.save(Report.anonymous(placeId, ReportType.COOL, null, true,
                OffsetDateTime.now().plusHours(3)));
    }

    private void stubWeather(Optional<Weather> weather) {
        when(weatherClient.fetchNowcast(anyInt(), anyInt(), anyString(), anyString())).thenReturn(weather);
    }

    @Test
    @DisplayName("날씨 조회 실패(빈 응답)면 500 없이 기존(기온 성분 제외) 점수로 폴백한다")
    void weatherUnavailableFallsBackGracefully() throws Exception {
        stubWeather(Optional.empty());

        mvc.perform(get("/places/" + placeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.survival.grade").value("GOOD"));
    }

    @Test
    @DisplayName("쾌적한 날씨(23도)면 같은 제보라도 comfort_score가 날씨 없을 때보다 높다")
    void pleasantWeatherRaisesComfort() throws Exception {
        stubWeather(Optional.of(new Weather(23.0, 55, 0.0, PrecipitationType.NONE, "202607091300")));
        double comfortWithWeather = comfortScore(mvc.perform(get("/places/" + placeId))
                .andExpect(status().isOk()));

        stubWeather(Optional.empty());
        double comfortWithoutWeather = comfortScore(mvc.perform(get("/places/" + placeId))
                .andExpect(status().isOk()));

        assertThat(comfortWithWeather).isGreaterThan(comfortWithoutWeather);
    }

    @Test
    @DisplayName("폭염(체감 38도 이상)이면 같은 제보라도 종합 점수가 쾌적 날씨보다 낮다")
    void heatwaveLowersScoreVersusPleasant() throws Exception {
        stubWeather(Optional.of(new Weather(23.0, 55, 0.0, PrecipitationType.NONE, "202607091300")));
        int pleasantScore = score(mvc.perform(get("/places/" + placeId)).andExpect(status().isOk()));

        stubWeather(Optional.of(new Weather(38.0, 85, 0.0, PrecipitationType.NONE, "202607091300")));
        int heatwaveScore = score(mvc.perform(get("/places/" + placeId)).andExpect(status().isOk()));

        assertThat(heatwaveScore).isLessThan(pleasantScore);
    }

    @Test
    @DisplayName("제보 없는 장소는 날씨가 좋아도 등급은 여전히 UNKNOWN(정보 부족) — 등급은 제보 존재로만 결정")
    void weatherNeverPromotesUnknownGrade() throws Exception {
        reportRepository.deleteAll(); // reportCount=0으로
        stubWeather(Optional.of(new Weather(23.0, 55, 0.0, PrecipitationType.NONE, "202607091300")));

        mvc.perform(get("/places/" + placeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.survival.grade").value("UNKNOWN"));
    }

    private double comfortScore(ResultActions result) throws Exception {
        return survivalNode(result).path("comfortScore").asDouble();
    }

    private int score(ResultActions result) throws Exception {
        return survivalNode(result).path("score").asInt();
    }

    private JsonNode survivalNode(ResultActions result) throws Exception {
        JsonNode root = OBJECT_MAPPER.readTree(result.andReturn().getResponse().getContentAsString());
        return root.path("survival");
    }
}
