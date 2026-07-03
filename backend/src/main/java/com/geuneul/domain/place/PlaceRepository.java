package com.geuneul.domain.place;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 공간검색 리포지토리 — 세 쿼리 모두 인덱스 경로가 설계의 핵심 (ADR-0001).
 *
 * - 반경/최근접: geography(geom) 함수식 사용 → V3의 GIST(geography(geom)) 함수 인덱스를 탄다.
 *   함수 인덱스는 "쿼리의 식이 인덱스의 식과 동일"할 때만 작동하므로 geography(...) 표기를 바꾸면 안 된다.
 *   (PostGIS 표준 캐스팅 문법 `geom::geography`는 Spring Data 네이티브 쿼리 파서가
 *   `:geography`를 파라미터로 오인하므로 함수형 `geography(geom)`을 쓴다 — 같은 의미.)
 *   표시 거리(distanceM)는 정렬식과 같은 타원체 기준 ST_Distance로 쿼리가 함께 반환한다(PlaceDistanceView) —
 *   애플리케이션 재계산을 없애 표시=정렬 일치.
 * - bounds: geometry && 연산 → V2의 GIST(geom) 인덱스를 탄다(박스 검색은 도 단위로 충분).
 *   거리 개념이 없어(중심점 없음) 거리 미반환·정렬 없음. limit 초과 시 박스 내 부분집합을 반환하며
 *   공간 분포/군집화는 프런트 마커 클러스터가 담당한다(중심거리 정렬을 넣으면 GIST(geom) && 경로를 벗어남).
 * - category 필터의 CAST(:category AS text)는 null 파라미터 타입 추론 실패(PostgreSQL) 방지용.
 */
public interface PlaceRepository extends JpaRepository<Place, Long> {

    /** 반경 검색: 중심(lat,lng)에서 radiusMeters 이내, 가까운 순 + 거리(m). */
    @Query(value = """
            SELECT p.id AS "id", p.name AS "name", p.category AS "category", p.address AS "address",
                   ST_Y(p.geom) AS "lat", ST_X(p.geom) AS "lng", p.source AS "source",
                   ST_Distance(geography(p.geom), geography(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326))) AS "distanceM"
            FROM places p
            WHERE ST_DWithin(
                    geography(p.geom),
                    geography(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)),
                    :radiusMeters)
              AND (CAST(:category AS text) IS NULL OR p.category = CAST(:category AS text))
            ORDER BY geography(p.geom) <-> geography(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326))
            LIMIT :limit
            """, nativeQuery = true)
    List<PlaceDistanceView> findWithinRadius(@Param("lat") double lat,
                                             @Param("lng") double lng,
                                             @Param("radiusMeters") double radiusMeters,
                                             @Param("category") String category,
                                             @Param("limit") int limit);

    /** kNN 최근접: 반경 제한 없이 가까운 순 상위 N개 (<-> 연산자, index-assisted KNN) + 거리(m). */
    @Query(value = """
            SELECT p.id AS "id", p.name AS "name", p.category AS "category", p.address AS "address",
                   ST_Y(p.geom) AS "lat", ST_X(p.geom) AS "lng", p.source AS "source",
                   ST_Distance(geography(p.geom), geography(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326))) AS "distanceM"
            FROM places p
            WHERE (CAST(:category AS text) IS NULL OR p.category = CAST(:category AS text))
            ORDER BY geography(p.geom) <-> geography(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326))
            LIMIT :limit
            """, nativeQuery = true)
    List<PlaceDistanceView> findNearest(@Param("lat") double lat,
                                        @Param("lng") double lng,
                                        @Param("category") String category,
                                        @Param("limit") int limit);

    /** bounds(뷰포트) 검색: west,south,east,north 박스 안의 마커(거리 없음, 위 클래스 주석 참고). */
    @Query(value = """
            SELECT p.* FROM places p
            WHERE p.geom && ST_MakeEnvelope(:west, :south, :east, :north, 4326)
              AND (CAST(:category AS text) IS NULL OR p.category = CAST(:category AS text))
            LIMIT :limit
            """, nativeQuery = true)
    List<Place> findInBounds(@Param("west") double west,
                             @Param("south") double south,
                             @Param("east") double east,
                             @Param("north") double north,
                             @Param("category") String category,
                             @Param("limit") int limit);

    long countBySource(String source);
}
