package com.geuneul.domain.place;

import com.geuneul.domain.ai.AiSummaryService;
import com.geuneul.domain.place.dto.PlaceResponse;
import com.geuneul.domain.weather.WeatherService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PlaceSearchService 단위 테스트(Mockito, DB 불필요) — 날씨 comfort 배선(P3 날씨 2부, ADR-0009)의
 * <b>N+1 금지 계약</b>을 못 박는다: 결과에 장소가 몇 개든 WeatherService는 요청(쿼리)당 정확히 1회만
 * 호출돼야 한다. 공간쿼리 정확성·survival 조립은 SurvivalScoreIT(실 PostGIS)가 검증.
 *
 * <p>AI 요약(P3, ADR-0010)의 <b>단건 상세 전용 계약</b>도 여기서 못 박는다: AiSummaryService는
 * {@link PlaceSearchService#getById(long)}에서만 호출되고, 목록/반경/bounds/nearest 경로에서는
 * 절대 호출되지 않는다(비용 방어 — AI는 상세 조회 1건에만 붙는다).
 *
 * <p>관측성(P4, ADR-0014)의 <b>커스텀 타이머 계약</b>도 여기서 못 박는다: 반경/kNN 검색은 각각
 * {@code geuneul.place.search.radius}/{@code geuneul.place.search.nearest} Timer를 정확히 1회 기록한다
 * (SimpleMeterRegistry — 실 Prometheus/OTLP 없이도 Micrometer 표준 인메모리 레지스트리로 검증 가능).
 */
class PlaceSearchServiceTest {

    private final PlaceRepository placeRepository = mock(PlaceRepository.class);
    private final PlaceFeatureRepository placeFeatureRepository = mock(PlaceFeatureRepository.class);
    private final WeatherService weatherService = mock(WeatherService.class);
    private final AiSummaryService aiSummaryService = mock(AiSummaryService.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final PlaceSearchService service =
            new PlaceSearchService(placeRepository, placeFeatureRepository, weatherService, aiSummaryService,
                    meterRegistry);

    @BeforeEach
    void stubWeatherAvailable() {
        when(weatherService.getComfortScore(anyDouble(), anyDouble())).thenReturn(Optional.of(0.8));
        when(aiSummaryService.summarize(anyLong())).thenReturn(Optional.empty());
        when(placeFeatureRepository.findByPlaceIdOrderByFeatureType(anyLong())).thenReturn(List.of());
    }

    @Test
    @DisplayName("반경 검색: 결과가 여러 건이어도 WeatherService는 요청당 1회만 호출된다")
    void searchRadiusCallsWeatherExactlyOnce() {
        when(placeRepository.findWithinRadiusScored(anyDouble(), anyDouble(), anyDouble(), any(), anyInt()))
                .thenReturn(List.of(view(1, 10.0), view(2, 20.0), view(3, 30.0)));

        List<PlaceResponse> result = service.searchRadius(37.5, 127.0, 800, null, 100);

        assertThat(result).hasSize(3);
        result.forEach(r -> assertThat(r.survival()).isNotNull());
        verify(weatherService, times(1)).getComfortScore(eq(37.5), eq(127.0));
    }

    @Test
    @DisplayName("bounds 검색: 결과가 여러 건이어도 WeatherService는 bounds 중심(centroid) 기준 1회만 호출된다")
    void searchBoundsCallsWeatherExactlyOnceAtCentroid() {
        when(placeRepository.findInBoundsScored(anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(), anyInt()))
                .thenReturn(List.of(view(1, null), view(2, null), view(3, null), view(4, null)));

        List<PlaceResponse> result = service.searchBounds(126.9, 37.4, 127.1, 37.6, null, 100);

        assertThat(result).hasSize(4);
        // centroid = ((37.4+37.6)/2, (126.9+127.1)/2) = (37.5, 127.0)
        verify(weatherService, times(1)).getComfortScore(eq(37.5), eq(127.0));
    }

    @Test
    @DisplayName("단건 상세: 장소 좌표 기준 1회 호출되고 comfort가 응답에 반영된다")
    void getByIdCallsWeatherOnceAtPlaceCoordinate() {
        when(placeRepository.findByIdScored(1L)).thenReturn(Optional.of(view(1, null)));

        PlaceResponse result = service.getById(1L);

        assertThat(result.survival()).isNotNull();
        verify(weatherService, times(1)).getComfortScore(anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("날씨 조회 실패(빈 Optional)여도 예외 없이 report-only 점수로 폴백한다(graceful degradation)")
    void weatherUnavailableStillReturnsScoredResponse() {
        when(weatherService.getComfortScore(anyDouble(), anyDouble())).thenReturn(Optional.empty());
        when(placeRepository.findWithinRadiusScored(anyDouble(), anyDouble(), anyDouble(), any(), anyInt()))
                .thenReturn(List.of(view(1, 10.0)));

        List<PlaceResponse> result = service.searchRadius(37.5, 127.0, 800, null, 100);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).survival()).isNotNull();
    }

    @Test
    @DisplayName("nearest(팬아웃) 경로는 survival을 계산하지 않으므로 WeatherService를 호출하지 않는다")
    void searchNearestNeverCallsWeather() {
        when(placeRepository.findNearest(anyDouble(), anyDouble(), any(), anyInt())).thenReturn(List.of());

        service.searchNearest(37.5, 127.0, null, 5);

        verify(weatherService, times(0)).getComfortScore(anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("단건 상세: AiSummaryService가 이 장소 ID로 정확히 1회 호출되고 결과가 응답에 반영된다")
    void getByIdCallsAiSummaryOnceAndPopulatesResponse() {
        when(placeRepository.findByIdScored(1L)).thenReturn(Optional.of(view(1, null)));
        when(aiSummaryService.summarize(1L)).thenReturn(Optional.of("최근 제보 기준 시원해요"));

        PlaceResponse result = service.getById(1L);

        assertThat(result.aiSummary()).isEqualTo("최근 제보 기준 시원해요");
        verify(aiSummaryService, times(1)).summarize(1L);
    }

    @Test
    @DisplayName("AI 요약 실패(빈 Optional)여도 예외 없이 aiSummary=null로 폴백한다(graceful degradation)")
    void aiSummaryUnavailableStillReturnsResponseWithNullSummary() {
        when(placeRepository.findByIdScored(1L)).thenReturn(Optional.of(view(1, null)));
        when(aiSummaryService.summarize(1L)).thenReturn(Optional.empty());

        PlaceResponse result = service.getById(1L);

        assertThat(result.aiSummary()).isNull();
        assertThat(result.survival()).isNotNull(); // survival_score는 AI와 무관하게 정상 조립
    }

    @Test
    @DisplayName("반경 검색은 AiSummaryService를 호출하지 않는다(목록 경로는 AI 비용을 지지 않는다)")
    void searchRadiusNeverCallsAiSummary() {
        when(placeRepository.findWithinRadiusScored(anyDouble(), anyDouble(), anyDouble(), any(), anyInt()))
                .thenReturn(List.of(view(1, 10.0), view(2, 20.0)));

        service.searchRadius(37.5, 127.0, 800, null, 100);

        verify(aiSummaryService, never()).summarize(anyLong());
    }

    @Test
    @DisplayName("bounds 검색은 AiSummaryService를 호출하지 않는다(마커 조회는 AI 비용을 지지 않는다)")
    void searchBoundsNeverCallsAiSummary() {
        when(placeRepository.findInBoundsScored(anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(), anyInt()))
                .thenReturn(List.of(view(1, null)));

        service.searchBounds(126.9, 37.4, 127.1, 37.6, null, 100);

        verify(aiSummaryService, never()).summarize(anyLong());
    }

    @Test
    @DisplayName("nearest(팬아웃) 경로는 AiSummaryService를 호출하지 않는다")
    void searchNearestNeverCallsAiSummary() {
        when(placeRepository.findNearest(anyDouble(), anyDouble(), any(), anyInt())).thenReturn(List.of());

        service.searchNearest(37.5, 127.0, null, 5);

        verify(aiSummaryService, never()).summarize(anyLong());
    }

    @Test
    @DisplayName("반경 검색은 geuneul.place.search.radius 타이머를 category 태그와 함께 정확히 1회 기록한다")
    void searchRadiusRecordsCustomTimer() {
        when(placeRepository.findWithinRadiusScored(anyDouble(), anyDouble(), anyDouble(), any(), anyInt()))
                .thenReturn(List.of(view(1, 10.0)));

        service.searchRadius(37.5, 127.0, 800, PlaceCategory.COOLING_SHELTER, 100);

        var timer = meterRegistry.find("geuneul.place.search.radius")
                .tag("category", PlaceCategory.COOLING_SHELTER.name())
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("nearest(kNN) 검색은 geuneul.place.search.nearest 타이머를 category=ALL 태그로 기록한다(카테고리 미지정)")
    void searchNearestRecordsCustomTimerWithAllTag() {
        when(placeRepository.findNearest(anyDouble(), anyDouble(), any(), anyInt())).thenReturn(List.of());

        service.searchNearest(37.5, 127.0, null, 5);

        var timer = meterRegistry.find("geuneul.place.search.nearest").tag("category", "ALL").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    private static ScoredPlaceView view(long id, Double distanceM) {
        return new ScoredPlaceView() {
            @Override public long getId() { return id; }
            @Override public String getName() { return "place-" + id; }
            @Override public String getCategory() { return PlaceCategory.COOLING_SHELTER.name(); }
            @Override public String getAddress() { return "서울 동작구 테스트로 1"; }
            @Override public double getLat() { return 37.5; }
            @Override public double getLng() { return 127.0; }
            @Override public String getSource() { return "test"; }
            @Override public Double getDistanceM() { return distanceM; }
            @Override public long getReportCount() { return 1; }
            @Override public double getFreshnessScore() { return 0.5; }
            @Override public double getComfortScore() { return 0.5; }
            @Override public double getRiskScore() { return 0.0; }
            @Override public double getFeatureComfortScore() { return 0.0; }
        };
    }
}
