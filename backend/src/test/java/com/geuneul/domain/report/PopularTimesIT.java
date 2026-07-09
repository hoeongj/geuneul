package com.geuneul.domain.report;

import com.geuneul.AbstractIntegrationTest;
import com.geuneul.domain.place.Place;
import com.geuneul.domain.place.PlaceCategory;
import com.geuneul.domain.place.PlaceRepository;
import com.geuneul.domain.report.dto.PopularTimesSlot;
import com.geuneul.global.geo.GeoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 시간대별 혼잡 파생 IT (ADR-0005 §④) — 실 PostGIS로 KST 요일×시간 집계와 등급을 검증한다.
 * created_at은 @CreationTimestamp라 저장 후 JdbcTemplate으로 특정 시각으로 백데이트한다.
 */
class PopularTimesIT extends AbstractIntegrationTest {

    @Autowired
    ReportService reportService;

    @Autowired
    ReportRepository reportRepository;

    @Autowired
    PlaceRepository placeRepository;

    @Autowired
    JdbcTemplate jdbc;

    private Long placeId;

    @BeforeEach
    void setUp() {
        reportRepository.deleteAll();
        placeRepository.deleteAll();
        Place saved = placeRepository.save(Place.of(
                "노들서가", PlaceCategory.STUDY_CAFE, "서울 동작구",
                GeoUtils.point(37.5124, 126.9530), "test", "pt-1"));
        placeId = saved.getId();
    }

    private void save(ReportType type) {
        reportRepository.save(Report.anonymous(placeId, type, null, true, OffsetDateTime.now().plusHours(1)));
    }

    @Test
    @DisplayName("KST 토요일 14시 슬롯으로 집계 — UTC 저장이 KST 벽시계(hour=14)로 변환되고 CROWDED 우세는 BUSY")
    void aggregatesByKstSlotWithBusyLevel() {
        // 3 CROWDED + 1 SEAT_OK
        save(ReportType.CROWDED);
        save(ReportType.CROWDED);
        save(ReportType.CROWDED);
        save(ReportType.SEAT_OK);
        // 전부 KST 2026-07-11(토) 14:30 으로 백데이트 → UTC로는 05:30. AT TIME ZONE 변환이 옳으면 hour=14.
        jdbc.update("UPDATE reports SET created_at = TIMESTAMPTZ '2026-07-11 14:30:00+09' WHERE place_id = ?", placeId);

        List<PopularTimesSlot> slots = reportService.popularTimes(placeId);

        assertThat(slots).hasSize(1);
        PopularTimesSlot slot = slots.get(0);
        assertThat(slot.hour()).isEqualTo(14);          // UTC 5시가 아니라 KST 14시로 변환됐는지 = 시간대 정확성
        assertThat(slot.dow()).isEqualTo(6);            // 2026-07-11은 토요일(DOW 6)
        assertThat(slot.sampleCount()).isEqualTo(4);
        assertThat(slot.crowdedCount()).isEqualTo(3);
        assertThat(slot.seatOkCount()).isEqualTo(1);
        assertThat(slot.level()).isEqualTo("BUSY");     // (3-1)/4 = 0.5 ≥ 1/3
    }

    @Test
    @DisplayName("만료된 제보도 혼잡 패턴 집계에 포함된다 (이력 채굴 — 스코어링과 다른 규약)")
    void includesExpiredReports() {
        // 만료(과거 expires_at) 제보만 2건 → 스코어링에선 빠지지만 popular-times엔 잡혀야 한다
        reportRepository.save(Report.anonymous(placeId, ReportType.CROWDED, null, true,
                OffsetDateTime.now().minusHours(5)));
        reportRepository.save(Report.anonymous(placeId, ReportType.CROWDED, null, true,
                OffsetDateTime.now().minusHours(5)));

        List<PopularTimesSlot> slots = reportService.popularTimes(placeId);
        assertThat(slots).isNotEmpty();
        assertThat(slots.stream().mapToLong(PopularTimesSlot::sampleCount).sum()).isEqualTo(2);
    }

    @Test
    @DisplayName("제보 없는 장소는 빈 목록 (404 아님 — 장소는 존재)")
    void emptyWhenNoReports() {
        assertThat(reportService.popularTimes(placeId)).isEmpty();
    }
}
