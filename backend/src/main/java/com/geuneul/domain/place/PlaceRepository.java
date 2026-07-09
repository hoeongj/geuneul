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
 *   표시 거리(distanceM)는 정렬식과 같은 타원체 기준 ST_Distance로 쿼리가 함께 반환한다 —
 *   애플리케이션 재계산을 없애 표시=정렬 일치.
 * - bounds: geometry && 연산 → V2의 GIST(geom) 인덱스를 탄다(박스 검색은 도 단위로 충분).
 *   거리 개념이 없어(중심점 없음) 거리 미반환·정렬 없음. limit 초과 시 박스 내 부분집합을 반환하며
 *   공간 분포/군집화는 프런트 마커 클러스터가 담당한다(중심거리 정렬을 넣으면 GIST(geom) && 경로를 벗어남).
 * - category 필터의 CAST(:category AS text)는 null 파라미터 타입 추론 실패(PostgreSQL) 방지용.
 *
 * <p><b>스코어드 쿼리(반경/bounds/단건)</b>는 위 공간 필터에 place_report_signals 뷰(ADR-0007)를
 * LEFT JOIN 해 survival_score 시공간 신호를 함께 반환한다(ScoredPlaceView). 제보 없는 장소는
 * COALESCE(...,0)으로 신호 0(등급 UNKNOWN). 공간 인덱스 경로는 스코어드에서도 동일하게 유지된다.
 * 최근접(nearest)은 "화장실 급할 때" 팬아웃 경로라 점수 없이 거리만 반환한다(PlaceDistanceView).
 *
 * <p>모든 쿼리는 {@code p.deleted_at IS NULL}을 함께 건다(ADR-0006) — 스냅샷 재적재로 soft-delete된
 * 장소(폐업 회전)는 검색·상세에서 즉시 사라진다. GiST 인덱스 선필터(ST_DWithin/&&) 뒤에 붙는 평범한
 * 술어라 인덱스 경로에는 영향이 없다.
 */
public interface PlaceRepository extends JpaRepository<Place, Long> {

    /** 스코어드 반경 검색: 중심(lat,lng)에서 radiusMeters 이내, 가까운 순 + 거리(m) + survival 신호. */
    @Query(value = """
            SELECT p.id AS "id", p.name AS "name", p.category AS "category", p.address AS "address",
                   ST_Y(p.geom) AS "lat", ST_X(p.geom) AS "lng", p.source AS "source",
                   ST_Distance(geography(p.geom), geography(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326))) AS "distanceM",
                   COALESCE(s.report_count, 0)   AS "reportCount",
                   COALESCE(s.freshness_score, 0) AS "freshnessScore",
                   COALESCE(s.comfort_score, 0)  AS "comfortScore",
                   COALESCE(s.risk_score, 0)     AS "riskScore"
            FROM places p
            LEFT JOIN place_report_signals s ON s.place_id = p.id
            WHERE p.deleted_at IS NULL
              AND ST_DWithin(
                    geography(p.geom),
                    geography(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)),
                    :radiusMeters)
              AND (CAST(:category AS text) IS NULL OR p.category = CAST(:category AS text))
            ORDER BY geography(p.geom) <-> geography(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326))
            LIMIT :limit
            """, nativeQuery = true)
    List<ScoredPlaceView> findWithinRadiusScored(@Param("lat") double lat,
                                                 @Param("lng") double lng,
                                                 @Param("radiusMeters") double radiusMeters,
                                                 @Param("category") String category,
                                                 @Param("limit") int limit);

