package com.geuneul.domain.notification.dto;

import com.geuneul.domain.notification.NotificationDelivery;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

/** 알림 센터 응답 — 발송 이력 목록 + 안읽음 수. 발송 문구는 §6대로 순화돼 있다(백엔드가 생성). */
@Schema(description = "알림 센터")
public record NotificationResponse(
        @Schema(description = "안읽음 수", example = "2") long unread,
        @Schema(description = "발송 목록(최신순)") List<Item> items
) {

    @Schema(description = "알림 1건")
    public record Item(
            @Schema(description = "발송 ID") long id,
            @Schema(description = "종류", example = "SURGE_NEARBY") String type,
            @Schema(description = "제목", example = "내 주변 제보 급증") String title,
            @Schema(description = "내용(§6 순화)", example = "노량진 지하보도 · 최근 침수 제보가 몰리고 있어요 · 우회를 권장해요") String body,
            @Schema(description = "관련 장소 ID", nullable = true) Long placeId,
            @Schema(description = "읽음 여부") boolean read,
            @Schema(description = "발송 시각") OffsetDateTime createdAt
    ) {
        public static Item of(NotificationDelivery d) {
            return new Item(d.getId(), d.getType(), d.getTitle(), d.getBody(), d.getPlaceId(),
                    d.isRead(), d.getCreatedAt());
        }
    }

    public static NotificationResponse of(List<NotificationDelivery> deliveries, long unread) {
        return new NotificationResponse(unread, deliveries.stream().map(Item::of).toList());
    }
}
