package com.geuneul.global.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.geuneul.domain.report.dto.PopularTimesSlot;
import com.geuneul.domain.weather.Weather;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.type.TypeFactory;

import java.time.Duration;
import java.util.List;

/**
 * Redis 캐시 구성 — P3 날씨 TTL 캐시(docs/SPEC.md §7) + AI 요약 캐시(P3, 곁다리).
 *
 * 설계 결정(WORKLOG 기록):
 * - 값 직렬화는 **타입 바인드 JSON**(JacksonJsonRedisSerializer&lt;Weather&gt;) — "weather" 캐시는 Weather만
 *   담으므로 대상 타입을 못 박는다. GenericJackson(무타이핑)은 @class 없이 저장해 GET 시 LinkedHashMap으로
 *   역직렬화 → 캐스트 실패(500)했다(TS-011). 타입 바인드면 @class 없이도 정확히 Weather로 복원되고
 *   폴리모픽 default typing의 보안 우려도 없다. redis-cli로 값을 눈으로 볼 수 있는 것도 유지.
 * - null(빈 결과)은 캐시하지 않는다(disableCachingNullValues) — 클라이언트에서 실패는 unless로도 거르지만
 *   이중 방어. 일시 장애가 TTL 동안 굳는 걸 막는다.
 * - **캐시 장애를 삼킨다(CacheErrorHandler)**: Redis가 죽거나(ElastiCache 프로비저닝 전/네트워크 단절)
 *   미프로비저닝이어도 캐시 오류로 요청이 실패하지 않고 원본 로직(기상청 직접 호출/AI 미사용)으로 우회한다.
 *   캐시는 가용성을 낮추면 안 되는 부가 계층이라는 원칙.
 * - TTL 30분(weather): 초단기실황은 매시각 갱신이라 발표시각(캐시 키)이 매시각 바뀐다. 30분이면 같은 슬롯 내
 *   재조회를 막아 rate limit을 아끼면서, 키가 넘어가면 자연히 새 값으로 교체된다.
 * - TTL 3시간(aiSummary): AI 한줄 요약(AiSummaryService)은 장소별로 캐시해 상세 조회마다 호출하지 않는다.
 *   지시 범위(1~6h)의 중간값 — 너무 짧으면 비용 방어 효과가 작고, 너무 길면 새 제보가 와도 요약이 안 바뀐다.
 *   값은 String(Jackson으로 감싸 타입 바인드) — weather와 동일하게 무타이핑 캐스트 실패(TS-011)를 피한다.
 */
@Configuration
@EnableCaching
public class RedisCacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheConfig.class);

    public static final String WEATHER_CACHE = "weather";
    public static final String AI_SUMMARY_CACHE = "aiSummary";
    public static final String POPULAR_TIMES_CACHE = "popularTimes";
    private static final Duration WEATHER_TTL = Duration.ofMinutes(30);
    private static final Duration AI_SUMMARY_TTL = Duration.ofHours(3);
    // 시간대별 혼잡 파생(popular-times, ADR-0005 §④)은 "과거 이력의 요일×시간 분포"라 느리게 변한다 —
    // 상세 조회마다 group-by를 돌리지 않게 장소별로 1시간 캐시(새 제보/숨김의 반영 지연은 최대 TTL, 허용).
    private static final Duration POPULAR_TIMES_TTL = Duration.ofHours(1);

    /** "weather" 캐시 값 직렬화기 — 타입을 Weather로 바인드(테스트가 왕복을 검증할 수 있게 노출). */
    public static JacksonJsonRedisSerializer<Weather> weatherValueSerializer() {
        return new JacksonJsonRedisSerializer<>(Weather.class);
    }

    /** "aiSummary" 캐시 값 직렬화기 — 타입을 String으로 바인드(TS-011과 동일 사유로 GenericJackson 배제). */
    public static JacksonJsonRedisSerializer<String> aiSummaryValueSerializer() {
        return new JacksonJsonRedisSerializer<>(String.class);
    }

    /**
     * "popularTimes" 캐시 값 직렬화기 — {@code List<PopularTimesSlot>}를 정확한 JavaType으로 바인드한다.
     * 제네릭 List라 GenericJackson(무타이핑)이면 GET 시 LinkedHashMap 리스트로 복원돼 캐스트 실패(TS-011/012)
     * 하므로, TypeFactory로 원소 타입까지 못 박는다. 왕복은 RedisCacheConfigTest가 검증(Redis 없이).
     */
    @SuppressWarnings("unchecked")
    public static JacksonJsonRedisSerializer<List<PopularTimesSlot>> popularTimesValueSerializer() {
        JavaType type = TypeFactory.createDefaultInstance()
                .constructCollectionType(List.class, PopularTimesSlot.class);
        return (JacksonJsonRedisSerializer<List<PopularTimesSlot>>) (JacksonJsonRedisSerializer<?>)
                new JacksonJsonRedisSerializer<>(type);
    }

    @Bean
    RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration weather = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(WEATHER_TTL)
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(weatherValueSerializer()));
        RedisCacheConfiguration aiSummary = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(AI_SUMMARY_TTL)
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(aiSummaryValueSerializer()));
        RedisCacheConfiguration popularTimes = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(POPULAR_TIMES_TTL)
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(popularTimesValueSerializer()));
        return RedisCacheManager.builder(connectionFactory)
                .withCacheConfiguration(WEATHER_CACHE, weather)
                .withCacheConfiguration(AI_SUMMARY_CACHE, aiSummary)
                .withCacheConfiguration(POPULAR_TIMES_CACHE, popularTimes)
                .build();
    }

    /** Redis 불가 시 캐시 계층을 우회(로그만) — 가용성을 캐시에 의존시키지 않는다. */
    @Override
    public CacheErrorHandler errorHandler() {
        return new SimpleCacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("[cache] get 우회({} key={}): {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("[cache] put 우회({} key={}): {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("[cache] evict 우회({} key={}): {}", cache.getName(), key, e.getMessage());
            }
        };
    }
}
