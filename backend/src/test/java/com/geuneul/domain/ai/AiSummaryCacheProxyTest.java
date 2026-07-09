package com.geuneul.domain.ai;

import com.geuneul.domain.report.Report;
import com.geuneul.domain.report.ReportRepository;
import com.geuneul.domain.report.ReportType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @Cacheable 프록시를 실제로 태우는 회귀 테스트 (WeatherCacheProxyTest와 같은 취지, TS-011 재발 방지).
 *
 * AiSummaryServiceTest는 서비스를 직접 호출해 캐시 프록시를 우회한다 → Optional 언랩 후 unless SpEL이
 * 실제로는 평가되지 않는 함정을 놓칠 수 있다. 이 테스트는 프록시된 빈을 통해 present 결과를 캐시하고
 * (예외 없이), 2회차가 ReportRepository/ChatCompletionClient 재호출 없이 캐시에서 오는지 확인한다.
 */
class AiSummaryCacheProxyTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-10T12:00:00Z"), ZoneOffset.UTC);

    @Configuration
    @EnableCaching
    static class CacheConfig {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("aiSummary");
        }
    }

    @Test
    @DisplayName("present 결과는 SpEL 오류 없이 캐시되고, 2회차는 리포지토리/클라이언트 재호출 없이 캐시 히트")
    void presentResultCachedWithoutSpelError() {
        ReportRepository reportRepository = mock(ReportRepository.class);
        ChatCompletionClient client = mock(ChatCompletionClient.class);
        Report r = Report.of(null, 1L, ReportType.COOL, null, null, false, false,
                OffsetDateTime.now(CLOCK).plusHours(1));
        ReflectionTestUtils.setField(r, "createdAt", OffsetDateTime.now(CLOCK).minusMinutes(10));
        when(reportRepository.findTop20ByPlaceIdAndExpiresAtAfterOrderByCreatedAtDesc(eq(1L), any()))
                .thenReturn(List.of(r));
        when(client.complete(anyString(), anyString())).thenReturn(Optional.of("최근 제보 기준 시원해요"));

        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.registerBean(AiSummaryService.class, () -> new AiSummaryService(reportRepository, client, CLOCK));
            ctx.register(CacheConfig.class);
            ctx.refresh();

            AiSummaryService service = ctx.getBean(AiSummaryService.class);
            Optional<String> first = service.summarize(1L);
            Optional<String> second = service.summarize(1L); // 캐시 히트(리포지토리/클라이언트 호출 없음)

            assertThat(first).contains("최근 제보 기준 시원해요");
            assertThat(second).contains("최근 제보 기준 시원해요");
            verify(reportRepository, times(1))
                    .findTop20ByPlaceIdAndExpiresAtAfterOrderByCreatedAtDesc(eq(1L), any());
            verify(client, times(1)).complete(anyString(), anyString());
        }
    }

    @Test
    @DisplayName("empty 결과(제보 없음)는 캐시하지 않는다 — 다음 호출에서 다시 리포지토리를 조회한다")
    void emptyResultNotCached() {
        ReportRepository reportRepository = mock(ReportRepository.class);
        ChatCompletionClient client = mock(ChatCompletionClient.class);
        when(reportRepository.findTop20ByPlaceIdAndExpiresAtAfterOrderByCreatedAtDesc(eq(2L), any()))
                .thenReturn(List.of());

        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.registerBean(AiSummaryService.class, () -> new AiSummaryService(reportRepository, client, CLOCK));
            ctx.register(CacheConfig.class);
            ctx.refresh();

            AiSummaryService service = ctx.getBean(AiSummaryService.class);
            service.summarize(2L);
            service.summarize(2L);

            verify(reportRepository, times(2))
                    .findTop20ByPlaceIdAndExpiresAtAfterOrderByCreatedAtDesc(eq(2L), any());
        }
    }
}
