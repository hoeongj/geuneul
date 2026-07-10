package com.geuneul.domain.route;

/**
 * 화장실 경유지 선택 투영(B2) — 출발·도착에서의 거리(m)를 DB가 계산해 detour 최소 후보를 고른다.
 * distFromM+distToM가 "화장실 들렀다 가는 총 거리"의 근사이며, 정렬 기준(우회 최소)이다.
 */
public interface RouteWaypointView {
    long getPlaceId();

    String getName();

    double getLat();

    double getLng();

    String getAddress();

    double getDistFromM();

    double getDistToM();
}
