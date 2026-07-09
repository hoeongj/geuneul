package com.geuneul.domain.report.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 혼잡 등급 유도(순수 함수) 단위테스트 — CROWDED/SEAT_OK 상대 비율 → BUSY/MODERATE/QUIET/UNKNOWN.
 */
class PopularTimesSlotTest {

    @Test
    @DisplayName("CROWDED 우세면 BUSY (crowdScore ≥ +1/3)")
    void busyWhenCrowded() {
        assertThat(PopularTimesSlot.level(8, 2)).isEqualTo("BUSY");   // (8-2)/10 = 0.6
        assertThat(PopularTimesSlot.level(2, 1)).isEqualTo("BUSY");   // (2-1)/3 ≈ 0.33 → 경계 포함
    }

    @Test
    @DisplayName("SEAT_OK 우세면 QUIET (crowdScore ≤ −1/3)")
    void quietWhenSeatsOpen() {
        assertThat(PopularTimesSlot.level(2, 8)).isEqualTo("QUIET");
        assertThat(PopularTimesSlot.level(0, 5)).isEqualTo("QUIET");
    }

    @Test
    @DisplayName("팽팽하면 MODERATE")
    void moderateWhenBalanced() {
        assertThat(PopularTimesSlot.level(5, 5)).isEqualTo("MODERATE");   // 0
        assertThat(PopularTimesSlot.level(3, 4)).isEqualTo("MODERATE");   // ≈ -0.14
    }

    @Test
    @DisplayName("혼잡 신호가 아예 없으면(둘 다 0) UNKNOWN")
    void unknownWhenNoCrowdSignal() {
        assertThat(PopularTimesSlot.level(0, 0)).isEqualTo("UNKNOWN");
    }
}
