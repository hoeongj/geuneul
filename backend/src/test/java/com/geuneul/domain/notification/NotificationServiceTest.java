package com.geuneul.domain.notification;

import com.geuneul.domain.alert.dto.SurgeInfo;
import com.geuneul.domain.notification.dto.NotificationRuleRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * NotificationService 오케스트레이션 단위테스트 — DB 없이 로컬에서 항상 돈다(TS-009 무관).
 * 실 매칭 INSERT(ST_DWithin·dedup)·조인은 NotificationFlowIT(CI)가 커버; 여기는 검증·평가 배선만.
 */
class NotificationServiceTest {

    private NotificationRuleRepository ruleRepository;
    private NotificationDeliveryRepository deliveryRepository;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        ruleRepository = mock(NotificationRuleRepository.class);
        deliveryRepository = mock(NotificationDeliveryRepository.class);
        service = new NotificationService(ruleRepository, deliveryRepository);
    }

    @Test
    @DisplayName("SURGE_NEARBY 규칙은 lat/lng/radiusM이 없으면 400")
    void surgeNearbyRequiresGeo() {
        var req = new NotificationRuleRequest(NotificationRuleType.SURGE_NEARBY, null, null, null);

        assertThatThrownBy(() -> service.createRule(10L, req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
        verify(ruleRepository, never()).save(any());
    }

    @Test
    @DisplayName("BOOKMARK_SURGE 규칙은 좌표 없이도 생성된다(관심 장소 기반)")
    void bookmarkSurgeAllowsNoGeo() {
        var req = new NotificationRuleRequest(NotificationRuleType.BOOKMARK_SURGE, null, null, null);
        // 실 DB는 save 시 id를 부여한다 — 목은 응답 조립(NotificationRuleResponse는 primitive id)이 깨지지 않게 id를 넣어 돌려준다.
        when(ruleRepository.save(any())).thenAnswer(i -> {
            NotificationRule r = i.getArgument(0);
            setId(r, 1L);
            return r;
        });

        service.createRule(10L, req);

        verify(ruleRepository).save(any(NotificationRule.class));
    }

    private static void setId(NotificationRule rule, long id) {
        try {
            var f = NotificationRule.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(rule, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("급증 발생 시 SURGE_NEARBY·BOOKMARK_SURGE 매칭 INSERT를 각각 부른다(같은 버킷)")
    void onSurgeInvokesBothMatchers() {
        SurgeInfo surge = SurgeInfo.of(185L, "노량진 지하보도", 37.514, 126.942, 4, "FLOOD");

        service.onSurge(surge);

        verify(deliveryRepository).insertSurgeNearby(eq(185L), eq(37.514), eq(126.942),
                anyString(), anyString(), anyLong());
        verify(deliveryRepository).insertBookmarkSurge(eq(185L), anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("없는 규칙 토글은 404")
    void toggleMissingThrows404() {
        when(ruleRepository.findByIdAndUserId(99L, 10L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.setActive(10L, 99L, false))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }
}
