package com.geuneul.domain.report;

import java.time.Instant;

/**
 * 관심 장소 상태 알림(C3, ADR-0026)의 유의미 제보 투영 — 타입 + 생성 시각. createdAt은 <b>Instant</b>다
 * (timestamptz 네이티브 프로젝션은 pgJDBC가 Instant를 주므로 OffsetDateTime로 받으면 언프로젝션 실패, TS-016).
 * onBookmarkStatus가 createdAt으로 dedup 버킷을 산정해(벽시계 아님) 같은 제보가 후속 이벤트로 재알림되는 것을 막는다.
 */
public interface MeaningfulReportView {
    String getReportType();

    Instant getCreatedAt();
}
