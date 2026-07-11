package com.geuneul.domain.notification;

import com.geuneul.domain.alert.dto.SurgeInfo;
import com.geuneul.domain.notification.dto.NotificationResponse;
import com.geuneul.domain.notification.dto.NotificationRuleRequest;
import com.geuneul.domain.notification.dto.NotificationRuleResponse;
import com.geuneul.domain.place.PlaceDistanceView;
import com.geuneul.domain.place.PlaceRepository;
import com.geuneul.domain.push.PushService;
import com.geuneul.domain.report.MeaningfulReportView;
import com.geuneul.domain.report.ReportRepository;
import com.geuneul.domain.weather.HeatComfort;
import com.geuneul.domain.weather.Weather;
import com.geuneul.domain.weather.WeatherService;
import com.geuneul.global.web.ApiRequests;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * 알림(B1, ADR-0018) — 규칙 CRUD + 급증 이벤트 평가 + 발송 이력 조회. 로그인 유저 개인화(살).
 * 평가는 새 스케줄러 없이 급증 LISTEN/NOTIFY(ADR-0016)에 훅한다({@link #onSurge}) — ReportNotificationListener가 호출.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /** cooldown 창(ms) — 같은 규칙×장소가 이 창 안에 반복 급증해도 재발송 안 함(dedup_key 시간 버킷). */
    static final long COOLDOWN_MS = 600_000; // 10분

    /** HEAT_ESCAPE cooldown(ms) — 날씨는 시간당 갱신이라 규칙당 3시간 1회로 나깅 방지(ADR-0020). */
    static final long HEAT_COOLDOWN_MS = 10_800_000; // 3시간

    static final int MAX_RULE_RADIUS_M = 10_000;

    /**
     * 관심 장소 단건 상태 알림(C3, ADR-0026)의 유의미 제보 타입 — §6 우회/주의 권장 대상(침수·미끄럼)만.
     * 모든 제보로 열면 COOL/SEAT_OK까지 스팸이 되므로 장마철 안전 위험 둘로 좁혀 신호 대 잡음을 지킨다.
     */
    static final List<String> BOOKMARK_STATUS_TYPES = List.of("FLOOD", "SLIPPERY");

    private final NotificationRuleRepository ruleRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final WeatherService weatherService;
    private final PlaceRepository placeRepository;
    private final PushService pushService;
    private final ReportRepository reportRepository;

    public NotificationService(NotificationRuleRepository ruleRepository,
                               NotificationDeliveryRepository deliveryRepository,
                               WeatherService weatherService,
                               PlaceRepository placeRepository,
                               PushService pushService,
                               ReportRepository reportRepository) {
        this.ruleRepository = ruleRepository;
        this.deliveryRepository = deliveryRepository;
        this.weatherService = weatherService;
        this.placeRepository = placeRepository;
        this.pushService = pushService;
        this.reportRepository = reportRepository;
    }

    // --- 규칙 CRUD ---

    @Transactional
    public NotificationRuleResponse createRule(long userId, NotificationRuleRequest req) {
        if (req.type() == NotificationRuleType.SURGE_NEARBY) {
            if (req.lat() == null || req.lng() == null || req.radiusM() == null || req.radiusM() <= 0) {
                throw new ResponseStatusException(BAD_REQUEST, "SURGE_NEARBY는 lat·lng·radiusM(>0)이 필요합니다");
            }
            ApiRequests.requireValidLatLng(req.lat(), req.lng());
            ApiRequests.requireRadiusWithin(req.radiusM(), MAX_RULE_RADIUS_M);
        } else if (req.type() == NotificationRuleType.HEAT_ESCAPE) {
            if (req.lat() == null || req.lng() == null) {
                throw new ResponseStatusException(BAD_REQUEST, "HEAT_ESCAPE는 lat·lng(폭염 판정 중심)이 필요합니다");
            }
            ApiRequests.requireValidLatLng(req.lat(), req.lng());
        }
        NotificationRule rule = ruleRepository.save(
                NotificationRule.of(userId, req.type(), req.lat(), req.lng(), req.radiusM()));
        return NotificationRuleResponse.of(rule);
    }

    @Transactional(readOnly = true)
    public List<NotificationRuleResponse> listRules(long userId) {
        return ruleRepository.findTop100ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(NotificationRuleResponse::of)
                .toList();
    }

    @Transactional
    public NotificationRuleResponse setActive(long userId, long ruleId, boolean active) {
        NotificationRule rule = ruleRepository.findByIdAndUserId(ruleId, userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "rule not found: " + ruleId));
        rule.setActive(active);
        return NotificationRuleResponse.of(rule);
    }

    @Transactional
    public void deleteRule(long userId, long ruleId) {
        NotificationRule rule = ruleRepository.findByIdAndUserId(ruleId, userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "rule not found: " + ruleId));
        ruleRepository.delete(rule);
    }

    // --- 발송 이력(알림 센터) ---

    @Transactional(readOnly = true)
    public NotificationResponse list(long userId) {
        List<NotificationDelivery> items = deliveryRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId);
        long unread = deliveryRepository.countByUserIdAndReadFalse(userId);
        return NotificationResponse.of(items, unread);
    }

    /** id=null이면 전체 읽음, 아니면 해당 1건(내 것만). */
    @Transactional
    public void markRead(long userId, Long id) {
        deliveryRepository.markRead(userId, id);
    }

    // --- 급증 이벤트 평가(ADR-0018 §2·§3) ---

    /**
     * 급증이 확인된 장소에 대해 활성 규칙을 매칭·발송한다. ReportNotificationListener가 급증 확인 직후 호출한다.
     * 멀티 인스턴스에서 모두 호출되지만 dedup_key UNIQUE(ON CONFLICT DO NOTHING)로 정확히 1건만 남는다.
     * 한 건 실패가 SSE 브로드캐스트/리스너를 죽이지 않게, 호출부(리스너 handle)가 예외를 격리한다.
     */
    @Transactional
    public void onSurge(SurgeInfo surge) {
        long bucket = clock() / COOLDOWN_MS; // cooldown 시간 버킷(멀티 인스턴스 공통)
        String body = surge.name() + " · " + surge.message(); // §6 순화 문구 재사용(ADR-0016)

        int nearby = deliveryRepository.insertSurgeNearby(
                surge.placeId(), surge.lat(), surge.lng(), "내 주변 제보 급증", body, bucket);
        int bookmark = deliveryRepository.insertBookmarkSurge(
                surge.placeId(), "관심 장소 소식", body, bucket);

        if (nearby + bookmark > 0) {
            log.info("[notify] 급증 발송 placeId={} nearby={} bookmark={}", surge.placeId(), nearby, bookmark);
        }
    }

    // --- 관심 장소 단건 상태 변화(BOOKMARK_STATUS, C3·ADR-0026) ---

    /**
     * 관심 장소에 유의미한 안전 제보(침수·미끄럼)가 <b>1건</b>이라도 뜨면 저장한 유저에게 알림 1건(+F2 푸시).
     * 급증(≥3건)과 달리 단건이라 {@link ReportNotificationListener}가 급증 게이트 밖에서 <b>무조건</b> 호출한다
     * (모든 제보 이벤트마다). 기존 BOOKMARK_SURGE 규칙·토글을 그대로 재사용(§9 새 규칙타입/화면 금지).
     *
     * <p>since(=now-cooldown) 이후의 유의미 제보만 봐서, 오래된 미만료 침수 제보가 무관한 신규 제보(COOL 등)에
     * 딸려 재발화하는 것을 막는다. 최근 유의미 제보가 없으면 조용히 return(대상 없는 알림 안 만듦).
     *
     * <p>푸시는 인서트 <b>승자만</b> 미리 구한 fresh 수신자에게 보낸다({@code inserted>0} 가드) — 한 콜의 인서트는
     * 같은 dedup_key 집합이라 all-or-nothing이므로, 승자의 수신자 목록 = 실제 인서트 집합과 일치해 정확히 1회 푸시된다
     * (RETURNING 미사용, TS-029 비동기·실패 격리).
     */
    @Transactional
    public void onBookmarkStatus(long placeId) {
        OffsetDateTime since = OffsetDateTime.ofInstant(Instant.ofEpochMilli(clock() - COOLDOWN_MS), ZoneOffset.UTC);
        // since 이후 유의미 제보를 타입별 최신 1건씩(FLOOD·SLIPPERY가 거의 동시에 들어와도 둘 다 알림).
        List<MeaningfulReportView> recent =
                reportRepository.findRecentMeaningfulReports(placeId, BOOKMARK_STATUS_TYPES, since);
        String title = "관심 장소 소식";
        for (MeaningfulReportView r : recent) {
            String reportType = r.getReportType();
            String body = statusBody(reportType);
            // dedup 버킷은 제보의 created_at에서 산정한다(벽시계 아님) — 그래야 같은 제보가 이후 무관한 제보 이벤트에
            // 딸려(버킷 경계를 넘어) 재알림되지 않는다(C3 리뷰: sliding since ↔ epoch bucket 불일치 제거).
            long bucket = r.getCreatedAt().toEpochMilli() / COOLDOWN_MS;
            // RETURNING으로 이 호출이 실제 삽입한 유저만 푸시 — 사전 SELECT 스냅샷과 실제 삽입 집합의 괴리로 생기던
            // 누락·중복 푸시를 없애 멀티 인스턴스에서도 정확히 1회 푸시된다(C3 리뷰).
            List<Long> pushed = deliveryRepository.insertBookmarkStatusReturning(placeId, reportType, title, body, bucket);
            if (!pushed.isEmpty()) {
                log.info("[notify] 관심장소 상태 발송 placeId={} type={} n={}", placeId, reportType, pushed.size());
                for (Long userId : pushed) {
                    pushService.sendToUser(userId, title, body, "/"); // 비동기·실패 격리(TS-029), push 비활성이면 no-op
                }
            }
        }
    }

    /** §6 중립 단수 문구 — '위험!' 없이 상태를 알리고 우회/주의를 권한다(공포 조장 금지). */
    private static String statusBody(String reportType) {
        return switch (reportType) {
            case "FLOOD" -> "최근 침수 제보가 있어요 · 우회를 권장해요";
            case "SLIPPERY" -> "최근 바닥이 미끄럽다는 제보가 있어요 · 조심해서 이동하세요";
            default -> "최근 상태 변화 제보가 있어요";
        };
    }

    // --- 폭염 피난 평가(HEAT_ESCAPE, ADR-0020) ---

    /**
     * 유저의 활성 HEAT_ESCAPE 규칙을 온디맨드로 평가한다 — 알림 센터를 열 때 컨트롤러가 list() 직전에 호출한다.
     * 급증(이벤트)과 달리 폭염은 상태라 트리거 소스가 없다 → 읽기 진입점에서 멱등 upsert(ADR-0020 §1).
     * 규칙 중심 날씨가 폭염주의보(체감 ≥33℃)면 가장 가까운 무더위쉼터를 찾아 3시간 버킷 dedup으로 1건 발송.
     * 날씨 결측·쉼터 없음이면 조용히 skip(graceful) — 한 규칙 실패가 다른 규칙/목록을 막지 않게 예외를 격리한다.
     */
    @Transactional
    public void evaluateHeatEscape(long userId) {
        List<NotificationRule> rules =
                ruleRepository.findByUserIdAndTypeAndActiveTrue(userId, NotificationRuleType.HEAT_ESCAPE);
        if (rules.isEmpty()) {
            return;
        }
        long bucket = clock() / HEAT_COOLDOWN_MS;
        for (NotificationRule rule : rules) {
            try {
                evaluateHeatRule(rule, bucket);
            } catch (RuntimeException e) {
                log.warn("[notify] HEAT_ESCAPE 평가 실패 ruleId={} (skip)", rule.getId(), e);
            }
        }
    }

    private void evaluateHeatRule(NotificationRule rule, long bucket) {
        Double lat = rule.getCenterLat();
        Double lng = rule.getCenterLng();
        if (lat == null || lng == null) {
            return; // 방어(생성 검증이 막지만 과거 데이터 대비)
        }
        Optional<Weather> weather = weatherService.getWeather(lat, lng);
        if (weather.isEmpty() || !HeatComfort.isHeatAdvisory(weather.get())) {
            return; // 날씨 결측 or 폭염 아님 → skip
        }
        List<PlaceDistanceView> shelters = placeRepository.findNearest(lat, lng, "COOLING_SHELTER", 1);
        if (shelters.isEmpty()) {
            return; // 근처 쉼터 없음 → 대상 없는 알림 안 만듦
        }
        PlaceDistanceView shelter = shelters.get(0);
        Double feels = HeatComfort.feelsLike(weather.get());
        String title = "폭염 — 근처 쉼터로 피해요";
        String body = String.format(
                "지금 체감 %d℃. 가까운 무더위쉼터 '%s'(%dm)에서 잠깐 쉬어가세요.",
                Math.round(feels), shelter.getName(), Math.round(shelter.getDistanceM()));
        String dedupKey = "heat:" + rule.getId() + ":" + bucket;

        int inserted = deliveryRepository.insertHeatEscape(
                rule.getUserId(), rule.getId(), shelter.getId(), title, body, dedupKey);
        if (inserted > 0) {
            log.info("[notify] 폭염 피난 발송 ruleId={} shelterId={} feels={}", rule.getId(), shelter.getId(), feels);
            // 인앱 센터에 이어 OS 배너도(F2) — push 비활성이면 no-op, 실패는 격리(회귀 0).
            pushService.sendToUser(rule.getUserId(), title, body, "/");
        }
    }

    /** 현재 시각(ms). 테스트에서 재정의하지 않는다 — 버킷 경계 흔들림은 cooldown 특성상 허용(ADR-0018). */
    long clock() {
        return System.currentTimeMillis();
    }
}
