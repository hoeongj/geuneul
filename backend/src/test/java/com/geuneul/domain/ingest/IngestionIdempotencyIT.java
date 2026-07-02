package com.geuneul.domain.ingest;

import com.geuneul.AbstractIntegrationTest;
import com.geuneul.domain.place.PlaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 인제스천 멱등성 IT (CLAUDE.md 원칙 3) — 같은 파일을 두 번 넣어도 중복이 생기지 않아야 한다.
 * ON CONFLICT (source, source_external_id) DO UPDATE 경로를 실 PostGIS에서 검증.
 */
class IngestionIdempotencyIT extends AbstractIntegrationTest {

    @Autowired
    IngestionService ingestionService;

    @Autowired
    PlaceRepository placeRepository;

    @TempDir
    Path tempDir;

    private Path csv;

    @BeforeEach
    void setUp() throws IOException {
        placeRepository.deleteAll();
        csv = tempDir.resolve("cooling_shelter.csv");
        try (InputStream in = getClass().getResourceAsStream("/fixtures/cooling_shelter_sample.csv")) {
            Files.copy(in, csv, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Test
    @DisplayName("같은 CSV를 2회 인제스트해도 행 수가 늘지 않는다 (idempotent upsert)")
    void reIngestDoesNotDuplicate() {
        var first = ingestionService.ingestCoolingShelters(csv, StandardCharsets.UTF_8);
        long afterFirst = placeRepository.countBySource(IngestionService.SOURCE_COOLING_SHELTER);

        var second = ingestionService.ingestCoolingShelters(csv, StandardCharsets.UTF_8);
        long afterSecond = placeRepository.countBySource(IngestionService.SOURCE_COOLING_SHELTER);

        assertThat(afterFirst).isEqualTo(4);   // 유효 좌표 행만
        assertThat(afterSecond).isEqualTo(4);  // 재실행해도 그대로
        assertThat(first.skippedNoCoords()).isEqualTo(2);
        assertThat(second.upserted()).isEqualTo(4); // upsert는 수행되되(갱신) 중복은 없음
    }

    @Test
    @DisplayName("재적재 시 변경된 필드는 갱신된다 (DO UPDATE 경로)")
    void reIngestUpdatesChangedFields() throws IOException {
        ingestionService.ingestCoolingShelters(csv, StandardCharsets.UTF_8);

        // 같은 쉼터시설번호(SD-001), 이름만 개정된 2차 파일
        String updated = Files.readString(csv, StandardCharsets.UTF_8)
                .replace("상도1동 주민센터", "상도1동 주민센터(리모델링)");
        Path csvV2 = tempDir.resolve("cooling_shelter_v2.csv");
        Files.writeString(csvV2, updated, StandardCharsets.UTF_8);

        ingestionService.ingestCoolingShelters(csvV2, StandardCharsets.UTF_8);

        assertThat(placeRepository.countBySource(IngestionService.SOURCE_COOLING_SHELTER)).isEqualTo(4);
        assertThat(placeRepository.findAll())
                .extracting(p -> p.getName())
                .contains("상도1동 주민센터(리모델링)")
                .doesNotContain("상도1동 주민센터");
    }
}
