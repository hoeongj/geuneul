package com.geuneul.domain.weather;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 기상청 격자 변환 검증 — 기상청 문서에 공개된 기준 좌표로 DFS_XY_CONV 공식을 확증한다.
 * 이 변환이 틀리면 엉뚱한 격자의 날씨를 붙이게 되므로(조용한 오답) 값 자체를 못 박아 둔다.
 */
class KmaGridTest {

    @Test
    @DisplayName("서울시청(37.5665, 126.9780) → 격자 (60, 127)")
    void seoulCityHall() {
        KmaGrid.Grid grid = KmaGrid.toGrid(37.5665, 126.9780);
        assertThat(grid.nx()).isEqualTo(60);
        assertThat(grid.ny()).isEqualTo(127);
    }

    @Test
    @DisplayName("동작구 상도동(37.5030, 126.9480) → 서울 격자 (59, 125) 부근")
    void sangdo() {
        // 필드테스트 거점(상도·노량진). 격자가 서울 도심권(59~60, 125~127)에 떨어지는지만 확증.
        KmaGrid.Grid grid = KmaGrid.toGrid(37.5030, 126.9480);
        assertThat(grid.nx()).isBetween(58, 60);
        assertThat(grid.ny()).isBetween(124, 126);
    }

    @Test
    @DisplayName("부산시청(35.1796, 129.0756) → 격자 (98, 76)")
    void busanCityHall() {
        KmaGrid.Grid grid = KmaGrid.toGrid(35.1796, 129.0756);
        assertThat(grid.nx()).isEqualTo(98);
        assertThat(grid.ny()).isEqualTo(76);
    }
}
