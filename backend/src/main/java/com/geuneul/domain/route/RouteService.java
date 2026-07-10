package com.geuneul.domain.route;

import com.geuneul.domain.place.PlaceRepository;
import com.geuneul.domain.route.dto.LatLng;
import com.geuneul.domain.route.dto.RouteResponse;
import com.geuneul.domain.route.dto.RouteResponse.RouteStop;
import com.geuneul.domain.route.dto.RouteResponse.ShadeSpot;
import com.geuneul.global.geo.GeoUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 화장실 포함 경로(B2, ADR-0019) — 출발→도착 사이에 <b>우회 최소 화장실</b> 1곳을 경유지로 끼우고 폴리라인을 만든다.
 * 경유지 선택은 PostGIS(우리 간판)가, 폴리라인은 {@link DirectionsProvider}(직선 MVP / 도로 API 후속)가 담당한다.
 */
@Service
@Transactional(readOnly = true)
public class RouteService {

    /** 경유지 후보를 좁히는 corridor 버퍼(m) — 직선의 절반 + 이 버퍼 반경 안에서 화장실을 찾는다. */
    static final double CORRIDOR_BUFFER_M = 1500;

    /** F4 그늘/비 경로(N8) — 경로 폴리라인에서 이 반경(m) 안의 그늘/실내를 오버레이(도보 우회 가능 범위). */
    static final double SHADE_CORRIDOR_M = 400;
    /** 오버레이 대상 그늘/실내 카테고리(enum name CSV, N8) — 쉼터·도서관·지하상가. */
    static final String SHADE_CATEGORIES = "COOLING_SHELTER,LIBRARY,UNDERGROUND";
    /** 오버레이 마커 상한(지도 혼잡 방지). */
    static final int SHADE_LIMIT = 15;

    private final PlaceRepository placeRepository;
    private final DirectionsProvider directions;

    public RouteService(PlaceRepository placeRepository, DirectionsProvider directions) {
        this.placeRepository = placeRepository;
        this.directions = directions;
    }

    public RouteResponse toiletRoute(double fromLat, double fromLng, double toLat, double toLng) {
        double directM = GeoUtils.haversineMeters(fromLat, fromLng, toLat, toLng);
        double midLat = (fromLat + toLat) / 2;
        double midLng = (fromLng + toLng) / 2;
        double corridorM = Math.max(directM / 2 + CORRIDOR_BUFFER_M, CORRIDOR_BUFFER_M);

        Optional<RouteWaypointView> best = placeRepository.findBestToiletWaypoint(
                fromLat, fromLng, toLat, toLng, midLat, midLng, corridorM);

        List<LatLng> waypoints = new ArrayList<>();
        waypoints.add(new LatLng(fromLat, fromLng));
        RouteStop waypointStop = null;
        double routeM = directM;
        if (best.isPresent()) {
            RouteWaypointView t = best.get();
            waypoints.add(new LatLng(t.getLat(), t.getLng()));
            waypointStop = RouteStop.place(t.getLat(), t.getLng(), t.getPlaceId(), t.getName());
            routeM = t.getDistFromM() + t.getDistToM(); // 화장실 들렀다 가는 총거리 근사
        }
        waypoints.add(new LatLng(toLat, toLng));

        // 도로 공급자가 있으면 도로 폴리라인, 없으면 직선(waypoints 그대로). mode로 구분.
        Optional<List<LatLng>> road = directions.polyline(waypoints);
        List<LatLng> polyline = road.orElse(waypoints);
        String mode = road.isPresent() ? directions.mode() : "straight";

        // F4(N8): 경로 corridor 주변 그늘/실내 피난처를 오버레이로 실어 보낸다("더울 때 피할 곳").
        List<ShadeSpot> shadeSpots = findShadeSpots(polyline);

        return new RouteResponse(
                RouteStop.coord(fromLat, fromLng), waypointStop, RouteStop.coord(toLat, toLng),
                polyline, mode,
                Math.round(directM * 10) / 10.0, Math.round(routeM * 10) / 10.0,
                shadeSpots);
    }

    /** 경로 폴리라인 corridor 안의 그늘/실내 피난처(F4) — PostGIS ST_DWithin(라인, 반경). */
    private List<ShadeSpot> findShadeSpots(List<LatLng> polyline) {
        if (polyline.size() < 2) {
            return List.of();
        }
        String lineWkt = toLineWkt(polyline);
        return placeRepository.findShadeAlongCorridor(lineWkt, SHADE_CATEGORIES, SHADE_CORRIDOR_M, SHADE_LIMIT)
                .stream()
                .map(v -> new ShadeSpot(v.getId(), v.getName(), v.getCategory(), v.getLat(), v.getLng()))
                .toList();
    }

    /** 폴리라인 → PostGIS WKT LINESTRING("경도 위도, ..."). Locale.ROOT로 소수점 = '.' 고정(WKT 파싱 안전). */
    private static String toLineWkt(List<LatLng> pts) {
        StringBuilder sb = new StringBuilder("LINESTRING(");
        for (int i = 0; i < pts.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(String.format(Locale.ROOT, "%.7f %.7f", pts.get(i).lng(), pts.get(i).lat()));
        }
        return sb.append(")").toString();
    }
}
