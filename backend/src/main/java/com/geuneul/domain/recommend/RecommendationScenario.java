package com.geuneul.domain.recommend;

import com.geuneul.domain.place.PlaceCategory;
import com.geuneul.domain.place.SurvivalScore;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static com.geuneul.domain.place.PlaceCategory.CAFE;
import static com.geuneul.domain.place.PlaceCategory.CIVIC;
import static com.geuneul.domain.place.PlaceCategory.COOLING_SHELTER;
import static com.geuneul.domain.place.PlaceCategory.LIBRARY;
import static com.geuneul.domain.place.PlaceCategory.PARK;
import static com.geuneul.domain.place.PlaceCategory.STUDY_CAFE;
import static com.geuneul.domain.place.PlaceCategory.TOILET;
import static com.geuneul.domain.place.PlaceCategory.UNDERGROUND;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * 추천 시나리오 (docs/SPEC.md §9 recommendations, ADR-0008).
 *
 * <p>추천 = <b>survival_score에 시나리오별 가중을 얹은 랭킹</b>이다. 각 시나리오는
 * <ul>
 *   <li><b>후보 카테고리 집합</b> — 이 상황에 갈 만한 장소 종류(예: "화장실 급함"=TOILET만),</li>
 *   <li><b>성분 가중치 프로파일</b>({@link SurvivalScore.Weights}) — 이 상황에서 무엇을 더 볼지</li>
 * </ul>
 * 를 정한다. 조립식(가중평균 base − risk 페널티)·등급 규칙은 지도 배지와 <b>동일한 순수 함수</b>를 재사용하고,
 * 오직 가중치만 바꾼다 — 스코어링 정책이 한 곳({@link SurvivalScore})에 모여 튜닝·테스트가 안전하다.
 *
 * <p><b>가중치 근거(§5 표준 0.25/0.20/0.20/−0.15 대비):</b>
 * <ul>
 *   <li><b>REST30</b>(잠깐 쉬어갈 곳): "시원하게 오래 앉을 곳"이라 comfort↑(0.35). 거리·최근성은 표준.</li>
 *   <li><b>RESTROOM</b>(화장실 급함): "급함"이라 distance가 압도(0.60) — 가까운 무제보 화장실이
 *       먼 유제보 화장실을 이기게. 편의/최근성은 곁다리(0.10).</li>
 *   <li><b>RAIN</b>(비 피할 곳): 침수·미끄럼 회피가 목적이라 risk 페널티를 강화(0.40).
 *       지도 배지는 §6("공포 조장 금지")대로 risk를 −0.15로 순화하지만, <b>비 피난 추천은 사용자가
 *       명시적으로 침수를 피하려는 상황</b>이라 랭킹에서 젖은 곳을 적극 강등한다(빨간 경고 라벨이 아니라 순위 하향).</li>
 *   <li><b>FOCUS</b>(집중해서 공부·작업): "조용히 오래 앉을 곳"이라 comfort↑(0.35) + risk 페널티 강화(0.30 —
 *       붐빔/소음/벌레를 적극 강등). 거리는 후순위(0.20 — 좋은 자리를 위해 좀 이동 감수).</li>
 *   <li><b>LONGSTAY</b>(오래 버틸 곳): 폭염 피해 장시간 체류라 comfort가 압도(0.40, 시원·자리) +
 *       risk 중시(0.30, 붐비면 오래 못 있음). 거리는 최하(0.15 — 자리 잡으면 오래 있으니 이동 감수).</li>
 * </ul>
 * FOCUS/LONGSTAY의 후보에는 CAFE·STUDY_CAFE도 넣는다 — 상권정보 활용신청 승인 후 적재되면 자동 커버되고,
 * 현재는 LIBRARY/CIVIC 등 적재된 카테고리로 동작한다(카테고리 집합은 데이터 유무와 독립적으로 "의미"를 표현).
 */
public enum RecommendationScenario {

    REST30("잠깐 쉬어갈 곳",
            new SurvivalScore.Weights(0.25, 0.35, 0.20, 0.25),
            EnumSet.of(COOLING_SHELTER, LIBRARY, UNDERGROUND, CIVIC, PARK)),

    RESTROOM("화장실 급함",
            new SurvivalScore.Weights(0.60, 0.10, 0.10, 0.15),
            EnumSet.of(TOILET)),

    RAIN("비 피할 곳",
            new SurvivalScore.Weights(0.35, 0.15, 0.15, 0.40),
            EnumSet.of(LIBRARY, UNDERGROUND, CIVIC, COOLING_SHELTER)),

    FOCUS("집중해서 공부·작업할 곳",
            new SurvivalScore.Weights(0.20, 0.35, 0.15, 0.30),
            EnumSet.of(STUDY_CAFE, CAFE, LIBRARY, CIVIC)),

    LONGSTAY("오래 버틸 곳",
            new SurvivalScore.Weights(0.15, 0.40, 0.15, 0.30),
            EnumSet.of(COOLING_SHELTER, LIBRARY, CIVIC, UNDERGROUND, CAFE, STUDY_CAFE));

    private final String label;
    private final SurvivalScore.Weights weights;
    private final Set<PlaceCategory> categories;

    RecommendationScenario(String label, SurvivalScore.Weights weights, Set<PlaceCategory> categories) {
        this.label = label;
        this.weights = weights;
        this.categories = categories;
    }

    public String label() {
        return label;
    }

    public SurvivalScore.Weights weights() {
        return weights;
    }

    /** 쿼리 IN 필터용 CSV(enum name, 예: "LIBRARY,UNDERGROUND"). */
    public String categoriesCsv() {
        return categories.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    /** API 파라미터(rest30|restroom|rain|focus|longstay, 대소문자 무관) → enum. 미지원 값은 400. */
    public static RecommendationScenario fromParam(String param) {
        try {
            return valueOf(param.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "지원하지 않는 시나리오: " + param + " (rest30 | restroom | rain | focus | longstay)");
        }
    }
}
