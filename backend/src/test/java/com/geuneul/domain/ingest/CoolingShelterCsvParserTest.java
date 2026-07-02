package com.geuneul.domain.ingest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 파서 단위 테스트 — DB/Spring 불필요, 오프라인에서도 항상 실행된다.
 */
class CoolingShelterCsvParserTest {

    private final CoolingShelterCsvParser parser = new CoolingShelterCsvParser();

    private Reader fixture() {
        return new InputStreamReader(
                getClass().getResourceAsStream("/fixtures/cooling_shelter_sample.csv"),
                StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("유효 좌표 행만 파싱하고, 좌표 결측/국내범위 밖 행은 skipped로 계수한다")
    void parsesValidRowsAndCountsSkipped() throws IOException {
        var result = parser.parse(fixture());

        assertThat(result.totalRecords()).isEqualTo(6);
        assertThat(result.rows()).hasSize(4);              // SD-001~004
        assertThat(result.skippedNoCoords()).isEqualTo(2); // 좌표 없음 1 + 범위 밖 1
    }

    @Test
    @DisplayName("쉼터시설번호를 source_external_id로 사용한다 (멱등 upsert 자연키)")
    void usesFacilityNumberAsExternalId() throws IOException {
        var rows = parser.parse(fixture()).rows();

        assertThat(rows.get(0).externalId()).isEqualTo("SD-001");
        assertThat(rows.get(0).name()).isEqualTo("상도1동 주민센터");
        assertThat(rows.get(0).lat()).isEqualTo(37.4986);
        assertThat(rows.get(0).lng()).isEqualTo(126.9531);
    }

    @Test
    @DisplayName("고유번호가 없으면 sha256(name|address) 대체키를 생성한다 — 같은 입력은 같은 키")
    void fallbackIdIsDeterministic() {
        String a = CoolingShelterCsvParser.fallbackId("쉼터", "서울 어딘가 1");
        String b = CoolingShelterCsvParser.fallbackId("쉼터", "서울 어딘가 1");
        String c = CoolingShelterCsvParser.fallbackId("쉼터", "서울 어딘가 2");

        assertThat(a).isEqualTo(b).startsWith("h:");
        assertThat(a).isNotEqualTo(c);
    }
}
