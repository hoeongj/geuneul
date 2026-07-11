package com.geuneul.domain.weather;

/**
 * 기온(체감) → survival_score comfort 신호[0,1] 매핑 (P3 날씨 2부, ADR-0009).
 *
 * <p><b>체감온도 공식</b>: 기상청이 2022-06-02부터 쓰는 여름철 체감온도 공식(공공데이터포털
 * "기상청_체감온도(여름철)")을 그대로 쓴다. 습구온도(Tw)는 Stull(2011) 근사식으로 기온·습도만으로 계산한다
 * (별도 관측장비 불필요 — 우리 {@link Weather}에 이미 있는 T1H·REH만으로 충분):
 * <pre>
 *   Tw = Ta·atan[0.151977·(RH+8.313659)^0.5] + atan(Ta+RH) - atan(RH-1.67633)
 *        + 0.00391838·RH^1.5·atan(0.023101·RH) - 4.686035
 *   체감온도 = -0.2442 + 0.55399·Tw + 0.45535·Ta - 0.0022·Tw² + 0.00278·Tw·Ta + 3.0
 * </pre>
 * 공식은 기온 20℃ 이상 구간에서 검증됐으므로, 20℃ 미만이거나 습도 결측이면 원 기온을 그대로 쓴다
 * (어차피 25℃ 이하는 {@link #mapTemperature} 매핑에서 쾌적 상한 1.0으로 고정돼 정밀도가 결과를 안 바꾼다).
 *
 * <p><b>comfort 매핑 앵커</b>: 2026-07 확인한 기상청 폭염특보 체감온도 임계값에 그대로 앵커했다(추측값 대신
 * 공식 기준선 사용 — docs/SPEC.md §0-B 방어 가능성 원칙):
 * <ul>
 *   <li>≤25℃ 쾌적 → comfort 1.0</li>
 *   <li>33℃ 폭염주의보(이틀 이상 예상 시 발효 기준) → comfort 0.4</li>
 *   <li>35℃ 폭염경보 → comfort 0.2</li>
 *   <li>≥38℃ 폭염중대경보(2026 신설) → comfort 0.0(하한 고정)</li>
 * </ul>
 * 구간 사이는 선형보간. 강수 중이면(PTY) 이동 중 불쾌감을 반영해 균일 −0.15 페널티를 얹는다 —
 * 장소별 실내/실외 구분(place_features)이 아직 없어 "실내 선호 가중"까지는 이번 스코프에서 하지 않고,
 * 전역 소폭 조정으로만 반영한다(ADR-0009 확장점).
 */
public final class HeatComfort {

    private static final double COMFORT_CEILING_C = 25.0;   // 쾌적 상한 — 이하는 comfort 1.0
    private static final double WARNING_C = 33.0;            // 폭염주의보
    private static final double WATCH_C = 35.0;              // 폭염경보
    private static final double EMERGENCY_C = 38.0;          // 폭염중대경보(2026 신설)
    private static final double RAIN_PENALTY = 0.15;
    private static final double FORMULA_MIN_TA_C = 20.0;     // 공식 검증 구간 하한

    private HeatComfort() {
    }

    /**
     * 날씨 관측값 → comfort 성분[0,1]. 기온 결측(관측 실패·키 미설정 등)이면 null(호출부가 report-only로 폴백).
     */
    public static Double comfortScore(Weather weather) {
        if (weather == null || weather.temperatureC() == null) {
            return null;
        }
        double feelsLike = feelsLikeC(weather.temperatureC(), weather.humidityPct());
        double comfort = mapTemperature(feelsLike);
        if (weather.precipitation() != null && weather.precipitation().isPrecipitating()) {
            comfort = clamp01(comfort - RAIN_PENALTY);
        }
        return comfort;
    }

    /**
     * 체감온도(℃) — 관측값에서 계산. 기온 결측이면 null(폭염 판정 불가 → 호출부는 skip).
     * HEAT_ESCAPE 알림 판정·문구용 공개 진입점(ADR-0020).
     */
    public static Double feelsLike(Weather weather) {
        if (weather == null || weather.temperatureC() == null) {
            return null;
        }
        return feelsLikeC(weather.temperatureC(), weather.humidityPct());
    }

    /**
     * 폭염주의보(체감온도 ≥ 33℃) 여부 — HEAT_ESCAPE 알림 트리거(ADR-0020). 기온 결측이면 false(오탐 방지).
     * 폭염특보 임계값(WARNING_C=33℃)을 comfort 매핑과 공유해 기준을 한 곳에 둔다.
     */
    public static boolean isHeatAdvisory(Weather weather) {
        Double feels = feelsLike(weather);
        return feels != null && feels >= WARNING_C;
    }

    /** 체감온도(℃) — 습도 결측이거나 공식 검증 구간(20℃) 미만이면 원 기온을 그대로 반환. */
    static double feelsLikeC(double taC, Integer rhPct) {
        if (rhPct == null || taC < FORMULA_MIN_TA_C) {
            return taC;
        }
        double rh = rhPct;
        double tw = taC * Math.atan(0.151977 * Math.sqrt(rh + 8.313659))
                + Math.atan(taC + rh)
                - Math.atan(rh - 1.67633)
                + 0.00391838 * Math.pow(rh, 1.5) * Math.atan(0.023101 * rh)
                - 4.686035;
        return -0.2442 + 0.55399 * tw + 0.45535 * taC - 0.0022 * tw * tw + 0.00278 * tw * taC + 3.0;
    }

    /** 체감온도 → comfort[0,1]. 기상청 폭염특보 임계값(33/35/38) 앵커 + 선형보간. */
    static double mapTemperature(double feelsLikeC) {
        if (feelsLikeC <= COMFORT_CEILING_C) {
            return 1.0;
        }
        if (feelsLikeC <= WARNING_C) {
            return lerp(feelsLikeC, COMFORT_CEILING_C, 1.0, WARNING_C, 0.4);
        }
        if (feelsLikeC <= WATCH_C) {
            return lerp(feelsLikeC, WARNING_C, 0.4, WATCH_C, 0.2);
        }
        if (feelsLikeC <= EMERGENCY_C) {
            return lerp(feelsLikeC, WATCH_C, 0.2, EMERGENCY_C, 0.0);
        }
        return 0.0;
    }

    private static double lerp(double x, double x0, double y0, double x1, double y1) {
        return y0 + (y1 - y0) * (x - x0) / (x1 - x0);
    }

    private static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }
}
