package com.geuneul.domain.ingest;

import com.geuneul.domain.ingest.openapi.PublicLibraryIngestionService;
import com.geuneul.domain.ingest.safetydata.ShelterIngestionService;
import com.geuneul.domain.ingest.storeapi.StoreIngestionService;

/**
 * 서로 다른 수집기의 기존 요약을 운영 원장이 공통으로 기록할 수 있게 정규화한 값 객체.
 * 상세 레코드나 주소는 담지 않아 원장이 개인정보·외부 응답의 복제 저장소가 되지 않게 한다.
 */
public record IngestRunResult(Long expectedRecords, long totalRecords, long upsertedRecords,
                              long skippedRecords, long geocodedRecords, long geocodeReusedRecords,
                              long geocodeFailedRecords, long deactivatedRecords,
                              long backfilledRecords, boolean complete) {

    public static IngestRunResult from(IngestionService.IngestSummary summary) {
        return new IngestRunResult((long) summary.totalRecords(), summary.totalRecords(), summary.upserted(),
                summary.skipped(), summary.geocoded(), summary.geocodeReused(), summary.geocodeFailed(),
                summary.deactivated(), summary.featuresBackfilled(), true);
    }

    public static IngestRunResult from(PublicLibraryIngestionService.LibraryIngestSummary summary) {
        Long expected = summary.reportedTotal() > 0 ? (long) summary.reportedTotal() : null;
        return new IngestRunResult(expected, summary.totalFetched(), summary.upserted(), summary.skipped(),
                summary.geocoded(), summary.geocodeReused(), summary.geocodeFailed(),
                summary.deactivated(), summary.featuresBackfilled(), summary.complete());
    }

    public static IngestRunResult from(ShelterIngestionService.ShelterIngestSummary summary) {
        Long expected = summary.reportedTotal() > 0 ? (long) summary.reportedTotal() : null;
        return new IngestRunResult(expected, summary.totalFetched(), summary.upserted(), summary.skipped(),
                summary.geocoded(), summary.geocodeReused(), summary.geocodeFailed(),
                summary.deactivated(), summary.airConditionedBackfilled(), summary.complete());
    }

    public static IngestRunResult from(StoreIngestionService.StoreIngestSummary summary) {
        long skipped = Math.max(0, summary.totalFetched() - summary.classified());
        return new IngestRunResult(null, summary.totalFetched(), summary.upserted(), skipped,
                summary.geocoded(), 0, summary.geocodeFailed(), 0, summary.featuresBackfilled(), true);
    }

    public boolean partial() {
        return !complete || skippedRecords > 0 || geocodeFailedRecords > 0;
    }

    public long incompleteRecords() {
        if (complete) {
            return 0;
        }
        if (expectedRecords == null) {
            return 1;
        }
        return Math.max(1, expectedRecords - totalRecords);
    }
}
