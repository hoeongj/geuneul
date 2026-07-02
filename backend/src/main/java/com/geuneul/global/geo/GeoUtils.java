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
    private static final double EARTH_RADIUS_M = 6_371_000d;

    private GeoUtils() {
    }

    public static Point point(double lat, double lng) {
        return FACTORY.createPoint(new Coordinate(lng, lat));
    }

    /**
     * 하버사인 근사 거리(m). 응답 표시용 — 검색·정렬은 DB(geography)가 담당하고
     * 여기서는 이미 선별된 결과의 표기값만 계산한다(이중계산 방지 목적의 역할 분리).
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
