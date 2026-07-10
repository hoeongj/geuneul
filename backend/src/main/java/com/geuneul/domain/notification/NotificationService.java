package com.geuneul.domain.notification;

import com.geuneul.domain.alert.dto.SurgeInfo;
import com.geuneul.domain.notification.dto.NotificationResponse;
import com.geuneul.domain.notification.dto.NotificationRuleRequest;
import com.geuneul.domain.notification.dto.NotificationRuleResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

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

    private final NotificationRuleRepository ruleRepository;
    private final NotificationDeliveryRepository deliveryRepository;

    public NotificationService(NotificationRuleRepository ruleRepository,
                               NotificationDeliveryRepository deliveryRepository) {
        this.ruleRepository = ruleRepository;
        this.deliveryRepository = deliveryRepository;
    }

    // --- 규칙 CRUD ---

    @Transactional
    public NotificationRuleResponse createRule(long userId, NotificationRuleRequest req) {
        if (req.type() == NotificationRuleType.SURGE_NEARBY) {
            if (req.lat() == null || req.lng() == null || req.radiusM() == null || req.radiusM() <= 0) {
                throw new ResponseStatusException(BAD_REQUEST, "SURGE_NEARBY는 lat·lng·radiusM(>0)이 필요합니다");
            }
        }
        NotificationRule rule = ruleRepository.save(
                NotificationRule.of(userId, req.type(), req.lat(), req.lng(), req.radiusM()));
        return NotificationRuleResponse.of(rule);
    }

    @Transactional(readOnly = true)
    public List<NotificationRuleResponse> listRules(long userId) {
        return ruleRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
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

    /** 현재 시각(ms). 테스트에서 재정의하지 않는다 — 버킷 경계 흔들림은 cooldown 특성상 허용(ADR-0018). */
    long clock() {
        return System.currentTimeMillis();
    }
}
