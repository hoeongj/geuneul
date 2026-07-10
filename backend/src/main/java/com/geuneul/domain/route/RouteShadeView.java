package com.geuneul.domain.route;

/**
 * 경로 주변 그늘/실내 피난처 투영(F4, N8) — 경로 폴리라인 corridor(ST_DWithin) 안의 쉼터/도서관/지하상가.
 * "더울 때 피할 곳"을 지도에 오버레이하기 위한 좌표·이름·카테고리. 자체 가중 라우팅이 아니라 기존 경로에 얹는 표시.
 */
public interface RouteShadeView {

    Long getId();

    String getName();

    String getCategory();

    Double getLat();

    Double getLng();
}
