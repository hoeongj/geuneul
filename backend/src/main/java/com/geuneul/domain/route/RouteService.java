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
    /** 오버레이 대상 그늘/실내 카테고리(enum name CSV, N8) — 쉼터·도서관·지하상가. C4 그늘 경유지 후보도 동일 집합. */
    static final String SHADE_CATEGORIES = "COOLING_SHELTER,LIBRARY,UNDERGROUND";
    /** 오버레이 마커 상한(지도 혼잡 방지). */
    static final int SHADE_LIMIT = 15;
    /** 화장실 경유지 카테고리(F3) — 경유지 선택용 CSV(단일). */
    static final String TOILET_CATEGORY = "TOILET";

    private final PlaceRepository placeRepository;
    private final DirectionsProvider directions;

    public RouteService(PlaceRepository placeRepository, DirectionsProvider directions) {
        this.placeRepository = placeRepository;
        this.directions = directions;
    }

    /** 화장실 포함 경로(B2·F3) — 경유지 후보는 TOILET. */
    public RouteResponse toiletRoute(double fromLat, double fromLng, double toLat, double toLng) {
        return viaRoute(fromLat, fromLng, toLat, toLng, TOILET_CATEGORY);
    }

    /**
     * 그늘 경유 경로(C4) — 출발→도착 사이 우회 최소 쿨링쉼터/도서관/지하상가 1곳을 경유지로 끼운다("가는 길에 쉬어가기").
     * F3 화장실 경로와 완전 대칭 — 경유지 카테고리만 SHADE_CATEGORIES로 바뀌고 인프라(corridor 선택·폴리라인·오버레이)는 공통.
     * §0-2대로 자체 가중 라우팅은 하지 않는다(경유지 1곳만 PostGIS로 고르고 폴리라인은 기존 DirectionsProvider).
     */
    public RouteResponse shadeRoute(double fromLat, double fromLng, double toLat, double toLng) {
        return viaRoute(fromLat, fromLng, toLat, toLng, SHADE_CATEGORIES);
    }

    /**
     * 경유지 경로 공통 로직 — 허용 카테고리(CSV) 안에서 우회 최소 경유지 1곳을 corridor로 고르고, 폴리라인을 만들고,
     * 경로 주변 그늘/실내 피난처(N8)를 오버레이로 얹는다. 경유지가 corridor에 없으면 직선/도로만(graceful 폴백).
     */
    private RouteResponse viaRoute(double fromLat, double fromLng, double toLat, double toLng, String categoriesCsv) {
        double directM = GeoUtils.haversineMeters(fromLat, fromLng, toLat, toLng);
        double midLat = (fromLat + toLat) / 2;
        double midLng = (fromLng + toLng) / 2;
        double corridorM = Math.max(directM / 2 + CORRIDOR_BUFFER_M, CORRIDOR_BUFFER_M);

        Optional<RouteWaypointView> best = placeRepository.findBestWaypointByCategories(
                fromLat, fromLng, toLat, toLng, midLat, midLng, corridorM, categoriesCsv);

        List<LatLng> waypoints = new ArrayList<>();
        waypoints.add(new LatLng(fromLat, fromLng));
        RouteStop waypointStop = null;
        double routeM = directM;
        if (best.isPresent()) {
            RouteWaypointView t = best.get();
            waypoints.add(new LatLng(t.getLat(), t.getLng()));
            waypointStop = RouteStop.place(t.getLat(), t.getLng(), t.getPlaceId(), t.getName(), t.getCategory());
            routeM = t.getDistFromM() + t.getDistToM(); // 경유지 들렀다 가는 총거리 근사
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
