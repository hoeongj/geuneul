package com.geuneul.domain.notification;

import com.geuneul.domain.auth.JwtService;
import com.geuneul.domain.notification.dto.NotificationResponse;
import com.geuneul.domain.notification.dto.NotificationRuleRequest;
import com.geuneul.domain.notification.dto.NotificationRuleResponse;
import com.geuneul.domain.notification.dto.NotificationRuleToggleRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 알림 API(B1, ADR-0018) — 규칙 CRUD + 알림 센터. 전부 로그인 필요(SecurityConfig 보호). 개인화(살).
 */
@Tag(name = "Notifications", description = "알림 규칙 + 인앱 알림 센터 — 로그인 필요")
@RestController
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Operation(summary = "알림 규칙 생성 (로그인 필요)", description = "SURGE_NEARBY는 lat/lng/radiusM 필수.")
    @PostMapping("/notifications/rules")
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationRuleResponse createRule(@AuthenticationPrincipal JwtService.AuthPrincipal principal,
                                               @Valid @RequestBody NotificationRuleRequest request) {
        return notificationService.createRule(principal.userId(), request);
    }

    @Operation(summary = "내 알림 규칙 목록 (로그인 필요)")
    @GetMapping("/notifications/rules")
    public List<NotificationRuleResponse> listRules(@AuthenticationPrincipal JwtService.AuthPrincipal principal) {
        return notificationService.listRules(principal.userId());
    }

    @Operation(summary = "알림 규칙 활성 토글 (로그인 필요)")
    @PatchMapping("/notifications/rules/{id}")
    public NotificationRuleResponse toggle(@AuthenticationPrincipal JwtService.AuthPrincipal principal,
                                           @PathVariable long id,
                                           @Valid @RequestBody NotificationRuleToggleRequest request) {
        return notificationService.setActive(principal.userId(), id, request.active());
    }

    @Operation(summary = "알림 규칙 삭제 (로그인 필요)")
    @DeleteMapping("/notifications/rules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRule(@AuthenticationPrincipal JwtService.AuthPrincipal principal, @PathVariable long id) {
        notificationService.deleteRule(principal.userId(), id);
    }

    @Operation(summary = "알림 센터 — 발송 이력 + 안읽음 수 (로그인 필요)")
    @GetMapping("/notifications")
    public NotificationResponse list(@AuthenticationPrincipal JwtService.AuthPrincipal principal) {
        return notificationService.list(principal.userId());
    }

    @Operation(summary = "알림 전체 읽음 (로그인 필요)")
    @PostMapping("/notifications/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll(@AuthenticationPrincipal JwtService.AuthPrincipal principal) {
        notificationService.markRead(principal.userId(), null);
    }

    @Operation(summary = "알림 1건 읽음 (로그인 필요)")
    @PostMapping("/notifications/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@AuthenticationPrincipal JwtService.AuthPrincipal principal, @PathVariable long id) {
        notificationService.markRead(principal.userId(), id);
    }
}
