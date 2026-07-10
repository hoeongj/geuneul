package com.geuneul.domain.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 지정 장소 검색 결과 1건(N5) — 카카오 로컬 키워드 검색 POI를 우리 계약으로 축약한 것.
 * 우리 places(DB)가 아니라 지도를 이동시킬 목적지 좌표다(선택 시 recenter). x=경도, y=위도(카카오).
 */
@Schema(description = "지정 장소 검색 결과(카카오 키워드 POI)")
public record PlaceSearchResult(
        @Schema(description = "장소명", example = "강남역 2호선") String name,
        @Schema(description = "지번 주소", nullable = true, example = "서울 강남구 역삼동 858") String address,
        @Schema(description = "도로명 주소", nullable = true, example = "서울 강남구 강남대로 지하 396") String roadAddress,
        @Schema(description = "위도", example = "37.498086") double lat,
        @Schema(description = "경도", example = "127.028001") double lng,
        @Schema(description = "카테고리명", nullable = true, example = "지하철역") String category
) {
}
