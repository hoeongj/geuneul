package com.geuneul.domain.ingest;

import com.geuneul.AbstractIntegrationTest;
import com.geuneul.domain.ingest.geocode.FakeGeocodingConfig.FakeGeocodingClient;
import com.geuneul.domain.place.PlaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 인제스천 멱등성 + 지오코딩 경로 IT (docs/SPEC.md 원칙 3, ADR-0002/0003).
 * 실 PostGIS + 페이크 지오코더로: 재적재 중복 0 / DO UPDATE 갱신 / 지오코딩 좌표 저장·재사용을 검증.
 */
class IngestionIdempotencyIT extends AbstractIntegrationTest {

    @Autowired
    IngestionService ingestionService;

    @Autowired
    PlaceRepository placeRepository;

    @Autowired
    FakeGeocodingClient fakeGeocoder;

    @Autowired
    JdbcTemplate jdbc;

    @TempDir
    Path tempDir;

    private Path shelterCsv;

    @BeforeEach
    void setUp() throws IOException {
        placeRepository.deleteAll();
        fakeGeocoder.reset();
        // SD-005("...어딘가 1")는 지오코딩 성공, SD-006("...어딘가 2")는 실패(무효 주소) 시나리오
        fakeGeocoder.willReturn("서울특별시 동작구 어딘가 1", 37.5000, 126.9500);
        shelterCsv = copyFixture("cooling_shelter_sample.csv");
    }

