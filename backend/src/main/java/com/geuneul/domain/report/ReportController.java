package com.geuneul.domain.report;

import com.geuneul.domain.report.dto.ReportCreateRequest;
import com.geuneul.domain.report.dto.ReportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

/**
 * 휘발성 제보 API (CLAUDE.md §9). MVP = 익명 제보 + 장소별 최근 제보 조회.
 * 사진 presign·로그인 신뢰도 가중·신고 큐는 P2 후속.
 */
@Tag(name = "Reports", description = "휘발성 상태 제보 — 익명 허용, expires_at 지나면 제외")
@RestController
public class ReportController {

    private final ReportService reportService;
    private final ReportRateLimiter rateLimiter;

    public ReportController(ReportService reportService, ReportRateLimiter rateLimiter) {
        this.reportService = reportService;
        this.rateLimiter = rateLimiter;
    }

    @Operation(summary = "제보 생성 (익명 허용)",
            description = "지금 상태를 제보한다. 타입별 TTL로 expires_at이 자동 산정된다. "
                    + "남용 방어: 클라이언트당 분당 3회·시간당 10회(초과 시 429).")
    @PostMapping("/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse create(@Valid @RequestBody ReportCreateRequest request, HttpServletRequest http) {
        if (!rateLimiter.tryAcquire(clientKey(http))) {
            throw new ResponseStatusException(TOO_MANY_REQUESTS, "제보가 너무 잦아요. 잠시 후 다시 시도해 주세요.");
        }
        return reportService.create(request);
    }

    @Operation(summary = "장소의 최근 제보", description = "유효(미만료) 제보 최신순 최대 20개.")
    @GetMapping("/places/{placeId}/reports")
    public List<ReportResponse> recent(@PathVariable long placeId) {
        return reportService.recentByPlace(placeId);
    }

    /**
     * 레이트리밋 키 = 클라이언트 IP. ALB·프론트 프록시(BFF)를 거치므로 X-Forwarded-For의
     * 최좌측(원 클라이언트)을 쓴다 — 프록시 뒤에서 remoteAddr만 보면 전 유저가 한 키로 뭉친다.
     */
    private static String clientKey(HttpServletRequest http) {
        String xff = http.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].strip();
        }
        return http.getRemoteAddr();
    }
}
