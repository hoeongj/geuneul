package com.geuneul.domain.place;

/**
 * survival_score("지금 갈만함 점수") — CLAUDE.md §5 공식의 최종 조립 (ADR-0007).
 *
 * <p>시공간 신호(거리·최근성·편의·리스크)는 DB(PostGIS/SQL, place_report_signals 뷰 + ST_Distance)가 계산하고,
 * 이 순수 값 객체가 <b>문서화된 가중치로 조립 + 등급화</b>한다 — DB 없이 단위테스트되는 결정 로직.
 *
 * <pre>
 * §5 원 공식:
 *   score = 0.25·distance + 0.20·open_now + 0.20·comfort + 0.20·freshness − 0.15·risk
 *   freshness 버킷: 0~1h=1.0 | 1~3h=0.8 | 오늘=0.6 | 이번주=0.3 | 그 외=0.1
 * </pre>
 *
 * <p><b>결측 성분 재정규화</b>(이 프로젝트의 실제 데이터에 맞춘 조정, ADR-0007):
 * <ul>
 *   <li><b>open_now</b>는 공공데이터의 운영시간(open_hours_json)이 사실상 전부 결측이라 신호로 쓰지 않는다.
 *       가짜 값을 넣지 않고 성분에서 제외(가중치 재정규화)한다. 데이터가 붙으면 additive하게 복원.</li>
 *   <li><b>distance</b>는 중심점이 있는 반경 검색에서만 반영한다. bounds(마커)·단건은 거리 성분을 빼고
 *       나머지(comfort/freshness) 가중치를 재정규화 → "장소 자체가 지금 좋은가"를 뜻한다.</li>
 * </ul>
 * 즉 base = Σ(wᵢ·sᵢ)/Σ(wᵢ)  (가용 긍정 성분의 가중평균, [0,1]) 이고,
 * score = clamp(base − 0.15·risk, 0, 1) × 100.
 *
 * <p><b>등급(마커 3색)</b>: 유효 제보가 하나도 없으면 UNKNOWN(회색·정보 부족) — 거리 점수가 있어도 "지금 상태"는 모른다.
 * 제보가 있으면 score ≥ 60 GOOD(초록·지금 좋음) / 그 외 OKAY(노랑·보통).
 *
 * <p><b>날씨 comfort 보정</b>(P3 날씨 2부, ADR-0009): §5 재정규화로 제외됐던 기온 신호를 <b>comfort_score
 * 안의 한 성분으로 additive 복원</b>한다 — §5 가중치 구조(distance/comfort/freshness/risk)는 그대로 두고,
 * comfort_score 자체를 "제보 comfort(0.6) + 날씨 comfort(0.4)"의 가중평균으로 바꾼다. 제보를 주로 삼는 이유는
 * UGC가 "이 장소 자체"에 대한 증거(에어컨·그늘 등)인 반면 날씨는 주변 대기 상태라는 보조 신호이기 때문.
 * 날씨 신호가 없으면(호출부가 weatherComfort=null 전달) 기존처럼 제보 comfort만 쓴다(폴백, 이전 동작과 동일).
 * 등급(UNKNOWN)은 여전히 reportCount로만 결정된다 — 날씨가 좋다고 "제보 없음"이 GOOD/OKAY로 바뀌지 않는다.
 */
