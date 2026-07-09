package com.geuneul.domain.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * trust_score 산출 순수 함수 단위테스트 — DB 불필요, 항상 로컬에서 돈다(TS-009 무관).
 * 공식·근거는 {@link TrustScore} 클래스 주석 + WORKLOG 2026-07-09.
 */
class TrustScoreTest {

    @Test
    @DisplayName("신규/무활동 유저는 0 — 익명과 동일한 기저(User 클래스 주석)")
    void newUserIsZero() {
        assertThat(TrustScore.calculate(0, 0, 0)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("활동 많고 계정 연령도 충분한 유저는 높은 점수(high)")
    void activeVeteranUserIsHigh() {
        double score = TrustScore.calculate(20, 5, 90);

        assertThat(score).isGreaterThan(70.0);
        assertThat(score).isLessThanOrEqualTo(TrustScore.MAX);
    }

    @Test
    @DisplayName("계정 연령이 0이면 활동량이 많아도 낮게 억제된다 (스팸 억제 — 곱 결합의 핵심 목적)")
    void freshAccountBurstIsSuppressedRegardlessOfVolume() {
        double burstOnDayZero = TrustScore.calculate(50, 0, 0);
        double burstOnDayOne = TrustScore.calculate(50, 0, 1);
        double sameVolumeAfterAMonth = TrustScore.calculate(50, 0, 30);

        assertThat(burstOnDayZero).isEqualTo(0.0); // age=0 → 곱 결합이라 volume과 무관하게 0
        assertThat(burstOnDayOne).isLessThan(40.0); // 하루 지나도 여전히 크게 억제
        assertThat(sameVolumeAfterAMonth).isGreaterThan(burstOnDayOne); // 같은 활동량이라도 시간이 지날수록 상승
    }

    @Test
    @DisplayName("활동이 전혀 없으면 계정이 오래돼도 0 — 나이만으로 신뢰를 사지 못한다 (AND형 게이트)")
    void oldButInactiveAccountIsZero() {
        assertThat(TrustScore.calculate(0, 0, 365)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("후기는 제보보다 2배 가중 — 로그인 필수·영구 콘텐츠라 더 강한 신호")
    void reviewsWeighMoreThanReports() {
        double fromReportsOnly = TrustScore.calculate(4, 0, 30);
        double fromOneReviewInstead = TrustScore.calculate(2, 1, 30); // 2 reports ≈ 1 review (2x 가중)

        assertThat(fromReportsOnly).isCloseTo(fromOneReviewInstead, within(0.01));
    }

    @Test
    @DisplayName("점수는 0~100 범위를 벗어나지 않는다 (극단 입력 방어)")
    void staysWithinBounds() {
        assertThat(TrustScore.calculate(100_000, 100_000, 100_000))
                .isLessThanOrEqualTo(TrustScore.MAX)
                .isGreaterThanOrEqualTo(TrustScore.MIN);
        assertThat(TrustScore.calculate(-5, -5, -5)).isEqualTo(0.0); // 음수 입력 방어(이론상 불가하지만)
    }

    @Test
    @DisplayName("활동량이 늘수록 점수는 단조 비감소한다 (같은 연령 기준)")
    void isMonotonicInVolume() {
        double low = TrustScore.calculate(1, 0, 30);
        double mid = TrustScore.calculate(5, 0, 30);
        double high = TrustScore.calculate(20, 0, 30);

        assertThat(mid).isGreaterThan(low);
        assertThat(high).isGreaterThan(mid);
    }
}
