package com.geuneul.domain.ingest;

import com.geuneul.domain.ingest.openapi.PublicLibraryIngestionService;
import com.geuneul.domain.ingest.safetydata.ShelterIngestionService;
import com.geuneul.domain.ingest.storeapi.StoreIngestionService;
import com.geuneul.domain.place.PlaceCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IngestRunResultTest {

    @Test
    @DisplayName("CSV 요약의 skip/geocode 실패를 부분 성공으로 정규화한다")
    void normalizesCsvSummary() {
        var summary = new IngestionService.IngestSummary(
                "public_toilet_std", 100, 90, 2, 5, 1, 3, 4, 6, 800);

        IngestRunResult result = IngestRunResult.from(summary);

        assertThat(result.expectedRecords()).isEqualTo(100);
        assertThat(result.backfilledRecords()).isEqualTo(6);
        assertThat(result.partial()).isTrue();
        assertThat(result.incompleteRecords()).isZero();
    }

    @Test
    @DisplayName("도서관 요약은 완전 수집으로 정규화한다")
    void normalizesLibrarySummary() {
        var summary = new PublicLibraryIngestionService.LibraryIngestSummary(
                30, 30, true, 2, 29, 0, 1, 0, 0, 2, 7);

        IngestRunResult result = IngestRunResult.from(summary);

        assertThat(result.totalRecords()).isEqualTo(30);
        assertThat(result.expectedRecords()).isEqualTo(30);
        assertThat(result.deactivatedRecords()).isEqualTo(2);
        assertThat(result.partial()).isFalse();
    }

    @Test
    @DisplayName("식별 불가능 도서관 행은 skip과 불완전 source로 원장에 전달한다")
    void normalizesIncompleteLibrarySummary() {
        var summary = new PublicLibraryIngestionService.LibraryIngestSummary(
                30, 30, false, 2, 29, 1, 1, 0, 0, 0, 7);

        IngestRunResult result = IngestRunResult.from(summary);

        assertThat(result.skippedRecords()).isEqualTo(1);
        assertThat(result.partial()).isTrue();
        assertThat(result.incompleteRecords()).isEqualTo(1);
    }

    @Test
    @DisplayName("쉼터 전량 수집 미완료 건수는 reportedTotal과 fetched 차이로 계산한다")
    void normalizesIncompleteShelterSummary() {
        var summary = new ShelterIngestionService.ShelterIngestSummary(
                90, 100, false, 80, 1, 5, 2, 3, 0, 4);

        IngestRunResult result = IngestRunResult.from(summary);

        assertThat(result.expectedRecords()).isEqualTo(100);
        assertThat(result.skippedRecords()).isEqualTo(1);
        assertThat(result.partial()).isTrue();
        assertThat(result.incompleteRecords()).isEqualTo(10);
    }

    @Test
    @DisplayName("상권 요약은 미분류 건수와 feature 백필 건수를 보존한다")
    void normalizesStoreSummary() {
        var summary = new StoreIngestionService.StoreIngestSummary(
                20, 18, 17, 1, 0, 9, Map.of(PlaceCategory.CAFE, 18));

        IngestRunResult result = IngestRunResult.from(summary);

        assertThat(result.skippedRecords()).isEqualTo(2);
        assertThat(result.backfilledRecords()).isEqualTo(9);
        assertThat(result.partial()).isTrue();
    }
}
