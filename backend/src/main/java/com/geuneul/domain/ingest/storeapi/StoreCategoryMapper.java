package com.geuneul.domain.ingest.storeapi;

import com.geuneul.domain.place.PlaceCategory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 상권업종 → {@link PlaceCategory} 매핑(ADR-0006 §2).
 *
 * <p><b>코드 확정(2026-07-10, TS-026)</b> — 활용신청 승인 후 실 호출로 소분류코드를 실측해,
 * 승인 전 임시로 쓰던 분류명 텍스트 매칭을 <b>코드 기반 확정 분류</b>로 승격했다:
 * <ul>
 *   <li>{@code I21201} = "카페" → {@link PlaceCategory#CAFE}</li>
 *   <li>{@code R10202} = "독서실/스터디 카페" → {@link PlaceCategory#STUDY_CAFE}</li>
 * </ul>
 * {@link #targetCodes()}는 인제스천이 서버측 {@code indsSclsCd} 필터로 <b>해당 업종만</b> 페이지네이션해
 * (전체 상가를 받아 클라이언트에서 거르지 않게) 하는 근거다 — 광화문 1.5km에 상가 6천 중 카페 수백뿐이라
 * 서버측 필터가 API 호출량을 한 자릿수 배 줄인다.
 *
 * <p>{@link #classify(String)}(분류명 기반)는 코드가 결측·예외인 레코드용 방어적 폴백으로만 남긴다.
 */
public final class StoreCategoryMapper {

    /** 소분류코드 → 카테고리 (인제스천이 이 키셋을 서버측 필터로 순회한다). */
    private static final Map<String, PlaceCategory> CODE_TO_CATEGORY;

    static {
        Map<String, PlaceCategory> m = new LinkedHashMap<>();
        m.put("R10202", PlaceCategory.STUDY_CAFE); // 독서실/스터디 카페
        m.put("I21201", PlaceCategory.CAFE);       // 카페
        CODE_TO_CATEGORY = Map.copyOf(m);
    }

    private static final Set<String> STUDY_CAFE_KEYWORDS = Set.of("독서실", "스터디");
    private static final Set<String> CAFE_KEYWORDS = Set.of("커피", "카페", "다방");

    private StoreCategoryMapper() {
    }

    /** 서버측 {@code indsSclsCd} 필터로 순회할 (코드 → 카테고리) 매핑. */
    public static Map<String, PlaceCategory> targetCodes() {
        return CODE_TO_CATEGORY;
    }

    /** 소분류코드 확정 분류(1차). 대상 외 코드는 empty. */
    public static Optional<PlaceCategory> classifyByCode(String indsSclsCd) {
        if (indsSclsCd == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(CODE_TO_CATEGORY.get(indsSclsCd.trim()));
    }

    /**
     * 분류명 기반 방어적 폴백 — 코드가 결측인 레코드에서만 쓴다. 독서실/스터디를 먼저 판별해
     * "독서실/스터디 카페"가 CAFE로 오분류되지 않게 한다(그 이름에도 "카페"가 들어 있으므로 순서 중요).
     */
    public static Optional<PlaceCategory> classify(String indsSclsNm) {
        if (indsSclsNm == null || indsSclsNm.isBlank()) {
            return Optional.empty();
        }
        if (containsAny(indsSclsNm, STUDY_CAFE_KEYWORDS)) {
            return Optional.of(PlaceCategory.STUDY_CAFE);
        }
        if (containsAny(indsSclsNm, CAFE_KEYWORDS)) {
            return Optional.of(PlaceCategory.CAFE);
        }
        return Optional.empty();
    }

    private static boolean containsAny(String text, Set<String> keywords) {
        return keywords.stream().anyMatch(text::contains);
    }
}