    /**
     * 스코어드 반경 검색 + <b>카테고리 집합</b> 필터(추천/시나리오용, ADR-0008).
     * findWithinRadiusScored와 동일한 인덱스 경로(ST_DWithin geography + KNN 정렬)를 타되,
     * 시나리오가 여러 카테고리를 허용하므로 단일 필터 대신 {@code category = ANY(...)}로 IN 필터한다.
     * categories는 enum name의 콤마 CSV(예: "LIBRARY,UNDERGROUND"); null이면 전 카테고리.
     * 반환은 "가까운 순 상위 pool"이고, 시나리오 가중 재랭킹은 앱 레이어(RecommendationService)가 한다
     * (2단 검색: 공간 인덱스 선필터 → 후보 풀 재랭킹).
     */
    @Query(value = """
            SELECT p.id AS "id", p.name AS "name", p.category AS "category", p.address AS "address",
                   ST_Y(p.geom) AS "lat", ST_X(p.geom) AS "lng", p.source AS "source",
                   ST_Distance(geography(p.geom), geography(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326))) AS "distanceM",
                   COALESCE(s.report_count, 0)   AS "reportCount",
                   COALESCE(s.freshness_score, 0) AS "freshnessScore",
                   COALESCE(s.comfort_score, 0)  AS "comfortScore",
                   COALESCE(s.risk_score, 0)     AS "riskScore"
            FROM places p
            LEFT JOIN place_report_signals s ON s.place_id = p.id
            WHERE p.deleted_at IS NULL
              AND ST_DWithin(
                    geography(p.geom),
                    geography(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)),
                    :radiusMeters)
              AND (CAST(:categories AS text) IS NULL OR p.category = ANY(string_to_array(:categories, ',')))
            ORDER BY geography(p.geom) <-> geography(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326))
            LIMIT :limit
            """, nativeQuery = true)
    List<ScoredPlaceView> findWithinRadiusScoredByCategories(@Param("lat") double lat,
                                                             @Param("lng") double lng,
                                                             @Param("radiusMeters") double radiusMeters,
                                                             @Param("categories") String categories,
                                                             @Param("limit") int limit);

    /** kNN 최근접: 반경 제한 없이 가까운 순 상위 N개 (<-> 연산자, index-assisted KNN) + 거리(m). */
    @Query(value = """
            SELECT p.id AS "id", p.name AS "name", p.category AS "category", p.address AS "address",
                   ST_Y(p.geom) AS "lat", ST_X(p.geom) AS "lng", p.source AS "source",
                   ST_Distance(geography(p.geom), geography(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326))) AS "distanceM"
            FROM places p
            WHERE p.deleted_at IS NULL
              AND (CAST(:category AS text) IS NULL OR p.category = CAST(:category AS text))
            ORDER BY geography(p.geom) <-> geography(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326))
            LIMIT :limit
            """, nativeQuery = true)
    List<PlaceDistanceView> findNearest(@Param("lat") double lat,
                                        @Param("lng") double lng,
                                        @Param("category") String category,
                                        @Param("limit") int limit);

    /** 스코어드 bounds(뷰포트) 검색: 박스 안의 마커 + survival 신호(거리 없음 → distanceM=NULL). */
    @Query(value = """
            SELECT p.id AS "id", p.name AS "name", p.category AS "category", p.address AS "address",
                   ST_Y(p.geom) AS "lat", ST_X(p.geom) AS "lng", p.source AS "source",
                   CAST(NULL AS double precision) AS "distanceM",
                   COALESCE(s.report_count, 0)   AS "reportCount",
                   COALESCE(s.freshness_score, 0) AS "freshnessScore",
                   COALESCE(s.comfort_score, 0)  AS "comfortScore",
                   COALESCE(s.risk_score, 0)     AS "riskScore"
            FROM places p
            LEFT JOIN place_report_signals s ON s.place_id = p.id
            WHERE p.deleted_at IS NULL
              AND p.geom && ST_MakeEnvelope(:west, :south, :east, :north, 4326)
              AND (CAST(:category AS text) IS NULL OR p.category = CAST(:category AS text))
            LIMIT :limit
            """, nativeQuery = true)
    List<ScoredPlaceView> findInBoundsScored(@Param("west") double west,
                                             @Param("south") double south,
                                             @Param("east") double east,
                                             @Param("north") double north,
                                             @Param("category") String category,
                                             @Param("limit") int limit);

    /** 스코어드 단건 상세: survival 신호 포함(거리 없음 → distanceM=NULL). 없으면 빈 Optional. */
    @Query(value = """
            SELECT p.id AS "id", p.name AS "name", p.category AS "category", p.address AS "address",
                   ST_Y(p.geom) AS "lat", ST_X(p.geom) AS "lng", p.source AS "source",
                   CAST(NULL AS double precision) AS "distanceM",
                   COALESCE(s.report_count, 0)   AS "reportCount",
                   COALESCE(s.freshness_score, 0) AS "freshnessScore",
                   COALESCE(s.comfort_score, 0)  AS "comfortScore",
                   COALESCE(s.risk_score, 0)     AS "riskScore"
            FROM places p
            LEFT JOIN place_report_signals s ON s.place_id = p.id
            WHERE p.id = :id AND p.deleted_at IS NULL
            """, nativeQuery = true)
    java.util.Optional<ScoredPlaceView> findByIdScored(@Param("id") long id);

    long countBySource(String source);
}
