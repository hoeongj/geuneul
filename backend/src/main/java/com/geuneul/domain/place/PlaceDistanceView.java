package com.geuneul.domain.place;

/**
 * 반경/최근접 검색의 결과 투영 — 좌표와 <b>거리(m)를 DB가 계산해 그대로 돌려준다</b>.
 *
 * <p>정렬(ORDER BY geography(geom) &lt;-&gt;)과 표시 거리(ST_Distance(geography,geography))가
 * 둘 다 동일한 타원체(WGS84) 기준이라, 표시값이 정렬 순서와 항상 일치한다.
 * (이전엔 애플리케이션에서 하버사인 구체 근사로 거리를 재계산해 정렬식과 미세하게 어긋났다.)
 * geom 컬럼 대신 ST_Y/ST_X로 lat/lng 스칼라만 뽑아 매핑을 단순화한다.
 */
public interface PlaceDistanceView {
    long getId();

    String getName();

    String getCategory();

    String getAddress();

    double getLat();

    double getLng();

    String getSource();

    /** 검색 중심점으로부터의 거리(m, 타원체). */
    double getDistanceM();
}
