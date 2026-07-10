package com.geuneul.domain.push;

import com.geuneul.domain.auth.JwtService;
import com.geuneul.domain.push.dto.PushSubscribeRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Web Push API(F2, ADR-0022) — 구독 등록 + 공개키 + 테스트 발송. 인앱 알림(B1)과 병렬 채널(살).
 * subscribe·test는 로그인 필요(SecurityConfig), public-key는 구독 전 필요라 공개.
 */
@Tag(name = "Web Push", description = "브라우저 OS 배너 푸시 — 구독/공개키/테스트")
@RestController
public class PushController {

    private final PushService pushService;

    public PushController(PushService pushService) {
        this.pushService = pushService;
    }

    @Operation(summary = "VAPID 공개키 + 활성 여부", description = "프론트 applicationServerKey용. 비활성이면 enabled=false.")
    @GetMapping("/push/public-key")
    public Map<String, Object> publicKey() {
        return Map.of("enabled", pushService.enabled(),
                "publicKey", pushService.publicKey() == null ? "" : pushService.publicKey());
    }

    @Operation(summary = "구독 등록 (로그인 필요)", description = "브라우저 pushManager.subscribe() 결과 저장(재구독 upsert).")
    @PostMapping("/push/subscribe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void subscribe(@AuthenticationPrincipal JwtService.AuthPrincipal principal,
                          @Valid @RequestBody PushSubscribeRequest req) {
        pushService.subscribe(principal.userId(), req.endpoint(), req.keys().p256dh(), req.keys().auth());
    }

    @Operation(summary = "내 기기로 테스트 발송 (로그인 필요)", description = "구독한 기기로 OS 배너 1회 — end-to-end 검증용.")
    @PostMapping("/push/test")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void test(@AuthenticationPrincipal JwtService.AuthPrincipal principal) {
        pushService.sendToUser(principal.userId(), "그늘 테스트 알림",
                "푸시가 정상 동작해요. 이제 폭염·급증 알림을 받을 수 있어요.", "/");
    }
}
