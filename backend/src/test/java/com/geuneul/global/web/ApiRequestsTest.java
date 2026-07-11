package com.geuneul.global.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiRequestsTest {

    @Test
    @DisplayName("requireValidLatLng는 NaN/Infinity를 400으로 거부한다")
    void latLngRejectsNonFinite() {
        assertThatThrownBy(() -> ApiRequests.requireValidLatLng(Double.NaN, 127.0))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
        assertThatThrownBy(() -> ApiRequests.requireValidLatLng(37.5, Double.POSITIVE_INFINITY))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    @DisplayName("requireRadiusWithin은 Infinity를 400으로 거부한다")
    void radiusRejectsNonFinite() {
        assertThatThrownBy(() -> ApiRequests.requireRadiusWithin(Double.POSITIVE_INFINITY, 5_000))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    @DisplayName("parseBounds는 4값을 파싱하고 최대 스팬 5도를 적용한다")
    void parseBoundsValidatesSpan() {
        assertThat(ApiRequests.parseBounds("126.9,37.4,127.0,37.5"))
                .containsExactly(126.9, 37.4, 127.0, 37.5);

        assertThatThrownBy(() -> ApiRequests.parseBounds("126.0,37.0,132.0,38.0"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    @DisplayName("parseBounds는 NaN과 WGS84 범위 밖 값을 거부한다")
    void parseBoundsRejectsNonFiniteAndOutOfRange() {
        assertThatThrownBy(() -> ApiRequests.parseBounds("126.0,NaN,127.0,37.5"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
        assertThatThrownBy(() -> ApiRequests.parseBounds("126.0,37.0,181.0,37.5"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }
}
