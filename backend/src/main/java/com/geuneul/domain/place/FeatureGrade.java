package com.geuneul.domain.place;

/**
 * place_features의 자유 문자열 value를 <b>등급화</b>하는 순수 함수(ADR-0005 §④ "살·즉효").
 * 스키마 변경 없이(value는 VARCHAR) 콘센트=개수/접근성·wifi=속도·noise_level(신설) 같은 등급 규약을 코드에 둔다.
 *
 * <p>규약:
 * <ul>
 *   <li><b>outlet</b>(콘센트): many/some/few/none, 또는 불리언 true(=있음)/false(=없음)</li>
 *   <li><b>wifi</b>: fast/ok/slow, 또는 불리언</li>
 *   <li><b>noise_level</b>(신설): quiet/moderate/loud</li>
 *   <li><b>불리언 시설</b>(air_conditioned·study_ok·quiet·seating·water·restroom·no_eyes): true/false</li>
 * </ul>
 * present=false(예: outlet=false)면 상세에서 칩을 감춘다(부재는 정보가 아니라 무표시). polarity는 UI 색/
 * 후속 comfort 정밀화의 방향 신호(POSITIVE=쾌적↑ / NEGATIVE=쾌적↓ / NEUTRAL).
 */
public record FeatureGrade(String level, String label, Polarity polarity, boolean present) {

    public enum Polarity { POSITIVE, NEGATIVE, NEUTRAL }

    /** (featureType, value) → 등급. value는 대소문자·공백 무관. 알 수 없는 조합은 원문 value를 라벨로 폴백. */
    public static FeatureGrade of(String featureType, String value) {
        String type = featureType == null ? "" : featureType.trim().toLowerCase();
        String v = value == null ? "" : value.trim().toLowerCase();

        return switch (type) {
            case "outlet" -> scale3(v, "콘센트 많음", "콘센트 있음", "콘센트 적음", "콘센트");
            case "wifi" -> scale3(v, "와이파이 빠름", "와이파이", "와이파이 느림", "와이파이");
            case "noise_level" -> noiseLevel(v);
            case "air_conditioned" -> bool(v, "냉방", Polarity.POSITIVE);
            case "study_ok" -> bool(v, "공부 가능", Polarity.POSITIVE);
            case "quiet" -> bool(v, "조용함", Polarity.POSITIVE);
            case "seating" -> bool(v, "앉을 수 있음", Polarity.POSITIVE);
            case "water" -> bool(v, "음수대", Polarity.POSITIVE);
            case "restroom" -> bool(v, "화장실", Polarity.POSITIVE);
            case "no_eyes" -> bool(v, "눈치 안 보임", Polarity.POSITIVE);
            default -> new FeatureGrade("UNKNOWN", value == null ? type : value, Polarity.NEUTRAL, isTruthy(v));
        };
    }

    /** 3단계 등급(콘센트 개수·wifi 속도 동의어 포함, +불리언 폴백). 상/중=POSITIVE, 하=NEUTRAL. */
    private static FeatureGrade scale3(String v, String manyLabel, String someLabel, String fewLabel, String baseLabel) {
        return switch (v) {
            case "many", "high", "fast", "3" -> new FeatureGrade("MANY", manyLabel, Polarity.POSITIVE, true);
            case "some", "medium", "ok", "2" -> new FeatureGrade("SOME", someLabel, Polarity.POSITIVE, true);
            case "few", "low", "slow", "1" -> new FeatureGrade("FEW", fewLabel, Polarity.NEUTRAL, true);
            case "none", "0", "false", "no" -> new FeatureGrade("NONE", baseLabel, Polarity.NEUTRAL, false);
            default -> isTruthy(v)
                    ? new FeatureGrade("SOME", someLabel, Polarity.POSITIVE, true)   // true → 있음
                    : new FeatureGrade("NONE", baseLabel, Polarity.NEUTRAL, false);
        };
    }

    /** noise_level: quiet(조용, POSITIVE) / moderate(보통, NEUTRAL) / loud(시끄러움, NEGATIVE). */
    private static FeatureGrade noiseLevel(String v) {
        return switch (v) {
            case "quiet", "low", "1" -> new FeatureGrade("QUIET", "조용함", Polarity.POSITIVE, true);
            case "loud", "high", "3" -> new FeatureGrade("LOUD", "시끄러움", Polarity.NEGATIVE, true);
            case "moderate", "medium", "2" -> new FeatureGrade("MODERATE", "소음 보통", Polarity.NEUTRAL, true);
            default -> new FeatureGrade("MODERATE", "소음 보통", Polarity.NEUTRAL, true);
        };
    }

    /** 불리언 시설: truthy면 present(해당 polarity), 아니면 미표시. */
    private static FeatureGrade bool(String v, String label, Polarity polarity) {
        boolean present = isTruthy(v);
        return new FeatureGrade(present ? "PRESENT" : "ABSENT", label, present ? polarity : Polarity.NEUTRAL, present);
    }

    private static boolean isTruthy(String v) {
        return switch (v) {
            case "true", "1", "yes", "y", "o" -> true;
            default -> false;
        };
    }
}
