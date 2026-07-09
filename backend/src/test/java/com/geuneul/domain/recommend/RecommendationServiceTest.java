package com.geuneul.domain.recommend;

import com.geuneul.domain.place.PlaceCategory;
import com.geuneul.domain.place.PlaceRepository;
import com.geuneul.domain.place.ScoredPlaceView;
import com.geuneul.domain.recommend.dto.RecommendationResponse;
import com.geuneul.domain.weather.WeatherService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RecommendationService 단위 테스트(Mockito, DB 불필요) — 날씨 comfort 배선(ADR-0009)의
 * N+1 금지 계약: 후보가 몇 건이든 WeatherService는 요청당 정확히 1회만 호출돼야 한다.
 * 시나리오 재랭킹 자체는 기존 RecommendationIT(실 PostGIS)가 검증.
 */
class RecommendationServiceTest {

    private final PlaceRepository placeRepository = mock(PlaceRepository.class);
    private final WeatherService weatherService = mock(WeatherService.class);
    private final RecommendationService service = new RecommendationService(placeRepository, weatherService);

    @Test
    @DisplayName("후보가 여러 건이어도 WeatherService는 요청당 1회만 호출된다")
    void recommendCallsWeatherExactlyOnce() {
        when(placeRepository.findWithinRadiusScoredByCategories(anyDouble(), anyDouble(), anyDouble(), any(), anyInt()))
                .thenReturn(List.of(view(1), view(2), view(3), view(4), view(5)));
        when(weatherService.getComfortScore(anyDouble(), anyDouble())).thenReturn(Optional.of(0.9));

        List<RecommendationResponse> result = service.recommend(
                RecommendationScenario.RESTROOM, 37.5, 127.0, 2_000, 5);

        assertThat(result).hasSize(5);
        verify(weatherService, times(1)).getComfortScore(37.5, 127.0);
    }

    @Test
    @DisplayName("날씨 조회 실패(빈 Optional)여도 예외 없이 추천 결과를 낸다(graceful degradation)")
    void weatherUnavailableStillReturnsRanking() {
        when(placeRepository.findWithinRadiusScoredByCategories(anyDouble(), anyDouble(), anyDouble(), any(), anyInt()))
                .thenReturn(List.of(view(1)));
        when(weatherService.getComfortScore(anyDouble(), anyDouble())).thenReturn(Optional.empty());

        List<RecommendationResponse> result = service.recommend(
                RecommendationScenario.REST30, 37.5, 127.0, 2_000, 5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).place().survival()).isNotNull();
    }

    private static ScoredPlaceView view(long id) {
        return new ScoredPlaceView() {
            @Override public long getId() { return id; }
            @Override public String getName() { return "place-" + id; }
            @Override public String getCategory() { return PlaceCategory.TOILET.name(); }
            @Override public String getAddress() { return "서울 동작구 테스트로 1"; }
            @Override public double getLat() { return 37.5; }
            @Override public double getLng() { return 127.0; }
            @Override public String getSource() { return "test"; }
            @Override public Double getDistanceM() { return 100.0 * id; }
            @Override public long getReportCount() { return 1; }
            @Override public double getFreshnessScore() { return 0.5; }
            @Override public double getComfortScore() { return 0.5; }
            @Override public double getRiskScore() { return 0.0; }
        };
    }
}
