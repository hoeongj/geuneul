package com.geuneul.domain.notification;

import com.geuneul.domain.alert.dto.SurgeInfo;
import com.geuneul.domain.notification.dto.NotificationRuleRequest;
import com.geuneul.domain.place.PlaceDistanceView;
import com.geuneul.domain.place.PlaceRepository;
import com.geuneul.domain.weather.HeatComfort;
import com.geuneul.domain.weather.Weather;
import com.geuneul.domain.weather.WeatherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
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
    private WeatherService weatherService;
    private PlaceRepository placeRepository;
    private com.geuneul.domain.push.PushService pushService;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        ruleRepository = mock(NotificationRuleRepository.class);
        deliveryRepository = mock(NotificationDeliveryRepository.class);
        weatherService = mock(WeatherService.class);
        placeRepository = mock(PlaceRepository.class);
        pushService = mock(com.geuneul.domain.push.PushService.class);
        service = new NotificationService(ruleRepository, deliveryRepository, weatherService, placeRepository,
                pushService);
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

    @Test
    @DisplayName("HEAT_ESCAPE 규칙은 lat/lng 없으면 400")
    void heatEscapeRequiresGeo() {
        var req = new NotificationRuleRequest(NotificationRuleType.HEAT_ESCAPE, null, null, null);

        assertThatThrownBy(() -> service.createRule(10L, req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
        verify(ruleRepository, never()).save(any());
    }

    @Test
    @DisplayName("폭염주의보 + 근처 쉼터 있으면 폭염 피난 발송 1건")
    void heatEscapeAdvisoryInsertsDelivery() {
        NotificationRule rule = NotificationRule.of(10L, NotificationRuleType.HEAT_ESCAPE, 37.5, 127.0, null);
        setId(rule, 7L);
        when(ruleRepository.findByUserIdAndTypeAndActiveTrue(10L, NotificationRuleType.HEAT_ESCAPE))
                .thenReturn(List.of(rule));

        Weather weather = mock(Weather.class);
        when(weatherService.getWeather(37.5, 127.0)).thenReturn(Optional.of(weather));

        PlaceDistanceView shelter = mock(PlaceDistanceView.class);
        when(shelter.getId()).thenReturn(500L);
        when(shelter.getName()).thenReturn("상도동 경로당");
        when(shelter.getDistanceM()).thenReturn(120.0);
        when(placeRepository.findNearest(37.5, 127.0, "COOLING_SHELTER", 1)).thenReturn(List.of(shelter));

        try (MockedStatic<HeatComfort> heatComfort = mockStatic(HeatComfort.class)) {
            heatComfort.when(() -> HeatComfort.isHeatAdvisory(weather)).thenReturn(true);
            heatComfort.when(() -> HeatComfort.feelsLike(weather)).thenReturn(34.0);

            service.evaluateHeatEscape(10L);
        }

        verify(deliveryRepository).insertHeatEscape(eq(10L), eq(7L), eq(500L), anyString(), anyString(),
                contains("heat:7:"));
    }

    @Test
    @DisplayName("폭염주의보가 아니면 쉼터 조회·발송 없이 skip")
    void heatEscapeSkipsWhenNotAdvisory() {
        NotificationRule rule = NotificationRule.of(10L, NotificationRuleType.HEAT_ESCAPE, 37.5, 127.0, null);
        setId(rule, 7L);
        when(ruleRepository.findByUserIdAndTypeAndActiveTrue(10L, NotificationRuleType.HEAT_ESCAPE))
                .thenReturn(List.of(rule));

        Weather weather = mock(Weather.class);
        when(weatherService.getWeather(37.5, 127.0)).thenReturn(Optional.of(weather));

        try (MockedStatic<HeatComfort> heatComfort = mockStatic(HeatComfort.class)) {
            heatComfort.when(() -> HeatComfort.isHeatAdvisory(weather)).thenReturn(false);

            service.evaluateHeatEscape(10L);
        }

        verify(placeRepository, never()).findNearest(anyDouble(), anyDouble(), anyString(), anyInt());
        verify(deliveryRepository, never()).insertHeatEscape(anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("폭염주의보여도 근처 쉼터가 없으면 발송 없이 skip")
    void heatEscapeSkipsWhenNoShelter() {
        NotificationRule rule = NotificationRule.of(10L, NotificationRuleType.HEAT_ESCAPE, 37.5, 127.0, null);
        setId(rule, 7L);
        when(ruleRepository.findByUserIdAndTypeAndActiveTrue(10L, NotificationRuleType.HEAT_ESCAPE))
                .thenReturn(List.of(rule));

        Weather weather = mock(Weather.class);
        when(weatherService.getWeather(37.5, 127.0)).thenReturn(Optional.of(weather));
        when(placeRepository.findNearest(37.5, 127.0, "COOLING_SHELTER", 1)).thenReturn(List.of());

        try (MockedStatic<HeatComfort> heatComfort = mockStatic(HeatComfort.class)) {
            heatComfort.when(() -> HeatComfort.isHeatAdvisory(weather)).thenReturn(true);

            service.evaluateHeatEscape(10L);
        }

        verify(deliveryRepository, never()).insertHeatEscape(anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("활성 HEAT_ESCAPE 규칙이 없으면 날씨 조회조차 하지 않는다")
    void heatEscapeNoRulesDoesNothing() {
        when(ruleRepository.findByUserIdAndTypeAndActiveTrue(10L, NotificationRuleType.HEAT_ESCAPE))
                .thenReturn(List.of());

        service.evaluateHeatEscape(10L);

        verify(weatherService, never()).getWeather(anyDouble(), anyDouble());
    }
}
