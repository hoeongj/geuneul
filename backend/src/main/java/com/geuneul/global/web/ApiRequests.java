package com.geuneul.global.web;

import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * 컨트롤러 공통 요청 검증/정규화 — 여러 엔드포인트가 동일하게 쓰는 좌표·반경·limit 규칙을 한곳에 모은다.
 * (같은 검증이 컨트롤러마다 복붙되면 규칙이 어긋나기 쉬워 여기로 캡슐화한다.)
 * 위반은 400(BAD_REQUEST)으로 통일한다 — 그 외 도메인 예외와 구분되는 "입력 계약" 위반.
 */
public final class ApiRequests {

    private ApiRequests() {
    }

    /** 위경도 범위 검증(WGS84). 벗어나면 400. */
    public static void requireValidLatLng(double lat, double lng) {
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new ResponseStatusException(BAD_REQUEST, "좌표 범위가 잘못됐습니다 (lat -90~90, lng -180~180)");
        }
    }

    /** 반경(m)이 (0, maxMeters] 범위인지 검증. 벗어나면 400. */
    public static void requireRadiusWithin(double radiusMeters, double maxMeters) {
        if (radiusMeters <= 0 || radiusMeters > maxMeters) {
            throw new ResponseStatusException(BAD_REQUEST, "radius는 1~" + (int) maxMeters + "m 범위여야 합니다");
        }
    }

    /** limit을 [1, max]로 클램프(잘못된 값에 방어적 — 400 대신 안전한 경계로 보정). */
    public static int clampLimit(int limit, int max) {
        return Math.min(Math.max(limit, 1), max);
    }
}
