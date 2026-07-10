package com.geuneul.domain.place;

/**
 * 스코어드 검색 결과 투영 — 장소 좌표/거리 + survival_score 시공간 신호(place_report_signals 뷰, ADR-0007).
 *
 * <p>거리(distanceM)는 <b>중심점이 있는 반경 검색에서만</b> 채워진다(Double, nullable):
 * bounds(마커)·단건 상세는 중심점이 없어 null이고, 그 경우 점수는 "장소 자체 상태"(거리 성분 제외,
 * 나머지 가중치 재정규화)로 계산된다 — 마커 색은 "지금 이 장소가 좋은가"를 뜻하지 내 거리와 무관.
 * 반경/최근접 랭킹만 거리를 0.25 성분으로 반영한다("지금 갈만함").
 *
 * <p>신호(freshnessScore/comfortScore/riskScore)와 reportCount는 뷰가 계산한 값이며,
 * 제보가 없는 장소는 LEFT JOIN이라 쿼리에서 COALESCE(...,0)으로 0이 온다(등급 UNKNOWN=정보 부족).
 */
public interface ScoredPlaceView {
    long getId();

    String getName();

    String getCategory();

    String getAddress();

    double getLat();

    double getLng();

    String getSource();

    /** 검색 중심점으로부터의 거리(m, 타원체). 반경 검색에서만 non-null. */
    Double getDistanceM();

    /** 유효(미만료) 제보 수. 0이면 등급 UNKNOWN(정보 부족). */
    long getReportCount();

    /** 최근성 점수(0~1) — 가장 신선한 유효 제보 기준. */
    double getFreshnessScore();

    /** 편의 신호(0~1) — 긍정 제보(시원/자리/물/화장실) 누적, 신뢰도·최근성 가중. */
    double getComfortScore();

    /** 리스크 신호(0~1) — 부정 제보(더움/붐빔/벌레/냄새/침수/미끄럼) 누적, 위험도 가중. */
    double getRiskScore();

    /**
     * 시설(place_features) 기반 comfort 신호(0~1) — 정적 시설(에어컨·콘센트·좌석 등)을 confidence·polarity로
     * 집계한 값(place_feature_signals 뷰, A1/ADR-0017). 시설 없는 장소는 COALESCE(...,0)으로 0.
     * 최종 조립 시 제보 comfort를 덮지 않게 report&gt;feature 순으로 가중된다(SurvivalScore).
     */
    double getFeatureComfortScore();
}
