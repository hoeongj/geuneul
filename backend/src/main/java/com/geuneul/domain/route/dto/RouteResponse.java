package com.geuneul.domain.route.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 화장실 포함 경로 응답(B2) — 출발 → (경유 화장실) → 도착 + 폴리라인.
 * mode: "straight"(우리 직선 폴백) | "road"(외부 directions API, 키 활성화 후). 경유 화장실이 없으면 waypoint=null.
 */
@Schema(description = "화장실 포함 경로")
public record RouteResponse(
        @Schema(description = "출발") RouteStop origin,
        @Schema(description = "경유 화장실(없으면 null)", nullable = true) RouteStop waypoint,
        @Schema(description = "도착") RouteStop destination,
        @Schema(description = "경로 폴리라인(출발→경유→도착)") List<LatLng> polyline,
        @Schema(description = "폴리라인 종류", example = "straight", allowableValues = {"straight", "road"}) String mode,
        @Schema(description = "출발-도착 직선거리(m)", example = "820.0") double directDistanceM,
        @Schema(description = "화장실 경유 총거리(m 근사)", example = "910.0") double routeDistanceM
) {

    @Schema(description = "경로 지점")
    public record RouteStop(
            @Schema(description = "위도") double lat,
            @Schema(description = "경도") double lng,
            @Schema(description = "장소 ID(경유 화장실만)", nullable = true) Long placeId,
            @Schema(description = "이름(경유 화장실만)", nullable = true) String name
    ) {
        public static RouteStop coord(double lat, double lng) {
            return new RouteStop(lat, lng, null, null);
        }

        public static RouteStop place(double lat, double lng, long placeId, String name) {
            return new RouteStop(lat, lng, placeId, name);
        }
    }
}
