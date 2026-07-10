package com.geuneul.global.geo;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

/**
 * 좌표 유틸. JTS 규약: X = 경도(lng), Y = 위도(lat) — 순서 실수가 흔한 버그라 여기로 캡슐화한다.
 */
public final class GeoUtils {

    public static final int SRID_WGS84 = 4326;
    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), SRID_WGS84);

    private GeoUtils() {
    }

    public static Point point(double lat, double lng) {
        return FACTORY.createPoint(new Coordinate(lng, lat));
    }

    private static final double EARTH_RADIUS_M = 6_371_000.0;

    /**
     * 두 좌표 간 대권거리(m, 하버사인 구체 근사). 정밀 표시/정렬 거리는 PostGIS ST_Distance(geography)가
     * 담당하고(타원체), 이 근사는 <b>경로 corridor 반경 산정</b> 같은 대략적 계산 전용이다(B2 라우트).
     */
    public static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS_M * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
