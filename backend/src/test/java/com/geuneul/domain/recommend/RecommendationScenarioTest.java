package com.geuneul.domain.recommend;

import com.geuneul.domain.place.SurvivalScore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 시나리오 가중치가 "의도한 랭킹"을 내는지 단위 검증(DB 불필요, ADR-0008).
 * matchScore = 시나리오 가중치로 조립한 survival_score(같은 순수 함수, 가중치만 다름).
 */
class RecommendationScenarioTest {

    private static final double RADIUS = 2_000;

    /** 시나리오 가중치로 matchScore만 뽑는 헬퍼(서비스와 동일 경로). */
    private static int match(RecommendationScenario s, Double distanceM, long count,
                             double freshness, double comfort, double risk) {
        return SurvivalScore.of(s.weights(), distanceM, RADIUS, count, freshness, comfort, risk).score();
    }

    @Test
    @DisplayName("화장실 급함: 가까운 무제보 화장실이 먼 유제보 화장실을 이긴다(distance 압도)")
    void restroomFavorsProximity() {
        int near = match(RecommendationScenario.RESTROOM, 100.0, 0, 0, 0, 0);
        int far = match(RecommendationScenario.RESTROOM, 1_500.0, 1, 1.0, 0.7, 0.0); // 먼데 깨끗 제보

        assertThat(near).isGreaterThan(far);
    }

    @Test
    @DisplayName("잠깐 쉬어갈 곳: 시원+자리 제보 > 붐빔 제보 > 무제보 (comfort 가중↑)")
    void rest30FavorsComfort() {
        int comfortable = match(RecommendationScenario.REST30, 300.0, 2, 1.0, 0.8, 0.0);
        int crowded = match(RecommendationScenario.REST30, 300.0, 1, 1.0, 0.0, 0.42);
        int unknown = match(RecommendationScenario.REST30, 300.0, 0, 0, 0, 0);

        assertThat(comfortable).isGreaterThan(crowded);
        assertThat(crowded).isGreaterThan(unknown);
    }

    @Test
    @DisplayName("비 피할 곳: 침수 제보 장소는 같은 거리의 무제보 장소보다 낮게 정렬된다(risk 페널티↑)")
    void rainDemotesFlooded() {
        int dry = match(RecommendationScenario.RAIN, 200.0, 0, 0, 0, 0);
        int flooded = match(RecommendationScenario.RAIN, 200.0, 1, 1.0, 0.0, 0.7); // 신선한 침수 제보

        assertThat(dry).isGreaterThan(flooded);
    }

    @Test
    @DisplayName("각 시나리오 카테고리 CSV는 enum name 콤마 목록 — 화장실은 TOILET 단일")
    void categoriesCsv() {
        assertThat(RecommendationScenario.RESTROOM.categoriesCsv()).isEqualTo("TOILET");
        assertThat(RecommendationScenario.RAIN.categoriesCsv().split(",")).contains("LIBRARY", "UNDERGROUND");
    }

    @Test
    @DisplayName("파라미터 매핑은 대소문자 무관 — rest30/RESTROOM 모두 인식")
    void fromParamCaseInsensitive() {
        assertThat(RecommendationScenario.fromParam("rest30")).isEqualTo(RecommendationScenario.REST30);
        assertThat(RecommendationScenario.fromParam("RESTROOM")).isEqualTo(RecommendationScenario.RESTROOM);
    }

    @Test
    @DisplayName("미지원 시나리오는 400(ResponseStatusException)")
    void fromParamRejectsUnknown() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> RecommendationScenario.fromParam("nope"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
}
