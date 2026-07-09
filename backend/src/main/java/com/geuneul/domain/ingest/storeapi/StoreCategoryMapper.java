package com.geuneul.domain.ingest.storeapi;

import com.geuneul.domain.place.PlaceCategory;

import java.util.Optional;
import java.util.Set;

/**
 * 상권업종소분류명(indsSclsNm) → PlaceCategory 매핑(ADR-0006 §2). 업종코드(indsSclsCd) 기반 매핑표
 * (data.go.kr 15067631, 2023 업종분류 개편)가 아직 실측 확정되지 않아, 코드가 아니라 <b>분류명 텍스트
 * 매칭</b>을 1차 판별로 쓴다 — 코드가 확정되면 이 클래스만 코드 매칭으로 교체하면 된다(호출부 무영향).
 * 두 키워드 집합에 모두 안 걸리는 상가(치킨집·편의점 등)는 이번 커버리지 확장 대상이 아니라 empty.
 */
public final class StoreCategoryMapper {

    private static final Set<String> STUDY_CAFE_KEYWORDS = Set.of("독서실", "스터디카페", "스터디룸");
    private static final Set<String> CAFE_KEYWORDS = Set.of("커피", "카페", "다방");

    private StoreCategoryMapper() {
    }

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