public record SurvivalScore(
        int score,
        Grade grade,
        Double distanceScore,
        double comfortScore,
        double freshnessScore,
        double riskScore,
        long reportCount
) {

    public enum Grade { GOOD, OKAY, UNKNOWN }

    static final double W_DISTANCE = 0.25;
    static final double W_COMFORT = 0.20;
    static final double W_FRESHNESS = 0.20;
    static final double W_RISK = 0.15;
    static final int GOOD_THRESHOLD = 60;

    /** comfort_score 내부 서브 가중치(ADR-0009) — 제보(장소 자체 증거)가 날씨(주변 대기)보다 우선. */
    static final double W_REPORT_COMFORT = 0.6;
    static final double W_WEATHER_COMFORT = 0.4;

    /** §5 표준 가중치 — survival_score(지도 배지)의 기본 프로파일. */
    public static final Weights DEFAULT_WEIGHTS = new Weights(W_DISTANCE, W_COMFORT, W_FRESHNESS, W_RISK);

    /**
     * 성분 가중치 프로파일. survival_score(지도 배지)는 §5 표준({@link #DEFAULT_WEIGHTS})을 쓰고,
     * 추천(/recommendations)은 <b>같은 조립식을 재사용</b>하되 이 가중치만 시나리오별로 바꾼다(ADR-0008).
     *
     * <p>distance·comfort·freshness는 "가용 긍정 성분의 가중평균(base)"을 이루고,
     * risk는 그 base에서 빼는 감점 계수다(성분이 아니라 페널티라 가중평균 분모에 안 들어간다).
     */
    public record Weights(double distance, double comfort, double freshness, double risk) {}

    /**
     * §5 표준 가중치로 점수·등급을 조립한다(지도 배지 경로). 날씨 신호 없음(기존 동작과 동일, 폴백).
     *
     * @param distanceM 검색 중심으로부터의 거리(m). null이면 거리 성분 제외(bounds/단건).
     * @param radiusM   거리 정규화 기준 반경(m). distanceM이 있을 때만 유효(양수).
     * @param reportCount 유효 제보 수(0이면 UNKNOWN).
     * @param freshness 최근성 점수 [0,1].
     * @param comfort   편의 점수 [0,1](제보 기반).
     * @param risk      리스크 점수 [0,1].
     */
    public static SurvivalScore of(Double distanceM, Double radiusM, long reportCount,
                                   double freshness, double comfort, double risk) {
        return of(DEFAULT_WEIGHTS, distanceM, radiusM, reportCount, freshness, comfort, risk, null);
    }

    /**
     * §5 표준 가중치로 점수·등급을 조립하되, 날씨 comfort 보정을 additive로 더한다(ADR-0009).
     *
     * @param weatherComfort 날씨 기반 comfort 보정[0,1] (예: {@code HeatComfort.comfortScore}).
     *                       null이면 날씨 신호 없음 — 제보 comfort만으로 폴백(기존 동작과 동일).
     */
    public static SurvivalScore of(Double distanceM, Double radiusM, long reportCount,
                                   double freshness, double comfort, double risk, Double weatherComfort) {
        return of(DEFAULT_WEIGHTS, distanceM, radiusM, reportCount, freshness, comfort, risk, weatherComfort);
    }

    /**
     * 주어진 가중치 프로파일로 점수·등급을 조립한다 — 추천(시나리오 가중)이 이 오버로드를 쓴다(ADR-0008).
     * 조립식은 §5 표준 경로와 동일하고 가중치만 다르다: {@code base = Σ(wᵢ·sᵢ)/Σ(wᵢ)},
     * {@code score = clamp(base − w_risk·risk, 0, 1)×100}. 등급 임계(GOOD≥60)와 UNKNOWN 규칙도 공유한다.
     * 날씨 신호 없음(기존 동작과 동일, 폴백).
     */
    public static SurvivalScore of(Weights w, Double distanceM, Double radiusM, long reportCount,
                                   double freshness, double comfort, double risk) {
        return of(w, distanceM, radiusM, reportCount, freshness, comfort, risk, null);
    }

    /**
     * 시나리오 가중치 + 날씨 comfort 보정(ADR-0009)을 함께 적용하는 완전 조립 경로.
     * 다른 모든 {@code of} 오버로드가 최종적으로 이 메서드로 수렴한다.
     *
     * @param weatherComfort 날씨 기반 comfort 보정[0,1]. null이면 제보 comfort만 쓴다(폴백).
     */
    public static SurvivalScore of(Weights w, Double distanceM, Double radiusM, long reportCount,
                                   double freshness, double comfort, double risk, Double weatherComfort) {
        double f = clamp01(freshness);
        double reportComfort = clamp01(comfort);
        double r = clamp01(risk);
        double c = effectiveComfort(reportComfort, weatherComfort);

        Double distanceScore = distanceScore(distanceM, radiusM);

        double weightedSum = w.comfort() * c + w.freshness() * f;
        double weightTotal = w.comfort() + w.freshness();
        if (distanceScore != null) {
            weightedSum += w.distance() * distanceScore;
            weightTotal += w.distance();
        }
        double base = weightTotal > 0 ? weightedSum / weightTotal : 0; // 가용 긍정 성분의 가중평균 [0,1]
        double score01 = clamp01(base - w.risk() * r);
        int score = (int) Math.round(score01 * 100);

        Grade grade = reportCount <= 0 ? Grade.UNKNOWN
                : (score >= GOOD_THRESHOLD ? Grade.GOOD : Grade.OKAY);

        return new SurvivalScore(score, grade, distanceScore, c, f, r, reportCount);
    }

    /**
     * comfort_score 조립(ADR-0009): 날씨 신호가 있으면 제보 comfort(0.6)·날씨 comfort(0.4)의 가중평균,
     * 없으면 제보 comfort 그대로(폴백 — 이전 동작과 100% 동일해 기존 호출부·테스트가 깨지지 않는다).
     */
    private static double effectiveComfort(double reportComfort, Double weatherComfort) {
        if (weatherComfort == null) {
            return reportComfort;
        }
        double wc = clamp01(weatherComfort);
        return clamp01((W_REPORT_COMFORT * reportComfort + W_WEATHER_COMFORT * wc)
                / (W_REPORT_COMFORT + W_WEATHER_COMFORT));
    }

    /** 거리 점수: 가까울수록 1, 반경 끝에서 0. 중심점(반경 검색)이 있을 때만. */
    private static Double distanceScore(Double distanceM, Double radiusM) {
        if (distanceM == null || radiusM == null || radiusM <= 0) {
            return null;
        }
        return clamp01(1.0 - distanceM / radiusM);
    }

    private static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }
}
