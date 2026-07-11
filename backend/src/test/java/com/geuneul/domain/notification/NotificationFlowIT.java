package com.geuneul.domain.notification;

import com.geuneul.AbstractIntegrationTest;
import com.geuneul.domain.alert.dto.SurgeInfo;
import com.geuneul.domain.auth.AuthProvider;
import com.geuneul.domain.auth.JwtService;
import com.geuneul.domain.auth.User;
import com.geuneul.domain.auth.UserRepository;
import com.geuneul.domain.bookmark.Bookmark;
import com.geuneul.domain.bookmark.BookmarkRepository;
import com.geuneul.domain.place.Place;
import com.geuneul.domain.place.PlaceCategory;
import com.geuneul.domain.place.PlaceRepository;
import com.geuneul.domain.report.Report;
import com.geuneul.domain.report.ReportRepository;
import com.geuneul.domain.report.ReportType;
import com.geuneul.global.geo.GeoUtils;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 알림 플로우 IT (B1, ADR-0018) — 실 PostGIS + Security + JWT로 규칙 생성 → 급증 평가(onSurge) →
 * 인앱 발송(중복 없이) → 읽음을 관통 검증한다(수용 기준). ST_DWithin 매칭·dedup_key·bookmark 조인을 실 SQL로 확인.
 * 급증 이벤트는 리스너(realtime.enabled=false로 IT에선 꺼짐) 대신 notificationService.onSurge를 직접 호출해 시뮬레이션.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "jwt.secret=geuneul-notif-it-test-secret-0123456789ab") // gitleaks:allow (테스트 전용 더미)
class NotificationFlowIT extends AbstractIntegrationTest {

    private static final double LAT = 37.5124;
    private static final double LNG = 126.9530;

    @Autowired
    MockMvc mvc;

    @Autowired
    NotificationService notificationService;

    @Autowired
    NotificationRuleRepository ruleRepository;

    @Autowired
    NotificationDeliveryRepository deliveryRepository;

    @Autowired
    BookmarkRepository bookmarkRepository;

    @Autowired
    PlaceRepository placeRepository;

    @Autowired
    ReportRepository reportRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JwtService jwtService;

    private Long placeId;
    private String token;

    @BeforeEach
    void setUp() {
        deliveryRepository.deleteAll();
        reportRepository.deleteAll();
        ruleRepository.deleteAll();
        bookmarkRepository.deleteAll();
        placeRepository.deleteAll();
        userRepository.deleteAll();

        Place place = placeRepository.save(Place.of(
                "노량진 지하보도", PlaceCategory.UNDERGROUND, "서울 동작구 노량진로",
                GeoUtils.point(LAT, LNG), "test", "nt-1"));
        placeId = place.getId();

        User user = userRepository.save(User.create(AuthProvider.KAKAO, "kakao-nt-1", "nt@test.com", "알림러", null));
        token = jwtService.issue(user);
    }

    private SurgeInfo surge() {
        return SurgeInfo.of(placeId, "노량진 지하보도", LAT, LNG, 4, "FLOOD");
    }

    /** 유효(미만료·미숨김) 익명 제보 1건 저장 — created_at은 @CreationTimestamp(방금)이라 since 창 안. */
    private void saveReport(ReportType type) {
        reportRepository.save(Report.of(null, placeId, type, null, null, true, false,
                OffsetDateTime.now().plusHours(12)));
    }

