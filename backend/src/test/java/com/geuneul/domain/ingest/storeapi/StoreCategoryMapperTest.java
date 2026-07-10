package com.geuneul.domain.ingest.storeapi;

import com.geuneul.domain.place.PlaceCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StoreCategoryMapperTest {

    @Test
    @DisplayName("확정 소분류코드로 카테고리를 분류한다(I21201→CAFE, R10202→STUDY_CAFE)")
    void classifiesByConfirmedCode() {
        assertThat(StoreCategoryMapper.classifyByCode("I21201")).contains(PlaceCategory.CAFE);
        assertThat(StoreCategoryMapper.classifyByCode("R10202")).contains(PlaceCategory.STUDY_CAFE);
        assertThat(StoreCategoryMapper.classifyByCode(" R10202 ")).contains(PlaceCategory.STUDY_CAFE);
    }

    @Test
    @DisplayName("대상 외 코드·null은 empty")
    void unknownCodeIsEmpty() {
        assertThat(StoreCategoryMapper.classifyByCode("I56111")).isEmpty(); // 한식
        assertThat(StoreCategoryMapper.classifyByCode(null)).isEmpty();
    }

    @Test
    @DisplayName("targetCodes()는 서버측 필터로 순회할 두 업종코드를 담는다")
    void targetCodesHoldsBothTargets() {
        assertThat(StoreCategoryMapper.targetCodes())
                .containsEntry("I21201", PlaceCategory.CAFE)
                .containsEntry("R10202", PlaceCategory.STUDY_CAFE)
                .hasSize(2);
    }

    @Test
    @DisplayName("분류명 폴백: 독서실/스터디를 먼저 판별해 '독서실/스터디 카페'가 CAFE로 오분류되지 않는다")
    void nameFallbackPrioritizesStudy() {
        assertThat(StoreCategoryMapper.classify("독서실/스터디 카페")).contains(PlaceCategory.STUDY_CAFE);
        assertThat(StoreCategoryMapper.classify("독서실")).contains(PlaceCategory.STUDY_CAFE);
        assertThat(StoreCategoryMapper.classify("카페")).contains(PlaceCategory.CAFE);
        assertThat(StoreCategoryMapper.classify("커피숍")).contains(PlaceCategory.CAFE);
    }

    @Test
    @DisplayName("분류명 폴백: 무관 업종·null·빈 문자열은 empty")
    void nameFallbackUnrelatedIsEmpty() {
        assertThat(StoreCategoryMapper.classify("치킨전문점")).isEmpty();
        assertThat(StoreCategoryMapper.classify(null)).isEmpty();
        assertThat(StoreCategoryMapper.classify("")).isEmpty();
    }
}
