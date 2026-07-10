package com.geuneul.domain.push.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 브라우저 PushSubscription 구독 요청(F2) — 브라우저 {@code pushManager.subscribe()} 결과를 그대로 보낸다.
 * 구조: {@code { endpoint, keys: { p256dh, auth } }}.
 */
@Schema(description = "Web Push 구독 등록 요청(브라우저 PushSubscription)")
public record PushSubscribeRequest(
        @Schema(description = "push 서비스 endpoint URL") @NotBlank String endpoint,
        @NotNull @Valid Keys keys) {

    @Schema(description = "구독 암호화 키")
    public record Keys(
            @Schema(description = "구독 공개키(P-256 ECDH)") @NotBlank String p256dh,
            @Schema(description = "auth secret") @NotBlank String auth) {
    }
}
