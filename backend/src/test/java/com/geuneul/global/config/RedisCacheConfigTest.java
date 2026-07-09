package com.geuneul.global.config;

import com.geuneul.domain.report.dto.PopularTimesSlot;
import com.geuneul.domain.weather.PrecipitationType;
import com.geuneul.domain.weather.Weather;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 캐시 값 직렬화 왕복 검증 (TS-011).
 *
 * 인메모리 캐시 테스트(WeatherCacheProxyTest)는 직렬화를 안 타므로, GenericJackson 무타이핑이
 * GET에서 LinkedHashMap을 돌려줘 캐스트 실패(500)한 버그를 못 잡았다. 이 테스트는 실제 캐시가 쓰는
 * 직렬화기로 Weather를 왕복시켜 정확히 Weather로 복원되는지(무타이핑이면 LinkedHashMap이라 실패) 못 박는다.
 */
class RedisCacheConfigTest {

    @Test
    @DisplayName("weather 캐시 직렬화기는 Weather를 JSON 왕복 후 그대로 복원한다")
    void weatherValueSerializerRoundTrips() {
        JacksonJsonRedisSerializer<Weather> serializer = RedisCacheConfig.weatherValueSerializer();
        Weather original = new Weather(29.3, 65, 0.0, PrecipitationType.RAIN, "202607091300");

        byte[] bytes = serializer.serialize(original);
        Weather restored = serializer.deserialize(bytes);

        assertThat(restored).isEqualTo(original); // record equals — LinkedHashMap이면 실패
    }

    @Test
    @DisplayName("popularTimes 캐시 직렬화기는 List<PopularTimesSlot>를 원소 타입까지 정확히 복원한다 (TS-011/012)")
    void popularTimesValueSerializerRoundTrips() {
        JacksonJsonRedisSerializer<List<PopularTimesSlot>> serializer =
                RedisCacheConfig.popularTimesValueSerializer();
        List<PopularTimesSlot> original = List.of(
                new PopularTimesSlot(6, 14, 12, 8, 2, "BUSY"),
                new PopularTimesSlot(1, 9, 3, 0, 2, "QUIET"));

        byte[] bytes = serializer.serialize(original);
        List<PopularTimesSlot> restored = serializer.deserialize(bytes);

        // 무타이핑이면 LinkedHashMap 리스트라 PopularTimesSlot equals가 실패한다.
        assertThat(restored).isEqualTo(original);
        assertThat(restored.get(0)).isInstanceOf(PopularTimesSlot.class);
        assertThat(restored.get(0).level()).isEqualTo("BUSY");
    }
}
