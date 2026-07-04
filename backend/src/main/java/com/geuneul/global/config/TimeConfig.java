package com.geuneul.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 시간 소스를 빈으로 노출 — 시간 의존 로직(제보 TTL·만료 필터, 레이트리밋 창)이 now()를 직접
 * 호출하지 않고 이 Clock을 주입받아, 테스트에서 결정적으로 시간을 제어할 수 있게 한다.
 */
@Configuration
public class TimeConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
