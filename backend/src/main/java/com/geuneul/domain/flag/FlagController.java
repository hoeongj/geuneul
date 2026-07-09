package com.geuneul.domain.flag;

import com.geuneul.domain.auth.JwtService;
import com.geuneul.domain.flag.dto.FlagCreateRequest;
import com.geuneul.domain.flag.dto.FlagResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 신고 접수 API (CLAUDE.md §0-7 모더레이션, §9). 로그인 필요 — SecurityConfig가 POST /flags를
 * 인증 요구로 보호하므로, 여기 도달했다면 principal은 항상 유효하다(ReviewController와 동일 패턴).
 * 검수 큐(관리자)는 별도 AdminFlagController.
 */
@Tag(name = "Flags", description = "허위 제보/후기 신고 — 로그인 필요, 같은 대상 중복 신고 방지")
@RestController
public class FlagController {

    private final FlagService flagService;

    public FlagController(FlagService flagService) {
        this.flagService = flagService;
    }

    @Operation(summary = "신고 접수 (로그인 필요)",
            description = "제보/후기를 신고한다. 같은 유저가 같은 대상을 다시 신고하면 409. "
                    + "대상이 존재하지 않으면 404. 비로그인은 401.")
    @PostMapping("/flags")
    @ResponseStatus(HttpStatus.CREATED)
    public FlagResponse create(@AuthenticationPrincipal JwtService.AuthPrincipal principal,
                               @Valid @RequestBody FlagCreateRequest request) {
        return flagService.create(principal.userId(), request);
    }
}
