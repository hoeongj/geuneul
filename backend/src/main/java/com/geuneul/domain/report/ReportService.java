package com.geuneul.domain.report;

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
    private final Clock clock;

    public ReportService(ReportRepository reportRepository, PlaceRepository placeRepository, Clock clock) {
        this.reportRepository = reportRepository;
        this.placeRepository = placeRepository;
        this.clock = clock;
    }

    @Transactional
    public ReportResponse create(ReportCreateRequest request) {
        requirePlace(request.placeId());
        // expires_at = 타입별 TTL(ReportType 주석 참고) — 만료된 제보는 조회·스코어에서 빠진다.
        OffsetDateTime expiresAt = OffsetDateTime.now(clock).plus(request.reportType().ttl());
        Report saved = reportRepository.save(Report.anonymous(
                request.placeId(), request.reportType(), normalize(request.comment()),
                request.anonymousOrDefault(), expiresAt));
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
