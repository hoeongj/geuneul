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
}
