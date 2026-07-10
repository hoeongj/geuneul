package com.geuneul.domain.route;

import com.geuneul.domain.route.dto.LatLng;

import java.util.List;
import java.util.Optional;

/**
 * 경로 폴리라인 공급자(B2, ADR-0019) — 경유지 순서(출발..도착)를 잇는 폴리라인을 만든다.
 *
 * <p>MVP는 {@link StraightLineDirectionsProvider}(우리 직선 폴백, 항상 성공). 도로를 따르는 실제 경로는
 * 외부 directions API(카카오모빌리티 waypoints)가 필요한데, <b>활용신청·키·실호출 계약검증(TS-026)</b>이
 * 붙는 사용자 액션이라 후속으로 분리한다. 키가 붙으면 이 인터페이스를 구현한 Kakao 공급자를 빈으로 얹으면
 * RouteService 변경 없이 mode="road"로 승격된다(전략 교체점). 실패/미설정 시 empty → 호출부가 직선 폴백.
 */
public interface DirectionsProvider {

    /** 폴리라인. 성공 시 도로/직선 좌표열, 실패·미설정 시 empty(호출부가 waypoints 직선으로 폴백). */
    Optional<List<LatLng>> polyline(List<LatLng> waypoints);

    /** "straight" | "road" — 응답 mode 표기용. */
    String mode();
}
