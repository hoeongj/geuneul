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
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * Redis 캐시 구성 — P3 날씨 TTL 캐시(CLAUDE.md §7). 지금은 "weather" 캐시만 쓴다.
 *
 * 설계 결정(WORKLOG 기록):
 * - 값 직렬화는 JSON(GenericJackson2JsonRedisSerializer) — 키/값을 redis-cli로 눈으로 볼 수 있고,
 *   record(Weather) 스키마가 바뀌어도 JDK 직렬화처럼 깨지지 않는다.
 * - null(빈 결과)은 캐시하지 않는다(disableCachingNullValues) — 클라이언트에서 실패는 unless로도 거르지만
 *   이중 방어. 일시 장애가 TTL 동안 굳는 걸 막는다.
 * - **캐시 장애를 삼킨다(CacheErrorHandler)**: Redis가 죽거나(ElastiCache 프로비저닝 전/네트워크 단절)
 *   미프로비저닝이어도 캐시 오류로 요청이 실패하지 않고 원본 로직(기상청 직접 호출)로 우회한다.
 *   캐시는 가용성을 낮추면 안 되는 부가 계층이라는 원칙.
 * - TTL 30분: 초단기실황은 매시각 갱신이라 발표시각(캐시 키)이 매시각 바뀐다. 30분이면 같은 슬롯 내
 *   재조회를 막아 rate limit을 아끼면서, 키가 넘어가면 자연히 새 값으로 교체된다.
 */
@Configuration
@EnableCaching
public class RedisCacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheConfig.class);

    public static final String WEATHER_CACHE = "weather";
    private static final Duration WEATHER_TTL = Duration.ofMinutes(30);

    @Bean
    RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration weather = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(WEATHER_TTL)
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(GenericJacksonJsonRedisSerializer.builder().build()));
        return RedisCacheManager.builder(connectionFactory)
                .withCacheConfiguration(WEATHER_CACHE, weather)
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
