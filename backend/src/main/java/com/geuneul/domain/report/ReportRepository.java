package com.geuneul.domain.report;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    /**
     * 장소의 유효(미만료·미숨김) 제보 최신순. idx_reports_place_created 경로.
     * 상세 화면 "최근 제보"용 — 20개면 충분(P3 freshness 스코어도 최근분만 본다).
     * HiddenFalse: 모더레이션 숨김 제보(V12)는 공개 목록에서 제외.
     */
    List<Report> findTop20ByPlaceIdAndExpiresAtAfterAndHiddenFalseOrderByCreatedAtDesc(long placeId, OffsetDateTime now);

    /**
     * 유저의 총 제보 수(만료 여부 무관) — trust_score 활동량 신호({@link com.geuneul.domain.auth.TrustScore}).
     * idx_reports_user(V6) 경로.
     */
    long countByUserId(Long userId);

    /**
     * 유저의 GPS 방문인증(verified=true) 제보 수 — trust_score verified 보너스(A2). countByUserId의
     * 부분집합이며, idx_reports_user(V6, user_id 부분 인덱스)로 좁힌 뒤 verified를 필터한다(전체스캔 없음).
     */
    long countByUserIdAndVerifiedTrue(Long userId);

    /**
     * 시간대별 혼잡 파생(ADR-0005 §④, 자체 popular-times) — 한 장소의 제보 이력을 KST 기준 요일×시간으로 집계.
     * <b>만료 제보도 포함</b>한다(휘발성 규약은 스코어에만 적용 — 혼잡 패턴은 과거 이력을 채굴). place_id
     * 선필터라 idx_reports_place_created 경로를 타고, 정렬은 요일→시간. created_at은 timestamptz(UTC 저장)라
     * {@code AT TIME ZONE 'Asia/Seoul'}로 한국 벽시계로 변환한 뒤 요일/시간을 뽑는다(국내 전용 서비스).
     * DOW: 0=일 ... 6=토.
     */
    @Query(value = """
            SELECT EXTRACT(DOW  FROM created_at AT TIME ZONE 'Asia/Seoul')::int AS "dow",
                   EXTRACT(HOUR FROM created_at AT TIME ZONE 'Asia/Seoul')::int AS "hour",
                   COUNT(*)                                                     AS "sampleCount",
                   COUNT(*) FILTER (WHERE report_type = 'CROWDED')              AS "crowdedCount",
                   COUNT(*) FILTER (WHERE report_type = 'SEAT_OK')              AS "seatOkCount"
            FROM reports
            WHERE place_id = :placeId
              AND NOT hidden
            GROUP BY 1, 2
            ORDER BY 1, 2
            """, nativeQuery = true)
    List<PlaceCongestionSlotView> congestionByPlace(@Param("placeId") long placeId);
}
