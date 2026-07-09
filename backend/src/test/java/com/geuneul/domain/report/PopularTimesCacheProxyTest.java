package com.geuneul.domain.report;

import com.geuneul.domain.auth.TrustScoreService;
import com.geuneul.domain.place.PlaceRepository;
import com.geuneul.domain.report.dto.PopularTimesSlot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * popular-times @Cacheable 프록시 회귀 테스트(P4, WeatherCacheProxyTest와 같은 취지) — 2회차가
 * 리포지토리(group-by 쿼리) 재호출 없이 캐시에서 오는지 확인한다. 직렬화 왕복은 RedisCacheConfigTest가 담당.
 */
class PopularTimesCacheProxyTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-10T12:00:00Z"), ZoneOffset.UTC);

    @Configuration
    @EnableCaching
    static class CacheConfig {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("popularTimes");
        }
    }

    @Test
    @DisplayName("popularTimes 2회 호출 시 집계 쿼리는 1회만 — 2회차는 캐시 히트")
    void secondCallHitsCache() {
        ReportRepository reportRepository = mock(ReportRepository.class);
        PlaceRepository placeRepository = mock(PlaceRepository.class);
        TrustScoreService trustScoreService = mock(TrustScoreService.class);

        PlaceCongestionSlotView slot = mock(PlaceCongestionSlotView.class);
        when(slot.getDow()).thenReturn(6);
        when(slot.getHour()).thenReturn(14);
        when(slot.getSampleCount()).thenReturn(4L);
        when(slot.getCrowdedCount()).thenReturn(3L);
        when(slot.getSeatOkCount()).thenReturn(1L);
        when(placeRepository.existsById(1L)).thenReturn(true);
        when(reportRepository.congestionByPlace(1L)).thenReturn(List.of(slot));

        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.registerBean(ReportService.class,
                    () -> new ReportService(reportRepository, placeRepository, trustScoreService, CLOCK));
            ctx.register(CacheConfig.class);
            ctx.refresh();

            ReportService service = ctx.getBean(ReportService.class);
            List<PopularTimesSlot> first = service.popularTimes(1L);
            List<PopularTimesSlot> second = service.popularTimes(1L); // 캐시 히트

            assertThat(first).hasSize(1);
            assertThat(first.get(0).level()).isEqualTo("BUSY");
            assertThat(second).isEqualTo(first);
            verify(reportRepository, times(1)).congestionByPlace(eq(1L));
            verify(placeRepository, times(1)).existsById(anyLong());   // 메서드 전체가 캐시되므로 검증도 1회
        }
    }
}
