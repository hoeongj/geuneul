package com.geuneul.domain.ingest;

/**
 * 표준데이터 CSV 1행의 파싱 결과 (좌표 유효 행만 생성된다).
 */
public record PlaceRow(
        String externalId,
        String name,
        String address,
        double lat,
        double lng
) {
}
