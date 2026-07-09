package com.geuneul.domain.weather;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * @Cacheable 프록시를 실제로 태우는 회귀 테스트 (TS-011).
 *
 * WeatherClientTest는 클라이언트를 직접 호출해 캐시 프록시를 우회한다 → `unless` SpEL이 평가되지 않아
 * "#result.isEmpty()" 버그(Optional 언랩 후 Weather에 isEmpty 호출)가 라이브에서야 500으로 드러났다.
 * 이 테스트는 프록시된 빈을 통해 present 결과를 캐시하고(예외 없이), 2회차가 HTTP 없이 캐시에서 오는지 확인한다.
 */
class WeatherCacheProxyTest {

    private static final String NCST_JSON = """
            {"response":{"header":{"resultCode":"00"},"body":{"items":{"item":[
              {"category":"T1H","obsrValue":"29.3"},
              {"category":"REH","obsrValue":"65"},
              {"category":"RN1","obsrValue":"강수없음"},
              {"category":"PTY","obsrValue":"0"}
            ]}}}}
            """;

    @Configuration
    @EnableCaching
    static class CacheConfig {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("weather");
        }
    }

    @Test
    @DisplayName("present 결과는 SpEL 오류 없이 캐시되고, 2회차는 HTTP 없이 캐시 히트")
    void presentResultCachedWithoutSpelError() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(ExpectedCount.once(), requestTo(containsString("/getUltraSrtNcst")))
                .andRespond(withSuccess(NCST_JSON, MediaType.APPLICATION_JSON));

        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.registerBean(WeatherClient.class, () -> new WeatherClient("test-key", builder));
            ctx.register(CacheConfig.class);
            ctx.refresh();

            WeatherClient client = ctx.getBean(WeatherClient.class);
            Optional<Weather> first = client.fetchNowcast(60, 127, "20260709", "1300");
            Optional<Weather> second = client.fetchNowcast(60, 127, "20260709", "1300"); // 캐시 히트(HTTP 없음)

            assertThat(first).isPresent();
            assertThat(first.get().temperatureC()).isEqualTo(29.3);
            assertThat(second).isPresent();
            assertThat(second.get().temperatureC()).isEqualTo(29.3);
            server.verify(); // once() — 2회차가 캐시에서 왔음을 증명
        }
    }
}
