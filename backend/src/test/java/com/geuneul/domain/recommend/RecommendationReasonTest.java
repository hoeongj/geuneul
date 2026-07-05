package com.geuneul.domain.recommend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 추천 근거 문구 단위 테스트(순수 함수). §6 톤: "위험!"이 아니라 "주의 제보". */
class RecommendationReasonTest {

    @Test
    @DisplayName("유효 제보 0건 → '실시간 제보 없음'")
    void noReports() {
        assertThat(RecommendationReason.of(0, 0, 0)).isEqualTo("실시간 제보 없음");
    }

    @Test
    @DisplayName("긍정 우세 → '최근 좋은 제보 n건'(건수 포함)")
    void positiveDominant() {
        assertThat(RecommendationReason.of(2, 0.8, 0.1)).isEqualTo("최근 좋은 제보 2건");
    }

    @Test
    @DisplayName("리스크가 편의보다 우세하고 유의미하면 '최근 주의 제보 있음'")
    void riskDominant() {
        assertThat(RecommendationReason.of(1, 0.1, 0.5)).isEqualTo("최근 주의 제보 있음");
    }

    @Test
    @DisplayName("긍정 신호 없이 미미한 리스크(≤0.2)만 있으면 '좋은'이 아니라 중립 '최근 제보 n건'")
    void negativeOnlyWithTinyRiskIsNeutral() {
        // 부정 제보 1건만(예: 오래된 BUG) → comfort 0, risk 0.15. 주의 임계(0.2) 미만이라 경고는 아니지만
        // 긍정 신호가 없으므로 "좋은 제보"로 오표기하지 않는다.
        assertThat(RecommendationReason.of(1, 0.0, 0.15)).isEqualTo("최근 제보 1건");
    }
}
