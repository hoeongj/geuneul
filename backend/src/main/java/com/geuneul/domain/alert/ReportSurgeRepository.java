package com.geuneul.domain.alert;

import com.geuneul.domain.report.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 제보 급증 감지(ADR-0016 §1) — 시공간 SQL. survival_score의 place_report_signals 뷰(ADR-0007)와 달리
 * <b>가중 없는 순수 건수 속도</b>("얼마나 몰리나")를 본다. 앱 전체스캔이 아니라 인덱스
 * (idx_reports_place_created·GiST) 경로를 탄다(docs/SPEC.md §0.4).
 *
 * <p>Report 엔티티에 붙지만 조회 전용 — 쓰기는 ReportRepository가 담당한다(관심사 분리).
 */
public interface ReportSurgeRepository extends JpaRepository<Report, Long> {

    /**
     * 한 장소의 시간창 내 유효(미만료) 제보 수. windowMinutes 분 이내 & expires_at 미도래만 센다.
     * 급증 판정(count ≥ minReports)은 호출부(ReportSurgeService)가 한다.
     */
    @Query(value = """
            SELECT COUNT(*) FROM reports
            WHERE place_id = :placeId
              AND expires_at > now()
              AND NOT hidden
              AND created_at >= now() - (:windowMinutes * interval '1 minute')
            """, nativeQuery = true)
    long countRecent(@Param("placeId") long placeId, @Param("windowMinutes") int windowMinutes);

    /**
     * 한 장소의 급증 요약(count + 최빈 타입) — 임계 미만이면 빈 Optional. 리스너가 NOTIFY를 받아
     * 이 장소가 실제 급증인지 재확인하고 SSE 이벤트를 조립할 때 쓴다.
     */
    @Query(value = """
            SELECT p.id AS "placeId", p.name AS "name",
                   ST_Y(p.geom) AS "lat", ST_X(p.geom) AS "lng",
                   agg.cnt AS "reportCount", agg.top_type AS "topType"
            FROM places p
            JOIN (
                SELECT place_id,
                       COUNT(*) AS cnt,
                       mode() WITHIN GROUP (ORDER BY report_type) AS top_type
                FROM reports
                WHERE place_id = :placeId
                  AND expires_at > now()
                  AND NOT hidden
                  AND created_at >= now() - (:windowMinutes * interval '1 minute')
                GROUP BY place_id
                HAVING COUNT(*) >= :minReports
            ) agg ON agg.place_id = p.id
            WHERE p.deleted_at IS NULL
            """, nativeQuery = true)
    Optional<SurgingPlaceView> findSurge(@Param("placeId") long placeId,
                                         @Param("windowMinutes") int windowMinutes,
                                         @Param("minReports") int minReports);

    /**
     * bounds 뷰포트 내 급증 장소 목록(폴백/스냅샷, GET /alerts/surge). places를 공간조인
     * (geom &amp;&amp; ST_MakeEnvelope → V2 GiST 경로)해, 시간창 내 유효제보가 minReports 이상인 장소만 반환한다.
     * mode()로 최빈 타입을 함께 뽑아 안내 문구를 만든다. deleted_at IS NULL(ADR-0006).
     */
    @Query(value = """
            SELECT p.id AS "placeId", p.name AS "name",
                   ST_Y(p.geom) AS "lat", ST_X(p.geom) AS "lng",
                   agg.cnt AS "reportCount", agg.top_type AS "topType"
            FROM places p
            JOIN (
                SELECT place_id,
                       COUNT(*) AS cnt,
                       mode() WITHIN GROUP (ORDER BY report_type) AS top_type
                FROM reports
                WHERE expires_at > now()
                  AND NOT hidden
                  AND created_at >= now() - (:windowMinutes * interval '1 minute')
                GROUP BY place_id
                HAVING COUNT(*) >= :minReports
            ) agg ON agg.place_id = p.id
            WHERE p.deleted_at IS NULL
              AND p.geom && ST_MakeEnvelope(:west, :south, :east, :north, 4326)
            ORDER BY agg.cnt DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<SurgingPlaceView> findSurgingInBounds(@Param("west") double west,
                                               @Param("south") double south,
                                               @Param("east") double east,
                                               @Param("north") double north,
                                               @Param("windowMinutes") int windowMinutes,
                                               @Param("minReports") int minReports,
                                               @Param("limit") int limit);
}
