package com.geuneul.domain.route;

import com.geuneul.domain.place.PlaceRepository;
import com.geuneul.domain.route.dto.LatLng;
import com.geuneul.domain.route.dto.RouteResponse;
import com.geuneul.domain.route.dto.RouteResponse.RouteStop;
import com.geuneul.global.geo.GeoUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
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

        return new RouteResponse(
                RouteStop.coord(fromLat, fromLng), waypointStop, RouteStop.coord(toLat, toLng),
                polyline, mode,
                Math.round(directM * 10) / 10.0, Math.round(routeM * 10) / 10.0);
    }
}
