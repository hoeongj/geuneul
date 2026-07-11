package com.geuneul.domain.alert;

import com.geuneul.domain.alert.dto.SurgeInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SSE 구독자 레지스트리 단위테스트 — 등록/집계와 "죽은 emitter 정리" 경로를 검증한다.
 * (실제 SSE 송신 성공 경로는 서블릿 비동기 컨텍스트가 필요해 IT/수동 검증 영역 — 여기서는 레지스트리
 * 자체의 상태 관리만 본다.)
 */
class SurgeEmitterRegistryTest {

    @Test
    @DisplayName("register()는 구독자 수를 늘린다")
    void registerIncrementsCount() {
        SurgeEmitterRegistry registry = new SurgeEmitterRegistry();
        assertThat(registry.subscriberCount()).isZero();
        registry.register();
        registry.register();
        assertThat(registry.subscriberCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("broadcast는 송신 불가한(완료된) emitter를 레지스트리에서 제거한다(누수 방지)")
    void broadcastRemovesDeadEmitters() {
        SurgeEmitterRegistry registry = new SurgeEmitterRegistry();
        var emitter = registry.register();
        emitter.complete();   // 완료된(죽은) emitter — 이후 send는 IllegalStateException을 던진다
        assertThat(registry.subscriberCount()).isEqualTo(1);

        registry.broadcast(SurgeInfo.of(1L, "테스트", 37.5, 127.0, 3, "COOL"));

        // 송신 실패(IllegalStateException) → broadcast가 제거하여 0
        assertThat(registry.subscriberCount()).isZero();
    }

    @Test
    @DisplayName("completeAll은 모든 구독자를 비운다")
    void completeAllClears() {
        SurgeEmitterRegistry registry = new SurgeEmitterRegistry();
        registry.register();
        registry.register();
        registry.completeAll();
        assertThat(registry.subscriberCount()).isZero();
    }

    @Test
    @DisplayName("연결 상한을 넘으면 등록을 거부한다")
    void rejectsWhenConnectionLimitExceeded() {
        SurgeEmitterRegistry registry = new SurgeEmitterRegistry(2);

        assertThat(registry.tryRegister()).isPresent();
        assertThat(registry.tryRegister()).isPresent();
        assertThat(registry.tryRegister()).isEmpty();
        assertThat(registry.subscriberCount()).isEqualTo(2);
    }
}
