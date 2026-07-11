package com.geuneul.domain.route;

import com.geuneul.domain.route.dto.LatLng;

import java.util.List;
import java.util.Optional;

/**
 * 경로 폴리라인 공급자(B2, ADR-0019) — 경유지 순서(출발..도착)를 잇는 폴리라인을 만든다.
 *
 * <p>주입되는 활성 공급자는 {@link KakaoDirectionsProvider}(@Primary, ADR-0021 — 카카오내비 도로 폴리라인,
 * 기존 REST 키 재사용). 키 미설정·실패 시 empty를 주면 {@link RouteService}가 waypoints 직선(mode="straight")으로
 * 인라인 폴백한다. {@link StraightLineDirectionsProvider}는 그 직선 전략의 참조/테스트용 구현이다(런타임 빈 아님).
 */
public interface DirectionsProvider {

    /** 폴리라인. 성공 시 도로/직선 좌표열, 실패·미설정 시 empty(호출부가 waypoints 직선으로 폴백). */
    Optional<List<LatLng>> polyline(List<LatLng> waypoints);

    /** "straight" | "road" — 응답 mode 표기용. */
    String mode();
}
