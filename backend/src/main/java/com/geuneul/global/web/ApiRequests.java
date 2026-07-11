package com.geuneul.global.web;

import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * 컨트롤러 공통 요청 검증/정규화 — 여러 엔드포인트가 동일하게 쓰는 좌표·반경·limit 규칙을 한곳에 모은다.
 * (같은 검증이 컨트롤러마다 복붙되면 규칙이 어긋나기 쉬워 여기로 캡슐화한다.)
 * 위반은 400(BAD_REQUEST)으로 통일한다 — 그 외 도메인 예외와 구분되는 "입력 계약" 위반.
 */
public final class ApiRequests {

    private static final double MAX_BOUNDS_SPAN_DEGREES = 5.0;

    private ApiRequests() {
    }

    /** 위경도 범위 검증(WGS84). 벗어나면 400. */
    public static void requireValidLatLng(double lat, double lng) {
        if (!Double.isFinite(lat) || !Double.isFinite(lng)
                || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new ResponseStatusException(BAD_REQUEST, "좌표 범위가 잘못됐습니다 (lat -90~90, lng -180~180)");
        }
    }

    /** 반경(m)이 (0, maxMeters] 범위인지 검증. 벗어나면 400. */
    public static void requireRadiusWithin(double radiusMeters, double maxMeters) {
        if (!Double.isFinite(radiusMeters) || radiusMeters <= 0 || radiusMeters > maxMeters) {
            throw new ResponseStatusException(BAD_REQUEST, "radius는 1~" + (int) maxMeters + "m 범위여야 합니다");
        }
    }

    /** "west,south,east,north" → double[4]. WGS84 범위·순서·최대 스팬까지 검증한다. */
    public static double[] parseBounds(String bounds) {
        if (bounds == null || bounds.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "bounds 형식: west,south,east,north");
        }
        String[] parts = bounds.split(",");
        if (parts.length != 4) {
            throw new ResponseStatusException(BAD_REQUEST, "bounds 형식: west,south,east,north");
        }
        try {
            double west = Double.parseDouble(parts[0].trim());
            double south = Double.parseDouble(parts[1].trim());
            double east = Double.parseDouble(parts[2].trim());
            double north = Double.parseDouble(parts[3].trim());
            requireValidLatLng(south, west);
            requireValidLatLng(north, east);
            if (west >= east || south >= north) {
                throw new ResponseStatusException(BAD_REQUEST, "bounds가 뒤집혔습니다 (west<east, south<north)");
            }
            if (east - west > MAX_BOUNDS_SPAN_DEGREES || north - south > MAX_BOUNDS_SPAN_DEGREES) {
                throw new ResponseStatusException(BAD_REQUEST, "bounds 범위는 위도·경도 각 5도 이하여야 합니다");
            }
            return new double[]{west, south, east, north};
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(BAD_REQUEST, "bounds 숫자 파싱 실패: " + bounds);
        }
    }

    /** limit을 [1, max]로 클램프(잘못된 값에 방어적 — 400 대신 안전한 경계로 보정). */
    public static int clampLimit(int limit, int max) {
        return Math.min(Math.max(limit, 1), max);
    }
}
