package com.geuneul.domain.recommend;

/**
 * 추천 근거 한 줄 — 실시간 제보 신호를 짧은 한국어로 요약한다(순수 함수, DB 불필요).
 *
 * <p>거리는 프론트가 이미 표시하므로 여기선 <b>"지금 상태"</b>만 요약한다. 판정 순서:
 * <ol>
 *   <li>유효 제보 0건 → "실시간 제보 없음"</li>
 *   <li>리스크가 편의보다 우세하고 유의미(&gt;0.2) → "최근 주의 제보 있음"(§6 "공포 조장 금지"대로 "위험!" 대신 순화)</li>
 *   <li>긍정 신호 없음(comfort=0) → "최근 제보 n건"(중립) — 부정 제보만 있는 곳을 "좋은 제보"로 오표기하지 않는다</li>
 *   <li>그 외(긍정 신호 있음) → "최근 좋은 제보 n건"</li>
 * </ol>
 * n은 유효 제보 총수다(긍정/부정 합산 — 시공간 신호는 개수가 아니라 가중합으로 굴러가므로 요약엔 총수만 노출).
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
        if (comfort <= 0) {
            return "최근 제보 " + reportCount + "건"; // 긍정 신호 없음 → "좋은"이라 하지 않는다(중립)
        }
        return "최근 좋은 제보 " + reportCount + "건";
    }
}
