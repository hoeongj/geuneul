package com.geuneul.domain.route;

import com.geuneul.domain.route.dto.LatLng;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 직선 경로 폴백(B2, ADR-0019) — 경유지를 그대로 이어 직선 폴리라인으로 준다. 외부 directions 키가 없어도
 * "출발 → 경유 화장실 → 도착"이 동작하는 MVP 기본 공급자. 도로를 따르진 않지만, 화장실 경유 여부·순서·근사
 * 거리는 정확하다(경유지 선택은 PostGIS가 담당). 키가 붙으면 도로 공급자로 교체(전략 교체점, DirectionsProvider).
 */
@Component
public class StraightLineDirectionsProvider implements DirectionsProvider {

    @Override
    public Optional<List<LatLng>> polyline(List<LatLng> waypoints) {
        return Optional.of(List.copyOf(waypoints));
    }

    @Override
    public String mode() {
        return "straight";
    }
}
