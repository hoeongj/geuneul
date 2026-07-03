package com.geuneul.domain.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    /**
     * 장소의 유효(미만료) 제보 최신순. idx_reports_place_created 경로.
     * 상세 화면 "최근 제보"용 — 20개면 충분(P3 freshness 스코어도 최근분만 본다).
     */
    List<Report> findTop20ByPlaceIdAndExpiresAtAfterOrderByCreatedAtDesc(long placeId, OffsetDateTime now);
}