    private void createBookmarkSurgeRule() throws Exception {
        mvc.perform(post("/notifications/rules").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"BOOKMARK_SURGE\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("BOOKMARK_STATUS: 관심 장소 침수 단건 제보 → 인앱 1건(§6 중립) + 재호출 dedup (C3)")
    void bookmarkStatusFloodNotifiesOnce() throws Exception {
        createBookmarkSurgeRule();
        User me = userRepository.findAll().get(0);
        bookmarkRepository.save(Bookmark.of(me.getId(), placeId, null));
        saveReport(ReportType.FLOOD); // 급증(≥3) 아님 — 단건

        notificationService.onBookmarkStatus(placeId);
        notificationService.onBookmarkStatus(placeId); // 같은 cooldown 버킷 → dedup

        mvc.perform(get("/notifications").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1)) // 중복 없이 1건
                .andExpect(jsonPath("$.items[0].type").value("BOOKMARK_SURGE"))
                .andExpect(jsonPath("$.items[0].title").value("관심 장소 소식"))
                .andExpect(jsonPath("$.items[0].placeId").value(placeId))
                .andExpect(jsonPath("$.items[0].body", Matchers.containsString("우회"))) // §6 우회 권장
                .andExpect(jsonPath("$.items[0].body", Matchers.not(Matchers.containsString("위험")))); // 공포 조장 금지
    }

    @Test
    @DisplayName("BOOKMARK_STATUS: 비유의미 단건 제보(COOL)는 알림 없음 (C3)")
    void bookmarkStatusIgnoresNonMeaningful() throws Exception {
        createBookmarkSurgeRule();
        User me = userRepository.findAll().get(0);
        bookmarkRepository.save(Bookmark.of(me.getId(), placeId, null));
        saveReport(ReportType.COOL); // 유의미(FLOOD·SLIPPERY) 아님

        notificationService.onBookmarkStatus(placeId);

        mvc.perform(get("/notifications").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    @DisplayName("BOOKMARK_STATUS: 북마크 안 하면 침수 제보여도 알림 없음 (C3)")
    void bookmarkStatusRequiresBookmark() throws Exception {
        createBookmarkSurgeRule(); // 규칙만, 북마크 없음
        saveReport(ReportType.FLOOD);

        notificationService.onBookmarkStatus(placeId);

        mvc.perform(get("/notifications").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    @DisplayName("SURGE_NEARBY 규칙 생성 → 급증 → 인앱 알림 1건(중복 없이) → 읽음")
    void nearbyRuleReceivesOneNotification() throws Exception {
        mvc.perform(post("/notifications/rules").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SURGE_NEARBY\",\"lat\":" + LAT + ",\"lng\":" + LNG + ",\"radiusM\":2000}"))
                .andExpect(status().isCreated());

        notificationService.onSurge(surge());
        notificationService.onSurge(surge()); // 같은 cooldown 버킷 → dedup

        mvc.perform(get("/notifications").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(1))          // 중복 없이 1건
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].type").value("SURGE_NEARBY"))
                .andExpect(jsonPath("$.items[0].title").value("내 주변 제보 급증"))
                .andExpect(jsonPath("$.items[0].placeId").value(placeId));

        mvc.perform(post("/notifications/read").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/notifications").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(0));
    }

    @Test
    @DisplayName("반경 밖 규칙은 급증 알림을 받지 않는다(ST_DWithin)")
    void ruleOutsideRadiusGetsNothing() throws Exception {
        // 중심을 약 5km 떨어뜨리고 반경 1km → 매칭 안 됨
        mvc.perform(post("/notifications/rules").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SURGE_NEARBY\",\"lat\":" + (LAT + 0.05) + ",\"lng\":" + LNG + ",\"radiusM\":1000}"))
                .andExpect(status().isCreated());

        notificationService.onSurge(surge());

        mvc.perform(get("/notifications").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    @DisplayName("BOOKMARK_SURGE: 저장한 장소에 급증이 나면 알림을 받는다")
    void bookmarkSurgeMatchesSavedPlace() throws Exception {
        mvc.perform(post("/notifications/rules").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"BOOKMARK_SURGE\"}"))
                .andExpect(status().isCreated());
        // 이 장소를 북마크(A7)
        User me = userRepository.findAll().get(0);
        bookmarkRepository.save(Bookmark.of(me.getId(), placeId, null));

        notificationService.onSurge(surge());

        mvc.perform(get("/notifications").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].type").value("BOOKMARK_SURGE"))
                .andExpect(jsonPath("$.items[0].title").value("관심 장소 소식"));
    }

    @Test
    @DisplayName("비로그인은 알림 센터 401")
    void unauthenticatedIsRejected() throws Exception {
        mvc.perform(get("/notifications")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("SURGE_NEARBY 좌표 누락은 400")
    void surgeNearbyWithoutGeoIs400() throws Exception {
        mvc.perform(post("/notifications/rules").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SURGE_NEARBY\"}"))
                .andExpect(status().isBadRequest());
    }
}
