package com.geuneul.domain.report.dto;

import com.geuneul.domain.report.PlaceCongestionSlotView;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 시간대별 혼잡 파생 한 슬롯(요일×시간)의 응답(ADR-0005 §④, 자체 popular-times).
 * 활동량(sampleCount)과 혼잡 등급(level)을 함께 싣는다 — 등급은 CROWDED vs SEAT_OK 순수 함수 유도.
 */
@Schema(description = "시간대별 혼잡 슬롯")
public record PopularTimesSlot(
        @Schema(description = "요일 0=일~6=토 (KST)", example = "6") int dow,
        @Schema(description = "시간 0~23 (KST)", example = "14") int hour,
        @Schema(description = "이 슬롯의 총 제보 수(활동량)", example = "12") long sampleCount,
        @Schema(description = "CROWDED 제보 수", example = "8") long crowdedCount,
        @Schema(description = "SEAT_OK 제보 수", example = "2") long seatOkCount,
        @Schema(description = "혼잡 등급", example = "BUSY", allowableValues = {"BUSY", "MODERATE", "QUIET", "UNKNOWN"})
        String level
) {

    public static PopularTimesSlot of(PlaceCongestionSlotView v) {
        return new PopularTimesSlot(
                v.getDow(), v.getHour(), v.getSampleCount(), v.getCrowdedCount(), v.getSeatOkCount(),
                level(v.getCrowdedCount(), v.getSeatOkCount()));
    }

    /**
     * 혼잡 등급(순수 함수) — CROWDED/SEAT_OK 상대 비율로 유도한다.
     * crowdScore = (crowded − seatOk) / (crowded + seatOk) ∈ [-1,1].
     * ≥ +1/3 → BUSY, ≤ −1/3 → QUIET, 그 사이 → MODERATE. 혼잡 신호가 아예 없으면(둘 다 0) UNKNOWN.
     * (§6 정신: 단정적 "만석/위험"이 아니라 상대 등급 — 표시 톤은 프론트가 렌더.)
     */
    static String level(long crowded, long seatOk) {
        long denom = crowded + seatOk;
        if (denom == 0) {
            return "UNKNOWN";
        }
        double crowdScore = (double) (crowded - seatOk) / denom;
        if (crowdScore >= 1.0 / 3) {
            return "BUSY";
        }
        if (crowdScore <= -1.0 / 3) {
            return "QUIET";
        }
        return "MODERATE";
    }
}
