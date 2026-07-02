package com.geuneul.domain.ingest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 범용 파서 단위 테스트 — 실전에서 만난 3가지 포맷을 전부 커버한다:
 * ① data.go.kr 한글 헤더+좌표 ② 행안부 safetydata 영문코드+BOM+이중헤더 ③ 2025-02 이후 좌표 미제공(지오코딩行).
 */
class StandardCsvParserTest {

    private final StandardCsvParser parser = new StandardCsvParser();

    private Reader fixture(String name) {
        return new InputStreamReader(
                getClass().getResourceAsStream("/fixtures/" + name), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("무더위쉼터(한글 헤더): 유효좌표 행 / 좌표없음+주소있음 행(지오코딩行) 분리")
    void parsesKoreanHeaderShelter() throws IOException {
        var result = parser.parse(fixture("cooling_shelter_sample.csv"), SourceSpec.COOLING_SHELTER);

        assertThat(result.totalRecords()).isEqualTo(6);
        assertThat(result.rows()).hasSize(4);              // SD-001~004 (좌표 보유)
        assertThat(result.needGeocode()).hasSize(2);       // SD-005(결측)·SD-006(범위밖) — 주소 있음
        assertThat(result.skipped()).isZero();
        assertThat(result.rows().get(0).externalId()).isEqualTo("SD-001");
    }

    @Test
    @DisplayName("행안부 safetydata 포맷: BOM 제거 + 영문코드 헤더(LA/LO) 매칭 + 한글 라벨行 스킵")
    void parsesSafetydataEnglishHeaderWithBom() throws IOException {
        var result = parser.parse(fixture("safetydata_shelter_sample.csv"), SourceSpec.COOLING_SHELTER);

        assertThat(result.rows()).hasSize(2);
        assertThat(result.skipped()).isEqualTo(1);         // 2행(한글 라벨 에코) 차단
        assertThat(result.needGeocode()).isEmpty();
        assertThat(result.rows().get(0).externalId()).isEqualTo("4711000-0001");
        assertThat(result.rows().get(0).name()).isEqualTo("포항 쉼터1");
        assertThat(result.rows().get(0).lat()).isEqualTo(36.0190);   // LA
        assertThat(result.rows().get(0).lng()).isEqualTo(129.3650);  // LO
        assertThat(result.rows().get(0).address()).isEqualTo("경북 포항시 북구 중앙로 100"); // RN_DTL_ADRES 우선
    }

    @Test
    @DisplayName("공중화장실(구포맷, WGS84 컬럼 보유): 좌표 그대로 사용")
    void parsesToiletWithWgs84Columns() throws IOException {
        var result = parser.parse(fixture("public_toilet_sample.csv"), SourceSpec.PUBLIC_TOILET);

        assertThat(result.rows()).hasSize(3);
        assertThat(result.needGeocode()).hasSize(1);       // TL-004 좌표결측+주소있음
        assertThat(result.rows().get(0).name()).isEqualTo("사육신공원 공중화장실");
    }

    @Test
    @DisplayName("공중화장실(2025-02 이후 좌표 미제공 포맷): 좌표 컬럼이 아예 없어도 주소行을 지오코딩 대상으로 수집")
    void parsesToiletWithoutCoordColumns() throws IOException {
        var result = parser.parse(fixture("public_toilet_nocoords_sample.csv"), SourceSpec.PUBLIC_TOILET);

        assertThat(result.rows()).isEmpty();               // 좌표 보유 행 없음
        assertThat(result.needGeocode()).hasSize(2);       // 주소 있는 2행
        assertThat(result.skipped()).isEqualTo(1);         // 주소도 없는 1행
        assertThat(result.needGeocode().get(0).externalId()).isEqualTo("202530000000100840");
        assertThat(result.needGeocode().get(0).address()).isEqualTo("서울특별시 종로구 성균관로 91");
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

        assertThatThrownBy(() -> SourceSpec.fromCliName("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("public_toilet");
    }
}
