package com.geuneul.domain.ingest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 범용 파서 단위 테스트 — 소스별 스펙(무더위쉼터/공중화장실) 둘 다 검증. DB/Spring 불필요.
 */
class StandardCsvParserTest {

    private final StandardCsvParser parser = new StandardCsvParser();

    private Reader fixture(String name) {
        return new InputStreamReader(
                getClass().getResourceAsStream("/fixtures/" + name), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("무더위쉼터: 유효 좌표 행만 파싱, 결측/범위밖은 skipped 계수")
    void parsesShelterRows() throws IOException {
        var result = parser.parse(fixture("cooling_shelter_sample.csv"), SourceSpec.COOLING_SHELTER);

        assertThat(result.totalRecords()).isEqualTo(6);
        assertThat(result.rows()).hasSize(4);
        assertThat(result.skippedNoCoords()).isEqualTo(2);
        assertThat(result.rows().get(0).externalId()).isEqualTo("SD-001");
    }

    @Test
    @DisplayName("공중화장실: WGS84위도/경도 별칭 컬럼을 찾아 파싱한다")
    void parsesToiletRowsWithWgs84Aliases() throws IOException {
        var result = parser.parse(fixture("public_toilet_sample.csv"), SourceSpec.PUBLIC_TOILET);

        assertThat(result.totalRecords()).isEqualTo(4);
        assertThat(result.rows()).hasSize(3);          // TL-004는 좌표 결측
        assertThat(result.skippedNoCoords()).isEqualTo(1);
        assertThat(result.rows().get(0).name()).isEqualTo("사육신공원 공중화장실");
        assertThat(result.rows().get(0).lat()).isEqualTo(37.5147);
        assertThat(result.rows().get(0).lng()).isEqualTo(126.9364);
    }

    @Test
    @DisplayName("고유번호가 없으면 sha256(name|address) 대체키 — 같은 입력은 같은 키")
    void fallbackIdIsDeterministic() {
        String a = StandardCsvParser.fallbackId("쉼터", "서울 어딘가 1");
        String b = StandardCsvParser.fallbackId("쉼터", "서울 어딘가 1");
        String c = StandardCsvParser.fallbackId("쉼터", "서울 어딘가 2");

        assertThat(a).isEqualTo(b).startsWith("h:");
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    @DisplayName("CLI 이름으로 SourceSpec을 찾고, 모르는 이름은 지원 목록과 함께 거부한다")
    void resolvesSpecByCliName() {
        assertThat(SourceSpec.fromCliName("public_toilet")).isEqualTo(SourceSpec.PUBLIC_TOILET);
        assertThat(SourceSpec.fromCliName("cooling_shelter")).isEqualTo(SourceSpec.COOLING_SHELTER);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> SourceSpec.fromCliName("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("public_toilet");
    }
}
