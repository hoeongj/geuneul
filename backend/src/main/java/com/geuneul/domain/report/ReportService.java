package com.geuneul.domain.report;

import com.geuneul.domain.auth.JwtService;
import com.geuneul.domain.auth.TrustScoreService;
import com.geuneul.domain.place.PlaceRepository;
import com.geuneul.domain.report.dto.ReportCreateRequest;
import com.geuneul.domain.report.dto.ReportResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@Transactional(readOnly = true)
public class ReportService {

    private final ReportRepository reportRepository;
    private final PlaceRepository placeRepository;
    private final TrustScoreService trustScoreService;
    private final Clock clock;

    public ReportService(ReportRepository reportRepository, PlaceRepository placeRepository,
                         TrustScoreService trustScoreService, Clock clock) {
        this.reportRepository = reportRepository;
        this.placeRepository = placeRepository;
        this.trustScoreService = trustScoreService;
        this.clock = clock;
    }

    /**
     * @param principal 로그인 시 JWT에서 뽑은 주체(비로그인이면 null — POST /reports는 permitAll이라
     *                  SecurityConfig가 강제하지 않는다, ReviewService.create의 "요청 바디로 안 받는다"와 동일 원칙).
     */
    @Transactional
    public ReportResponse create(JwtService.AuthPrincipal principal, ReportCreateRequest request) {
        requirePlace(request.placeId());
        Long userId = principal == null ? null : principal.userId();
        // 비로그인이면 무조건 익명(신원 자체가 없음). 로그인 유저는 "익명으로 표시" 선택과 무관하게
        // userId는 기록해 trust_score 가중을 유지한다(CLAUDE.md §6, Report.of 주석 참고).
        boolean anonymousFlag = userId == null || request.anonymousOrDefault();
        // expires_at = 타입별 TTL(ReportType 주석 참고) — 만료된 제보는 조회·스코어에서 빠진다.
        OffsetDateTime expiresAt = OffsetDateTime.now(clock).plus(request.reportType().ttl());
        Report saved = reportRepository.save(Report.of(
                userId, request.placeId(), request.reportType(), normalize(request.comment()),
                request.photoUrl(), anonymousFlag, expiresAt));
        if (userId != null) {
            trustScoreService.recalculate(userId);
        }
        return ReportResponse.of(saved);
    }

    /** 장소의 유효(미만료) 제보 최신순 — 상세 화면 "최근 제보". */
    public List<ReportResponse> recentByPlace(long placeId) {
        requirePlace(placeId);
        return reportRepository
                .findTop20ByPlaceIdAndExpiresAtAfterOrderByCreatedAtDesc(placeId, OffsetDateTime.now(clock))
                .stream()
                .map(ReportResponse::of)
                .toList();
    }

    private void requirePlace(long placeId) {
        if (!placeRepository.existsById(placeId)) {
            throw new ResponseStatusException(NOT_FOUND, "place not found: " + placeId);
        }
    }

    private static String normalize(String comment) {
        if (comment == null) return null;
        String trimmed = comment.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