    private Path copyFixture(String name) throws IOException {
        Path target = tempDir.resolve(name);
        try (InputStream in = getClass().getResourceAsStream("/fixtures/" + name)) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    @Test
    @DisplayName("같은 CSV 2회 인제스트: 행 수 불변 + 지오코딩 성공분은 재호출 없이 재사용된다")
    void reIngestDoesNotDuplicateAndReusesGeocode() {
        var first = ingestionService.ingest(SourceSpec.COOLING_SHELTER, shelterCsv, StandardCharsets.UTF_8);
        long afterFirst = placeRepository.countBySource(SourceSpec.COOLING_SHELTER.sourceKey());
        int callsAfterFirst = fakeGeocoder.calls();

        var second = ingestionService.ingest(SourceSpec.COOLING_SHELTER, shelterCsv, StandardCharsets.UTF_8);
        long afterSecond = placeRepository.countBySource(SourceSpec.COOLING_SHELTER.sourceKey());

        assertThat(afterFirst).isEqualTo(5);               // 좌표보유 4 + 지오코딩 성공 1(SD-005)
        assertThat(afterSecond).isEqualTo(5);              // 재실행해도 그대로
        assertThat(first.geocoded()).isEqualTo(1);
        assertThat(first.geocodeFailed()).isEqualTo(1);    // SD-006 무효 주소
        assertThat(callsAfterFirst).isEqualTo(2);          // 후보 2건 모두 시도

        assertThat(second.geocodeReused()).isEqualTo(1);   // SD-005는 저장 좌표 재사용
        assertThat(fakeGeocoder.calls()).isEqualTo(3);     // 2 + 재시도 1(실패했던 SD-006만)
    }

    @Test
    @DisplayName("A3: 냉방기 보유(COLR_HOLD_ARCNDTN>0) 쉼터에만 air_conditioned가 조건부 백필된다")
    void backfillsAirConditionedForEquippedShelters() throws IOException {
        Path csv = tempDir.resolve("shelter_aircon.csv");
        Files.writeString(csv, """
                RSTR_FCLTY_NO,RSTR_NM,RN_DTL_ADRES,LA,LO,COLR_HOLD_ARCNDTN
                AC-2,냉방쉼터,서울 동작구 성대로 1,37.50,127.00,2
                AC-0,비냉방쉼터,서울 동작구 성대로 2,37.51,127.01,0
                """, StandardCharsets.UTF_8);

        var summary = ingestionService.ingest(SourceSpec.COOLING_SHELTER, csv, StandardCharsets.UTF_8);

        assertThat(summary.featuresBackfilled()).isEqualTo(1);   // AC-2만
        assertThat(airConditionedCount("AC-2")).isEqualTo(1);
        assertThat(airConditionedCount("AC-0")).isZero();
    }

    private int airConditionedCount(String externalId) {
        Integer n = jdbc.queryForObject("""
                SELECT count(*) FROM place_features f
                JOIN places p ON p.id = f.place_id
                WHERE p.source = ? AND p.source_external_id = ? AND f.feature_type = 'air_conditioned'
                """, Integer.class, SourceSpec.COOLING_SHELTER.sourceKey(), externalId);
        return n == null ? 0 : n;
    }

    @Test
    @DisplayName("재적재 시 변경된 필드는 갱신된다 (DO UPDATE 경로)")
    void reIngestUpdatesChangedFields() throws IOException {
        ingestionService.ingest(SourceSpec.COOLING_SHELTER, shelterCsv, StandardCharsets.UTF_8);

        String updated = Files.readString(shelterCsv, StandardCharsets.UTF_8)
                .replace("상도1동 주민센터", "상도1동 주민센터(리모델링)");
        Path csvV2 = tempDir.resolve("cooling_shelter_v2.csv");
        Files.writeString(csvV2, updated, StandardCharsets.UTF_8);

        ingestionService.ingest(SourceSpec.COOLING_SHELTER, csvV2, StandardCharsets.UTF_8);

        assertThat(placeRepository.countBySource(SourceSpec.COOLING_SHELTER.sourceKey())).isEqualTo(5);
        assertThat(placeRepository.findAll())
                .extracting(p -> p.getName())
                .contains("상도1동 주민센터(리모델링)")
                .doesNotContain("상도1동 주민센터");
    }

    @Test
    @DisplayName("좌표 미제공 포맷(2025-02 이후 화장실): 전량 지오코딩으로 적재되고 geocoded=true로 저장된다")
    void ingestsNoCoordsFormatViaGeocoding() throws IOException {
        fakeGeocoder.willReturn("서울특별시 종로구 성균관로 91", 37.5824, 126.9982);
        fakeGeocoder.willReturn("서울특별시 종로구 율곡로 99", 37.5769, 126.9903);
        Path toiletCsv = copyFixture("public_toilet_nocoords_sample.csv");

        var report = ingestionService.ingest(SourceSpec.PUBLIC_TOILET, toiletCsv, StandardCharsets.UTF_8);

        assertThat(report.geocoded()).isEqualTo(2);
        assertThat(report.skipped()).isEqualTo(1);         // 주소 없는 행
        assertThat(placeRepository.countBySource(SourceSpec.PUBLIC_TOILET.sourceKey())).isEqualTo(2);
        assertThat(placeRepository.findAll())
                .filteredOn(p -> p.getSource().equals(SourceSpec.PUBLIC_TOILET.sourceKey()))
                .allSatisfy(p -> {
                    assertThat(p.isGeocoded()).isTrue();
                    assertThat(p.getGeom()).isNotNull();
                });
    }

    @Test
    @DisplayName("서로 다른 소스(쉼터/화장실)는 자연키가 분리되어 충돌하지 않는다")
    void sourcesDoNotCollide() throws IOException {
        Path toiletCsv = copyFixture("public_toilet_sample.csv");

        ingestionService.ingest(SourceSpec.COOLING_SHELTER, shelterCsv, StandardCharsets.UTF_8);
        ingestionService.ingest(SourceSpec.PUBLIC_TOILET, toiletCsv, StandardCharsets.UTF_8);

        assertThat(placeRepository.countBySource(SourceSpec.COOLING_SHELTER.sourceKey())).isEqualTo(5);
        assertThat(placeRepository.countBySource(SourceSpec.PUBLIC_TOILET.sourceKey())).isEqualTo(3);
    }
}
