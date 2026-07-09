package com.geuneul.domain.alert;

import com.geuneul.AbstractIntegrationTest;
import com.geuneul.domain.alert.dto.SurgeInfo;
import com.geuneul.domain.place.Place;
import com.geuneul.domain.place.PlaceCategory;
import com.geuneul.domain.place.PlaceRepository;
import com.geuneul.domain.report.Report;
import com.geuneul.domain.report.ReportRepository;
import com.geuneul.domain.report.ReportType;
import com.geuneul.global.geo.GeoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 제보 급증 감지·전파 IT (ADR-0016) — 실 PostGIS로 시공간 SQL과 NOTIFY 트리거(V9)를 검증한다.
 * 임계는 application.yml 기본값(window=10분, minReports=3)을 그대로 쓴다.
 */
class ReportSurgeIT extends AbstractIntegrationTest {

    @Autowired
    ReportSurgeService surgeService;

    @Autowired
    ReportRepository reportRepository;

    @Autowired
    PlaceRepository placeRepository;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DataSource dataSource;

    private Long placeId;

    // 노량진 인근 — bounds 테스트용
    private static final double LAT = 37.5140;
    private static final double LNG = 126.9420;

    @BeforeEach
    void setUp() {
        reportRepository.deleteAll();
        placeRepository.deleteAll();
        Place saved = placeRepository.save(Place.of(
                "노량진 지하보도", PlaceCategory.UNDERGROUND, "서울 동작구 노량진로",
                GeoUtils.point(LAT, LNG), "test", "surge-1"));
        placeId = saved.getId();
    }

    private void insertReports(int n, ReportType type) {
        for (int i = 0; i < n; i++) {
            reportRepository.save(Report.anonymous(placeId, type, "제보" + i, true,
                    OffsetDateTime.now().plusHours(1)));   // 유효(미만료)
        }
    }

    @Test
    @DisplayName("임계 미만이면 급증 아님, 임계 이상이면 급증 — count·최빈타입까지 반환")
    void thresholdBoundary() {
        insertReports(2, ReportType.FLOOD);
        assertThat(surgeService.isSurging(placeId)).isFalse();
        assertThat(surgeService.surgeForPlace(placeId)).isEmpty();

        insertReports(1, ReportType.FLOOD);   // 총 3건 = 임계 도달
        assertThat(surgeService.isSurging(placeId)).isTrue();
        assertThat(surgeService.surgeForPlace(placeId)).hasValueSatisfying(s -> {
            assertThat(s.reportCount()).isEqualTo(3);
            assertThat(s.topType()).isEqualTo("FLOOD");
            assertThat(s.message()).contains("침수").doesNotContain("위험!");   // §6 순화
        });
    }

    @Test
    @DisplayName("만료된 제보는 급증 집계에서 제외된다(휘발성 규약)")
    void expiredExcluded() {
        // 만료 3건(과거 expires_at) + 유효 1건 → 유효는 1건뿐이라 급증 아님
        for (int i = 0; i < 3; i++) {
            reportRepository.save(Report.anonymous(placeId, ReportType.BUG, "만료", true,
                    OffsetDateTime.now().minusHours(1)));
        }
        insertReports(1, ReportType.BUG);
        assertThat(surgeService.isSurging(placeId)).isFalse();
    }

    @Test
    @DisplayName("시간창(10분) 밖 제보는 제외된다 — 급증은 '지금 몰리나'를 본다")
    void outsideWindowExcluded() {
        insertReports(3, ReportType.CROWDED);
        assertThat(surgeService.isSurging(placeId)).isTrue();

        // created_at을 20분 전으로 백데이트 → 시간창(10분) 밖
        jdbc.update("UPDATE reports SET created_at = now() - interval '20 minutes' WHERE place_id = ?", placeId);
        assertThat(surgeService.isSurging(placeId)).isFalse();
    }

    @Test
    @DisplayName("bounds 안의 급증 장소만 반환된다")
    void surgingInBounds() {
        insertReports(3, ReportType.COOL);

        // 이 장소를 포함하는 bounds
        List<SurgeInfo> inside = surgeService.surgingInBounds(LNG - 0.01, LAT - 0.01, LNG + 0.01, LAT + 0.01, 50);
        assertThat(inside).extracting(SurgeInfo::placeId).contains(placeId);

        // 멀리 떨어진 bounds(부산 인근) → 없음
        List<SurgeInfo> outside = surgeService.surgingInBounds(129.0, 35.0, 129.1, 35.1, 50);
        assertThat(outside).isEmpty();
    }

    @Test
    @DisplayName("제보 INSERT가 geuneul_report_surge 채널로 NOTIFY를 쏜다(V9 트리거)")
    void insertFiresNotify() throws Exception {
        try (Connection listen = dataSource.getConnection()) {
            try (Statement st = listen.createStatement()) {
                st.execute("LISTEN geuneul_report_surge");
            }
            PGConnection pg = listen.unwrap(PGConnection.class);

            // 별도 커넥션(리포지토리)으로 제보 저장 → 커밋 시 트리거가 NOTIFY
            reportRepository.save(Report.anonymous(placeId, ReportType.FLOOD, "트리거 테스트", true,
                    OffsetDateTime.now().plusHours(1)));

            // 최대 5초 폴링
            PGNotification[] notifications = pg.getNotifications(5_000);
            assertThat(notifications).isNotNull();
            assertThat(notifications).anySatisfy(n -> {
                assertThat(n.getName()).isEqualTo("geuneul_report_surge");
                assertThat(n.getParameter()).isEqualTo(String.valueOf(placeId));
            });
        }
    }
}
