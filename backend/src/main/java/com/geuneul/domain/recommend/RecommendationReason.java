package com.geuneul.domain.recommend;

/**
 * 추천 근거 한 줄 — 실시간 제보 신호를 짧은 한국어로 요약한다(순수 함수, DB 불필요).
 *
 * <p>거리는 프론트가 이미 표시하므로 여기선 <b>"지금 상태"</b>만 요약한다: 유효 제보 유무와,
 * 있으면 긍정/주의 방향. 리스크가 편의보다 우세하고 유의미(&gt;0.2)하면 "주의"로,
 * 아니면 "좋은 제보 n건"으로. §6("공포 조장 금지")에 맞춰 "위험!"이 아니라 "주의 제보 있음"으로 순화한다.
 */
final class RecommendationReason {

    private RecommendationReason() {
    }

    static final double RISK_NOTABLE = 0.2;

    static String of(long reportCount, double comfort, double risk) {
        if (reportCount <= 0) {
            return "실시간 제보 없음";
        }
        if (risk > comfort && risk > RISK_NOTABLE) {
            return "최근 주의 제보 있음";
        }
        return "최근 좋은 제보 " + reportCount + "건";
    }
}
