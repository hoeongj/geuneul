package com.geuneul.domain.alert.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 급증 안내 문구 매핑 단위테스트 — 표현 규율(docs/SPEC.md §6): 공포 조장 금지.
 * 위험 계열(침수·미끄럼)도 "위험!"이 아니라 순화 문구여야 한다.
 */
class SurgeInfoTest {

    @Test
    @DisplayName("침수 급증은 '위험!'이 아니라 우회 권장 톤으로 순화된다 (§6)")
    void floodMessageIsSoftened() {
        String msg = SurgeInfo.message("FLOOD", 4);
        assertThat(msg).contains("침수").contains("우회");
        assertThat(msg).doesNotContain("위험!");
    }

    @Test
    @DisplayName("미끄럼 급증도 조심 톤으로 순화된다")
    void slipperyMessageIsSoftened() {
        assertThat(SurgeInfo.message("SLIPPERY", 3)).contains("미끄럼").doesNotContain("위험!");
    }

    @Test
    @DisplayName("타입별 대표 문구가 각각 매핑된다")
    void perTypeMessages() {
        assertThat(SurgeInfo.message("BUG", 3)).contains("벌레");
        assertThat(SurgeInfo.message("COOL", 3)).contains("시원");
        assertThat(SurgeInfo.message("CROWDED", 3)).contains("붐");
        assertThat(SurgeInfo.message("SEAT_OK", 3)).contains("자리");
    }

    @Test
    @DisplayName("알 수 없는/null 타입은 일반 문구로 폴백한다")
    void unknownTypeFallsBack() {
        assertThat(SurgeInfo.message("NOPE", 3)).isEqualTo("최근 제보가 몰리고 있어요");
        assertThat(SurgeInfo.message(null, 3)).isEqualTo("최근 제보가 몰리고 있어요");
    }

    @Test
    @DisplayName("of(...)는 뷰 값을 그대로 싣고 문구를 붙인다")
    void ofBuildsMessage() {
        SurgeInfo info = SurgeInfo.of(185L, "노량진 지하보도", 37.514, 126.942, 4, "FLOOD");
        assertThat(info.placeId()).isEqualTo(185L);
        assertThat(info.reportCount()).isEqualTo(4);
        assertThat(info.topType()).isEqualTo("FLOOD");
        assertThat(info.message()).contains("침수");
    }
}
