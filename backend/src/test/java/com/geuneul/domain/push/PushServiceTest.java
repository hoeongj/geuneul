package com.geuneul.domain.push;

import com.zerodeplibs.webpush.VAPIDKeyPair;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PushService 단위테스트(F2) — DB·네트워크 없이 로컬에서 항상 돈다(TS-009 무관).
 * 실제 암호화 전송(push 서비스로의 HTTP)은 실기기 구독이 있어야라 여기선 활성/비활성 분기·구독 저장만 굳힌다.
 * 핵심 계약: push.enabled=false(=VAPID 빈 없음)일 때 sendToUser가 조용히 no-op → 인앱 센터 회귀 0(ADR-0022).
 */
class PushServiceTest {

    private final PushSubscriptionRepository repository = mock(PushSubscriptionRepository.class);
    private final PushSubscriptionCleaner cleaner = mock(PushSubscriptionCleaner.class);

    @SuppressWarnings("unchecked")
    private PushService serviceWith(VAPIDKeyPair keyPairOrNull) {
        ObjectProvider<VAPIDKeyPair> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(keyPairOrNull);
        return new PushService(repository, provider, cleaner, "mailto:test@geuneul.app");
    }

    @Test
    @DisplayName("VAPID 키가 없으면(push 비활성) enabled=false")
    void disabledWhenNoKey() {
        assertThat(serviceWith(null).enabled()).isFalse();
    }

    @Test
    @DisplayName("VAPID 키가 있으면 enabled=true")
    void enabledWhenKeyPresent() {
        assertThat(serviceWith(mock(VAPIDKeyPair.class)).enabled()).isTrue();
    }

    @Test
    @DisplayName("비활성이면 publicKey=null")
    void publicKeyNullWhenDisabled() {
        assertThat(serviceWith(null).publicKey()).isNull();
    }

    @Test
    @DisplayName("활성이면 키의 uncompressed base64url 공개키를 반환한다")
    void publicKeyFromKeyPair() {
        VAPIDKeyPair kp = mock(VAPIDKeyPair.class);
        when(kp.extractPublicKeyInUncompressedFormAsString()).thenReturn("BXYZ");

        assertThat(serviceWith(kp).publicKey()).isEqualTo("BXYZ");
    }

    @Test
    @DisplayName("subscribe는 endpoint 기준 upsert를 정확히 1번 부른다")
    void subscribeUpserts() {
        serviceWith(null).subscribe(10L, "https://ep", "p256", "auth");

        verify(repository).upsert(10L, "https://ep", "p256", "auth");
    }

    @Test
    @DisplayName("subscribe는 위험한 endpoint를 저장하지 않고 400으로 거부한다")
    void subscribeRejectsUnsafeEndpoint() {
        assertThatThrownBy(() -> serviceWith(null).subscribe(10L, "http://127.0.0.1/push", "p256", "auth"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");

        verify(repository, never()).upsert(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("비활성이면 sendToUser는 구독 조회조차 하지 않는다(no-op → 회귀 0)")
    void sendIsNoOpWhenDisabled() {
        serviceWith(null).sendToUser(10L, "제목", "내용", "/");

        verify(repository, never()).findByUserId(anyLong());
    }
}
