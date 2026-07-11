package com.geuneul.domain.alert;

import com.geuneul.domain.alert.dto.SurgeInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 제보 급증 감지 서비스(ADR-0016). "최근 windowMinutes 분 안에 유효 제보 ≥ minReports 건"인 장소를
 * 시공간 SQL로 판정한다(간판=DB 레이어, docs/SPEC.md §5). 임계값은 설정으로 빼 현장 시딩 밀도에 맞춰 조정(P5).
 */
@Service
@Transactional(readOnly = true)
public class ReportSurgeService {

    private final ReportSurgeRepository repository;
    private final int windowMinutes;
    private final int minReports;

    public ReportSurgeService(ReportSurgeRepository repository,
                              @Value("${geuneul.realtime.surge-window-minutes:10}") int windowMinutes,
                              @Value("${geuneul.realtime.surge-min-reports:3}") int minReports) {
        this.repository = repository;
        this.windowMinutes = windowMinutes;
        this.minReports = minReports;
    }

    /** 이 장소가 지금 급증 상태인가(시간창 내 유효제보 ≥ 임계). */
    public boolean isSurging(long placeId) {
        return repository.countRecent(placeId, windowMinutes) >= minReports;
    }

    /** 이 장소의 급증 요약 — 급증이 아니면 빈 Optional. 리스너가 SSE 이벤트를 조립할 때 쓴다. */
    public Optional<SurgeInfo> surgeForPlace(long placeId) {
        return repository.findSurge(placeId, windowMinutes, minReports).map(SurgeInfo::of);
    }

    /** bounds 뷰포트 내 급증 장소 목록(폴백/스냅샷, GET /alerts/surge). */
    public List<SurgeInfo> surgingInBounds(double west, double south, double east, double north, int limit) {
        return repository.findSurgingInBounds(west, south, east, north, windowMinutes, minReports, limit)
                .stream().map(SurgeInfo::of).toList();
    }
}
