package com.geuneul.domain.route;

import com.geuneul.domain.route.dto.LatLng;

import java.util.List;
import java.util.Optional;

/**
 * 직선 경로 참조 구현(B2, ADR-0019) — 경유지를 그대로 이어 직선 폴리라인으로 준다. 화장실 경유 여부·순서·근사
 * 거리는 정확하다(경유지 선택은 PostGIS가 담당).
 *
 * <p><b>런타임 주입 빈은 아니다.</b> 활성 공급자는 {@link KakaoDirectionsProvider}(@Primary, ADR-0021)이고,
 * 도로 공급자가 empty를 주면 {@link RouteService}가 waypoints 직선(mode="straight")으로 인라인 폴백한다.
 * 이 클래스는 그 직선 전략을 명시하는 참조/테스트용 구현이다({@code RouteServiceTest}가 직접 생성해 사용).
 */
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
