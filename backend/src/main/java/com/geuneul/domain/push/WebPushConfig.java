package com.geuneul.domain.push;

import com.zerodeplibs.webpush.VAPIDKeyPair;
import com.zerodeplibs.webpush.VAPIDKeyPairs;
import com.zerodeplibs.webpush.key.PrivateKeySources;
import com.zerodeplibs.webpush.key.PublicKeySources;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Web Push VAPID 키(F2, ADR-0022) — {@code push.enabled=true}일 때만 {@link VAPIDKeyPair} 빈을 만든다.
 *
 * <p>키는 PEM 텍스트(PKCS8 개인키 + uncompressed 공개키)를 env/SSM으로 주입한다(§D — 파일·레포에 금지).
 * zerodep는 JDK 내장 crypto만 쓰므로 BouncyCastle 없이 동작한다(ADR-0018이 F2를 미룬 사유 해소).
 *
 * <p>비활성(로컬/CI 기본)이면 빈이 없어 {@link PushService}가 no-op으로 폴백한다(회귀 0). 활성인데 키가
 * 비면 컨텍스트가 뜰 때 즉시 실패해(빠른 실패) 잘못 배포를 막는다 — 조용히 안 보내는 것보다 안전.
 */
@Configuration
@ConditionalOnProperty(name = "push.enabled", havingValue = "true")
public class WebPushConfig {

    @Bean
    public VAPIDKeyPair vapidKeyPair(
            @Value("${push.vapid.private-key-pem:}") String privateKeyPem,
            @Value("${push.vapid.public-key-pem:}") String publicKeyPem) {
        if (privateKeyPem.isBlank() || publicKeyPem.isBlank()) {
            throw new IllegalStateException(
                    "push.enabled=true 인데 VAPID 키(push.vapid.*-key-pem)가 없습니다. env/SSM으로 주입하세요(§D).");
        }
        return VAPIDKeyPairs.of(
                PrivateKeySources.ofPEMText(privateKeyPem),
                PublicKeySources.ofPEMText(publicKeyPem));
    }
}
