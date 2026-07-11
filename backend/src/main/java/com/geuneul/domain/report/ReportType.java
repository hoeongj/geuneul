package com.geuneul.domain.report;

import java.time.Duration;

/**
 * 휘발성 제보 타입 (docs/SPEC.md §8 reports.report_type).
 * ttl = 제보의 유효 수명 — expires_at 산정에 쓰이고, 지나면 조회/스코어에서 제외된다.
 * 수명은 상태의 변화 속도에 비례: 자리 여유(가장 빠름) < 체감온도 < 기상 리스크 < 벌레/냄새 < 시설 상태(느림).
 */
public enum ReportType {
    // 자리 여유/혼잡 — 가장 휘발성 큼(2h). "지금 앉을 수 있음"이 여름 실내 오래 버티기의 핵심 신호
    // (survival_score freshness로 굴러감, ADR-0005). 여름 프레임: 쉼터·도서관 붐빔/자리없음.
    SEAT_OK("자리 있어요", Duration.ofHours(2)),
    CROWDED("붐벼요", Duration.ofHours(2)),
    COOL("시원해요", Duration.ofHours(3)),
    HOT("더워요", Duration.ofHours(3)),
    BUG("벌레 많아요", Duration.ofHours(24)),
    ODOR("악취 나요", Duration.ofHours(24)),
    SMOKE("담배 냄새", Duration.ofHours(24)),
    FLOOD("침수됐어요", Duration.ofHours(12)),
    SLIPPERY("미끄러워요", Duration.ofHours(12)),
    WATER_OK("물 있어요", Duration.ofHours(72)),
    RESTROOM_CLEAN("화장실 깨끗", Duration.ofHours(72));

    private final String label;
    private final Duration ttl;

    ReportType(String label, Duration ttl) {
        this.label = label;
        this.ttl = ttl;
    }

    public String label() {
        return label;
    }

    public Duration ttl() {
        return ttl;
    }
}
