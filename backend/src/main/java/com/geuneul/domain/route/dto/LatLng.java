package com.geuneul.domain.route.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 경로 폴리라인의 한 점(B2). */
@Schema(description = "좌표점")
public record LatLng(
        @Schema(description = "위도", example = "37.5140") double lat,
        @Schema(description = "경도", example = "126.9420") double lng
) {
}
