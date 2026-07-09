package com.geuneul.domain.alert.dto;

import com.geuneul.domain.alert.SurgingPlaceView;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 제보 급증 알림 1건(ADR-0016). "최근 N분 안에 유효 제보가 임계 이상 몰린 장소"를 나타낸다.
 * bounds 폴백 조회(GET /alerts/surge)와 SSE 실시간 푸시(GET /alerts/stream)가 같은 셰이프로 쓴다.
 *
 * <p>표현 규율(CLAUDE.md §6): 백엔드는 사실(placeId·count·대표 타입)만 싣고, message는 공포 조장이 아닌
 * 중립 문구로 만든다("위험!" 금지 → "제보가 몰리고 있어요"). 최종 톤·아이콘은 프론트가 렌더한다.
 */
@Schema(description = "제보 급증 알림")
public record SurgeInfo(
        @Schema(description = "장소 ID", example = "185") long placeId,
        @Schema(description = "장소 이름", example = "노량진 지하보도") String name,
        @Schema(description = "위도", example = "37.5140") double lat,
        @Schema(description = "경도", example = "126.9420") double lng,
        @Schema(description = "시간창 내 유효 제보 수", example = "4") long reportCount,
        @Schema(description = "가장 많이 올라온 제보 타입", example = "FLOOD") String topType,
        @Schema(description = "중립 안내 문구(공포 조장 금지, §6)", example = "최근 침수 제보가 몰리고 있어요 · 우회를 권장해요")
        String message
) {

    public static SurgeInfo of(SurgingPlaceView v) {
        return new SurgeInfo(
                v.getPlaceId(), v.getName(), v.getLat(), v.getLng(),
                v.getReportCount(), v.getTopType(), message(v.getTopType(), v.getReportCount()));
    }

    public static SurgeInfo of(long placeId, String name, double lat, double lng, long reportCount, String topType) {
        return new SurgeInfo(placeId, name, lat, lng, reportCount, topType, message(topType, reportCount));
    }

    /**
     * 대표 제보 타입 → 중립 안내 문구(§6: 공포 조장 금지). 위험 계열(침수·미끄럼)도 "위험!"이 아니라
     * "제보가 몰리고 있어요 · 우회 권장"으로 순화한다. 알 수 없는 타입은 일반 문구로 폴백.
     */
    static String message(String topType, long count) {
        String subject = switch (topType == null ? "" : topType) {
            case "FLOOD" -> "침수 제보가 몰리고 있어요 · 우회를 권장해요";
            case "SLIPPERY" -> "미끄럼 제보가 몰리고 있어요 · 조심하세요";
            case "BUG" -> "벌레 제보가 몰리고 있어요";
            case "ODOR", "SMOKE" -> "냄새 제보가 몰리고 있어요";
            case "CROWDED" -> "붐빈다는 제보가 몰리고 있어요";
            case "HOT" -> "덥다는 제보가 몰리고 있어요";
            case "COOL" -> "시원하다는 제보가 몰리고 있어요";
            case "SEAT_OK" -> "자리 있다는 제보가 몰리고 있어요";
            case "WATER_OK" -> "물 나온다는 제보가 몰리고 있어요";
            case "RESTROOM_CLEAN" -> "화장실 깨끗하다는 제보가 몰리고 있어요";
            default -> "제보가 몰리고 있어요";
        };
        return "최근 " + subject;
    }
}
