package com.geuneul.domain.ingest.storeapi;

import com.geuneul.domain.place.PlaceCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StoreCategoryMapperTest {

    @Test
    @DisplayName("독서실/스터디카페/스터디룸 키워드는 STUDY_CAFE로 분류된다")
    void classifiesStudyCafeKeywords() {
        assertThat(StoreCategoryMapper.classify("독서실")).contains(PlaceCategory.STUDY_CAFE);
        assertThat(StoreCategoryMapper.classify("스터디카페")).contains(PlaceCategory.STUDY_CAFE);
        assertThat(StoreCategoryMapper.classify("공유오피스/스터디룸")).contains(PlaceCategory.STUDY_CAFE);
    }

    @Test
    @DisplayName("커피/카페/다방 키워드는 CAFE로 분류된다")
    void classifiesCafeKeywords() {
        assertThat(StoreCategoryMapper.classify("커피전문점/카페/다방")).contains(PlaceCategory.CAFE);
        assertThat(StoreCategoryMapper.classify("커피숍")).contains(PlaceCategory.CAFE);
    }

    @Test
    @DisplayName("무관한 업종·null·빈 문자열은 empty")
    void unrelatedCategoriesAreEmpty() {
        assertThat(StoreCategoryMapper.classify("치킨전문점")).isEmpty();
        assertThat(StoreCategoryMapper.classify(null)).isEmpty();
        assertThat(StoreCategoryMapper.classify("")).isEmpty();
    }
}
