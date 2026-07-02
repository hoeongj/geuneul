package com.geuneul.domain.ingest;

/**
 * 좌표는 없지만 주소가 있어 지오코딩으로 살릴 수 있는 행.
 * (2025-02 이후 공중화장실 표준데이터가 좌표 미제공 — 이 경로가 사실상 본선이다.)
 */
public record GeocodeCandidate(
        String externalId,
        String name,
        String address
) {
}
