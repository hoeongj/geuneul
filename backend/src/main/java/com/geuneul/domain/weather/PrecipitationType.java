package com.geuneul.domain.weather;

/**
 * 강수 형태 — 기상청 초단기실황 PTY 코드(관측 기준)를 의미 있는 enum으로 정규화한다.
 *
 * 초단기실황(getUltraSrtNcst) PTY 코드: 0 없음 / 1 비 / 2 비·눈 / 3 눈 / 5 빗방울 / 6 빗방울·눈날림 / 7 눈날림.
 * 알 수 없는 값은 NONE으로 처리한다(방어적 — 코드 체계가 바뀌어도 점수 계산이 깨지지 않게).
 */
public enum PrecipitationType {
    NONE,
    RAIN,
    RAIN_SNOW,
    SNOW,
    DRIZZLE,
    DRIZZLE_SNOW,
    SNOW_FLURRY;

    public static PrecipitationType fromCode(String code) {
        if (code == null) {
            return NONE;
        }
        return switch (code.trim()) {
            case "1" -> RAIN;
            case "2" -> RAIN_SNOW;
            case "3" -> SNOW;
            case "5" -> DRIZZLE;
            case "6" -> DRIZZLE_SNOW;
            case "7" -> SNOW_FLURRY;
            default -> NONE; // "0" 및 미지의 코드
        };
    }

    /** 비/눈이 실제로 내리는 중인지 — 추천 rain 시나리오·요약에 쓴다. */
    public boolean isPrecipitating() {
        return this != NONE;
    }
}
