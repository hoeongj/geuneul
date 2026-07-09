package com.geuneul.domain.ingest;

import com.geuneul.AbstractIntegrationTest;
import com.geuneul.domain.ingest.openapi.FakeLibraryApiConfig.FakeLibraryApiClient;
import com.geuneul.domain.ingest.openapi.LibraryPage;
import com.geuneul.domain.ingest.openapi.PublicLibraryIngestionService;
import com.geuneul.domain.ingest.openapi.PublicLibraryRecord;
import com.geuneul.domain.place.Place;
import com.geuneul.domain.place.PlaceCategory;
import com.geuneul.domain.place.PlaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-0006(공부 가능 공간 커버리지 확장) IT — 실 PostGIS로 도서관 오픈API 인제스천의
 * is_commercial·seatCo 조건부 feature 백필·soft-delete diff까지 시나리오별로 검증한다.
 * IngestionIdempotencyIT(CSV 파이프라인 멱등)와는 별도 파일 — 오픈API 파이프라인이라 소스도 다르다.
 */
class StudySpaceCoverageIT extends AbstractIntegrationTest {

    @Autowired
    PublicLibraryIngestionService libraryIngestionService;

    @Autowired
    PlaceRepository placeRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    FakeLibraryApiClient fakeLibraryApiClient;

    private static final String SSU = "숭실대학교 중앙도서관";
    private static final String SADANG = "동작구립 사당도서관";
    private static final String NODEUL = "노들서가";

    @BeforeEach
    void setUp() {
        placeRepository.deleteAll();
        fakeLibraryApiClient.reset();
    }

    private static PublicLibraryRecord rec(String name, String addr, double lat, double lng, String seatCo) {
        return new PublicLibraryRecord(name, addr, String.valueOf(lat), String.valueOf(lng), seatCo);
    }

    private void seedThreeLibraries() {
        fakeLibraryApiClient.setPage(1, new LibraryPage(List.of(
                rec(SSU, "서울 동작구 상도로 369", 37.4962, 126.9573, "800"),
                rec(SADANG, "서울 동작구 사당로 40", 37.4835, 126.9814, "300"),
                rec(NODEUL, "서울 동작구 노들로 445", 37.5178, 126.9564, "0") // 열람좌석수 0 → study_ok 백필 제외
        ), 3));
    }

    @Test
    @DisplayName("LIBRARY 오픈API 적재: is_commercial=false + seatCo>0인 도서관만 study_ok/quiet 백필")
    void libraryIngestSetsNonCommercialAndBackfillsPositiveSeatCountOnly() {
        seedThreeLibraries();

        var summary = libraryIngestionService.ingestAll(false);

        assertThat(summary.upserted()).isEqualTo(3);
        assertThat(summary.featuresBackfilled()).isEqualTo(4); // SSU+사당 2곳 x (study_ok, quiet) — 노들서가(seatCo=0) 제외

        List<Place> libraries = placeRepository.findAll().stream()
                .filter(p -> p.getSource().equals(PublicLibraryIngestionService.SOURCE_KEY))
                .toList();
        assertThat(libraries).hasSize(3);
        assertThat(libraries).allSatisfy(p -> {
            assertThat(p.getCategory()).isEqualTo(PlaceCategory.LIBRARY);
            assertThat(p.isCommercial()).isFalse();
            assertThat(p.getDeletedAt()).isNull();
        });

        Long ssuId = idOf(libraries, SSU);
        Long nodeulId = idOf(libraries, NODEUL);
        assertThat(jdbcTemplate.queryForList(
                "SELECT feature_type FROM place_features WHERE place_id = ? ORDER BY feature_type",
                String.class, ssuId)).containsExactly("quiet", "study_ok");
        assertThat(jdbcTemplate.queryForList(
                "SELECT feature_type FROM place_features WHERE place_id = ?",
                String.class, nodeulId)).isEmpty(); // seatCo=0 → 백필 대상 아님
    }

    @Test
    @DisplayName("UGC가 이미 채운 feature는 재적재해도 자동 백필이 덮어쓰지 않는다(ON CONFLICT DO NOTHING)")
    void backfillDoesNotOverwriteExistingFeature() {
        seedThreeLibraries();
        libraryIngestionService.ingestAll(false);
        Long ssuId = idOf(placeRepository.findAll(), SSU);
        jdbcTemplate.update(
                "UPDATE place_features SET value = 'false', source = 'UGC', confidence = 1.0 "
                        + "WHERE place_id = ? AND feature_type = 'study_ok'",
                ssuId);

        libraryIngestionService.ingestAll(false); // 재적재(백필 재실행)

        String value = jdbcTemplate.queryForObject(
                "SELECT value FROM place_features WHERE place_id = ? AND feature_type = 'study_ok'",
                String.class, ssuId);
        assertThat(value).isEqualTo("false"); // UGC 값 유지
    }

    @Test
    @DisplayName("deactivateStale=true: 다음 수집에서 빠진 도서관은 soft-delete되어 검색에서 제외된다")
    void deactivateStaleSoftDeletesMissingRows() {
        seedThreeLibraries();
        libraryIngestionService.ingestAll(true);
        assertThat(placeRepository.countBySource(PublicLibraryIngestionService.SOURCE_KEY)).isEqualTo(3);

        // 사당도서관이 빠진 다음 스냅샷(폐관 시나리오)
        fakeLibraryApiClient.reset();
        fakeLibraryApiClient.setPage(1, new LibraryPage(List.of(
                rec(SSU, "서울 동작구 상도로 369", 37.4962, 126.9573, "800"),
                rec(NODEUL, "서울 동작구 노들로 445", 37.5178, 126.9564, "0")
        ), 2));

        var summary = libraryIngestionService.ingestAll(true);

        assertThat(summary.deactivated()).isEqualTo(1);
        Place sadang = placeRepository.findAll().stream()
                .filter(p -> SADANG.equals(p.getName()))
                .findFirst().orElseThrow();
        assertThat(sadang.getDeletedAt()).isNotNull();

        Optional<?> scored = placeRepository.findByIdScored(sadang.getId());
        assertThat(scored).isEmpty(); // 검색 경로는 soft-deleted 장소를 반환하지 않는다
    }

    @Test
    @DisplayName("deactivateStale=false(기본): 부분 응답(페이지 1건만)이 나머지 행을 지우지 않는다")
    void deactivateStaleDefaultsToFalseProtectsPartialSnapshot() {
        seedThreeLibraries();
        libraryIngestionService.ingestAll(true);

        fakeLibraryApiClient.reset();
        fakeLibraryApiClient.setPage(1, new LibraryPage(List.of(
                rec(SSU, "서울 동작구 상도로 369", 37.4962, 126.9573, "800")
        ), 1));

        libraryIngestionService.ingestAll(false); // deactivateStale 미지정 = false

        assertThat(placeRepository.findAll().stream().filter(p -> p.getDeletedAt() != null)).isEmpty();
    }

    private static Long idOf(List<Place> places, String name) {
        return places.stream().filter(p -> p.getName().equals(name)).findFirst().orElseThrow().getId();
    }
}
