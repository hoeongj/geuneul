package com.geuneul.domain.auth;

/**
 * users.trust_score(0~100) 산출 — CLAUDE.md §5 "제보는 trust_score로 가중"의 원천 계산.
 *
 * <p>DB 없이 단위테스트되는 순수 함수(SurvivalScore와 동일한 설계 방향, ADR-0007 참고) — 유저의
 * <b>검증 가능한 활동 신호</b>(제보/후기 작성 수, 계정 연령)만 쓰고, 자기신고/평판 같은 조작 가능한
 * 입력은 쓰지 않는다.
 *
 * <p><b>공식(WORKLOG 2026-07-09 근거)</b>:
 * <pre>
 * contributions = reportCount + 2·reviewCount        # 후기는 로그인 필수·영구 콘텐츠라 제보보다 고신뢰 신호
 * volumeScore   = clamp01( ln(1+contributions) / ln(1+50) )   # 로그 스케일 — 초반 기여는 빠르게, 이후 체감(폭주 억제)
 * ageScore      = clamp01( accountAgeDays / 30 )               # 가입 30일이면 연령 만점
 * trust_score   = 100 · volumeScore^0.7 · ageScore^0.3         # 곱(AND형) 결합 — 아래 "왜 곱셈인가" 참고
 * </pre>
 *
 * <p><b>왜 곱(가중기하평균)이지 가중합이 아닌가</b>: 가중합(0.7·volume + 0.3·age)이면 한쪽 성분이 0이어도
 * 다른 쪽만으로 상당한 점수가 나온다 — 예컨대 막 만든 계정이 레이트리밋 한도(분당 3·시간당 10)를 몰아써
 * 짧은 시간에 제보를 쌓으면 volumeScore가 빠르게 포화돼 age=0인데도 70점대가 나와버려 "스팸 억제"라는
 * 목적을 정면으로 비껴간다. 곱(각 성분의 지수합=1인 가중기하평균)은 <b>두 조건을 동시에</b> 요구해
 * 어느 한쪽이 0에 가까우면 전체도 0에 가깝게 끌려간다 — 위키피디아 autoconfirmed(계정연령 4일
 * <b>그리고</b> 편집 10회, 나이만으로도 활동만으로도 충족 불가)와 같은 이중 게이트 설계를 연속값으로
 * 일반화한 것. 신규 로그인 유저가 trust_score=0(익명과 동일 0.7 가중치, {@link User} 클래스 주석)에서
 * 시작해 "활동 + 시간"을 함께 쌓아야 신뢰도가 오르는 것도 이 곱 구조 덕에 자연스럽게 보장된다.
 *
 * <p>산출값은 {@link User#updateTrustScore(double)}로 저장되고, {@code place_report_signals} 뷰(V4,
 * ADR-0007)가 {@code 0.7 + 0.3·min(trust_score/100,1)}로 읽어 제보 comfort/risk 기여를 가중한다 —
 * 그 SQL 가중 공식 자체는 이미 완성돼 있어(V4) 이 클래스는 건드리지 않는다.
 */
public final class TrustScore {

    /** trust_score 하한(신규/무활동 유저 — 익명과 동일 취급). */
    public static final double MIN = 0.0;
    /** trust_score 상한(users.trust_score DOUBLE PRECISION, V2 스키마와 정합). */
    public static final double MAX = 100.0;

    static final double VOLUME_EXPONENT = 0.7;
    static final double AGE_EXPONENT = 0.3;
    static final double VOLUME_SATURATION_CONTRIBUTIONS = 50.0;
    static final double AGE_SATURATION_DAYS = 30.0;
    static final double REVIEW_CONTRIBUTION_WEIGHT = 2.0;

    private TrustScore() {
    }

    /**
     * @param reportCount     유저가 남긴 총 제보 수(만료 여부 무관 — 활동량 신호이므로 유효/만료를 가리지 않는다).
     * @param reviewCount     유저가 남긴 총 후기 수(장소당 1건 upsert 정책이라 "리뷰한 장소 수"와 같다).
     * @param accountAgeDays  가입일로부터 경과 일수(0 이상으로 클램프 — 시계 오차 등 방어).
     * @return 0~100 trust_score.
     */
    public static double calculate(long reportCount, long reviewCount, long accountAgeDays) {
        double contributions = Math.max(reportCount, 0) + REVIEW_CONTRIBUTION_WEIGHT * Math.max(reviewCount, 0);
        double volumeScore = clamp01(Math.log1p(contributions) / Math.log1p(VOLUME_SATURATION_CONTRIBUTIONS));
        double ageScore = clamp01(Math.max(accountAgeDays, 0) / AGE_SATURATION_DAYS);
        double score = MAX * Math.pow(volumeScore, VOLUME_EXPONENT) * Math.pow(ageScore, AGE_EXPONENT);
        return round2(clamp(score, MIN, MAX));
    }

    private static double clamp01(double v) {
        return clamp(v, 0.0, 1.0);
    }

    private static double clamp(double v, double min, double max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
