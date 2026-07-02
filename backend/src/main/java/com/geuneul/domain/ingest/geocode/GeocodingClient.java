package com.geuneul.domain.ingest.geocode;

import java.util.Optional;

/**
 * 주소 → WGS84 좌표. 구현체: 카카오 로컬 API(기본). 테스트는 페이크로 대체한다.
 */
public interface GeocodingClient {

    record LatLng(double lat, double lng) {
    }

    /** @return 좌표를 못 찾으면(무효 주소 등) empty */
    Optional<LatLng> geocode(String address);
}
