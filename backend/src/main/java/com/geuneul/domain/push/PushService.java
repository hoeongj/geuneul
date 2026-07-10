package com.geuneul.domain.push;

import com.zerodeplibs.webpush.PushSubscription;
import com.zerodeplibs.webpush.VAPIDKeyPair;
import com.zerodeplibs.webpush.httpclient.StandardHttpClientRequestPreparer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Web Push 전송(F2, ADR-0022) — 인앱 알림 센터(B1)와 병렬인 OS 배너 채널. 구독 저장 + VAPID 암호화 전송.
 *
 * <p><b>회귀 0 설계</b>: {@link VAPIDKeyPair} 빈은 {@code push.enabled=true}일 때만 존재({@link WebPushConfig}).
 * 없으면(로컬/CI·미배포) {@link #sendToUser}가 조용히 no-op → 인앱 delivery 경로는 그대로 산다. 구독 저장은
 * 키와 무관하게 동작한다(구독을 미리 받아두고 나중에 켜도 됨). 전송 실패(만료·네트워크)는 구독별로 격리하고,
 * 404/410(구독 소멸)이면 해당 endpoint를 정리한다.
 */
@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);

    private final PushSubscriptionRepository repository;
    private final ObjectProvider<VAPIDKeyPair> vapidKeyPair; // push.enabled=false면 비어 있음
    private final PushSubscriptionCleaner cleaner;
    private final String subject;
    // connect 타임아웃으로 죽은 endpoint가 오래 매달리지 않게 한다(요청 타임아웃은 요청별로 부여).
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    // ObjectMapper를 주입하지 않는다 — Boot 4는 Jackson 3(tools.jackson) 빈만 제공해
    // com.fasterxml.jackson(Jackson 2) ObjectMapper 빈이 없다(컨텍스트 부팅 실패, CI에서만 드러남 — TS-028).
    // 페이로드가 3필드라 버전 무관하게 직접 직렬화한다.
    public PushService(PushSubscriptionRepository repository,
                       ObjectProvider<VAPIDKeyPair> vapidKeyPair,
                       PushSubscriptionCleaner cleaner,
                       @Value("${push.vapid.subject:mailto:admin@geuneul.app}") String subject) {
        this.repository = repository;
        this.vapidKeyPair = vapidKeyPair;
        this.cleaner = cleaner;
        this.subject = subject;
    }

    /** 전송 활성 여부(키 존재). 프론트가 구독 UI를 켤지 판단하는 데도 쓴다. */
    public boolean enabled() {
        return vapidKeyPair.getIfAvailable() != null;
    }

    /** 프론트 applicationServerKey용 공개키(base64url uncompressed). 비활성이면 null. */
    public String publicKey() {
        VAPIDKeyPair keys = vapidKeyPair.getIfAvailable();
        return keys == null ? null : keys.extractPublicKeyInUncompressedFormAsString();
    }

    /** 구독 저장(재구독 upsert). 키 비활성이어도 저장은 한다(나중에 켜면 발송 대상). */
    @Transactional
    public void subscribe(long userId, String endpoint, String p256dh, String auth) {
        repository.upsert(userId, endpoint, p256dh, auth);
    }

    /**
     * 유저의 모든 기기에 push 전송. 비활성이면 no-op. <b>비동기 fire-and-forget</b> — push 서비스 왕복을
     * 요청 스레드에서 기다리지 않는다. 그래야 {@code POST /push/test} 등 호출부가 즉시 응답한다(동기 전송은
     * push 서비스 지연 시 클라이언트/CloudFront가 먼저 끊어 "Broken pipe"·프론트 오탐을 냈다 — TS-029).
     * 한 기기 실패는 격리하고, 404/410(구독 소멸)이면 별도 트랜잭션({@link PushSubscriptionCleaner})으로 정리한다.
     * 페이로드 = {title, body, url}(SW가 그대로 표시).
     */
    public void sendToUser(long userId, String title, String body, String url) {
        VAPIDKeyPair keys = vapidKeyPair.getIfAvailable();
        if (keys == null) {
            return; // push 비활성 — 인앱 센터만
        }
        List<PushSubscriptionEntity> subs = repository.findByUserId(userId);
        if (subs.isEmpty()) {
            return;
        }
        String payload = toPayload(title, body, url);
        for (PushSubscriptionEntity sub : subs) {
            String endpoint = sub.getEndpoint();
            try {
                HttpRequest request = buildRequest(keys, sub, payload);
                httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                        .thenAccept(res -> onResponse(endpoint, res.statusCode()))
                        .exceptionally(ex -> {
                            log.warn("[push] 비동기 전송 실패 endpoint(prefix)={} — {}", prefix(endpoint), ex.getMessage());
                            return null;
                        });
            } catch (Exception e) {
                log.warn("[push] 요청 조립 실패 endpoint(prefix)={} — {}", prefix(endpoint), e.getMessage());
            }
        }
    }

    private HttpRequest buildRequest(VAPIDKeyPair keys, PushSubscriptionEntity sub, String payload) {
        PushSubscription subscription = new PushSubscription();
        subscription.setEndpoint(sub.getEndpoint());
        PushSubscription.Keys k = new PushSubscription.Keys();
        k.setP256dh(sub.getP256dh());
        k.setAuth(sub.getAuth());
        subscription.setKeys(k);

        return StandardHttpClientRequestPreparer.getBuilder()
                .pushSubscription(subscription)
                .vapidJWTExpiresAfter(15, TimeUnit.MINUTES)
                .vapidJWTSubject(subject)
                .pushMessage(payload)
                .ttl(1, TimeUnit.HOURS)
                .build(keys)
                .toRequestBuilder()
                .timeout(Duration.ofSeconds(10)) // 죽은 push 서비스가 스레드를 오래 잡지 않게
                .build();
    }

    /** 비동기 응답 처리 — 404/410(구독 소멸)이면 정리(별도 트랜잭션), 그 외 4xx/5xx는 로그만. */
    private void onResponse(String endpoint, int code) {
        if (code == 404 || code == 410) {
            cleaner.remove(endpoint);
        } else if (code >= 400) {
            log.warn("[push] push 서비스 {}: endpoint(prefix)={}", code, prefix(endpoint));
        }
    }

    /** {title, body, url} JSON. Jackson 버전 결합을 피하려 3필드를 직접 직렬화(값은 JSON 문자열 이스케이프). */
    private static String toPayload(String title, String body, String url) {
        return "{\"title\":\"" + esc(title) + "\",\"body\":\"" + esc(body)
                + "\",\"url\":\"" + esc(url == null ? "/" : url) + "\"}";
    }

    /** JSON 문자열 값 이스케이프(공공데이터 장소명에 특수문자가 있어도 안전). */
    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private static String prefix(String endpoint) {
        return endpoint == null ? "" : endpoint.substring(0, Math.min(32, endpoint.length()));
    }
}
