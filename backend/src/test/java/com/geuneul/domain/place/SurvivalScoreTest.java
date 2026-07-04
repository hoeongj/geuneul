package com.geuneul.domain.place;

import com.geuneul.domain.place.SurvivalScore.Grade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * survival_score 조립·등급 단위 테스트 (DB 불필요) — §5 가중치 + 결측 성분 재정규화 + 3색 등급(ADR-0007).
 * 시공간 신호(freshness/comfort/risk)는 이미 계산됐다고 보고, 여기선 "가중합 + 등급" 결정 로직만 본다.
 */
class SurvivalScoreTest {

    @Test
    @DisplayName("제보가 없으면(reportCount=0) 거리 점수가 있어도 등급은 UNKNOWN(정보 부족)")
    void noReportsIsUnknown() {
        SurvivalScore s = SurvivalScore.of(100.0, 800.0, 0, 0, 0, 0);

        assertThat(s.grade()).isEqualTo(Grade.UNKNOWN);
        assertThat(s.reportCount()).isZero();
        assertThat(s.distanceScore()).isEqualTo(0.875); // 1 - 100/800
    }

    @Test
    @DisplayName("가까움 + 신선한 긍정 제보 → 만점에 가깝고 GOOD(초록)")
    void freshPositiveNearIsGood() {
        SurvivalScore s = SurvivalScore.of(0.0, 800.0, 2, 1.0, 1.0, 0.0);

        assertThat(s.score()).isEqualTo(100);
        assertThat(s.grade()).isEqualTo(Grade.GOOD);
    }

    @Test
    @DisplayName("거리 성분이 없으면(bounds/단건) 남은 가중치를 재정규화해 '장소 자체 상태'로 계산")
    void distanceAbsentRenormalizes() {
        SurvivalScore s = SurvivalScore.of(null, null, 1, 1.0, 1.0, 0.0);

        assertThat(s.distanceScore()).isNull();
        assertThat(s.score()).isEqualTo(100); // (0.2·1 + 0.2·1) / 0.4 = 1.0
        assertThat(s.grade()).isEqualTo(Grade.GOOD);
    }

    @Test
    @DisplayName("신선한 부정 제보(리스크)는 점수를 끌어내려 OKAY(노랑)")
    void freshNegativeIsOkay() {
        // bounds(거리 없음), comfort 0 · freshness 1 · risk 0.42(≈ 익명 BUG 1건)
        SurvivalScore s = SurvivalScore.of(null, null, 1, 1.0, 0.0, 0.42);

        // base = (0.2·0 + 0.2·1)/0.4 = 0.5 ; score01 = 0.5 - 0.15·0.42 = 0.437 → 44
        assertThat(s.score()).isEqualTo(44);
        assertThat(s.grade()).isEqualTo(Grade.OKAY);
    }

    @Test
    @DisplayName("리스크가 커도 점수는 0 미만으로 내려가지 않는다(clamp)")
    void scoreClampedAtZero() {
        SurvivalScore s = SurvivalScore.of(null, null, 1, 0.1, 0.0, 1.0);

        assertThat(s.score()).isZero();
        assertThat(s.grade()).isEqualTo(Grade.OKAY); // 제보는 있으니 UNKNOWN은 아님
    }

    @Test
    @DisplayName("등급 경계: 정확히 60이면 GOOD, 59면 OKAY")
    void gradeThresholdBoundary() {
        assertThat(SurvivalScore.of(null, null, 1, 0.6, 0.6, 0.0).grade()).isEqualTo(Grade.GOOD);   // 60
        assertThat(SurvivalScore.of(null, null, 1, 0.59, 0.59, 0.0).grade()).isEqualTo(Grade.OKAY); // 59
    }

    @Test
    @DisplayName("거리 점수: 중심에서 0m=1.0, 반경 끝=0, 반경 밖=0(clamp)")
    void distanceScoreNormalization() {
        assertThat(SurvivalScore.of(0.0, 800.0, 0, 0, 0, 0).distanceScore()).isEqualTo(1.0);
        assertThat(SurvivalScore.of(800.0, 800.0, 0, 0, 0, 0).distanceScore()).isEqualTo(0.0);
        assertThat(SurvivalScore.of(1000.0, 800.0, 0, 0, 0, 0).distanceScore()).isEqualTo(0.0);
        assertThat(SurvivalScore.of(400.0, 800.0, 0, 0, 0, 0).distanceScore()).isCloseTo(0.5, within(1e-9));
    }

    @Test
    @DisplayName("입력 신호가 [0,1] 밖이어도 clamp되어 안전하게 조립된다")
    void inputSignalsClamped() {
        SurvivalScore s = SurvivalScore.of(null, null, 1, 5.0, 5.0, -1.0);

        assertThat(s.freshnessScore()).isEqualTo(1.0);
        assertThat(s.comfortScore()).isEqualTo(1.0);
        assertThat(s.riskScore()).isEqualTo(0.0);
        assertThat(s.score()).isEqualTo(100);
    }
}
