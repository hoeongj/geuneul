-- Web Push 구독(F2, ADR-0022) — 브라우저 push 구독을 유저별로 저장한다.
-- 인앱 알림 센터(B1·V15)와 별개 채널: 발송 이력은 notification_deliveries, OS 배너 전송 대상은 여기.
-- endpoint = 브라우저별 push 서비스 URL(고유). 같은 기기 재구독 시 endpoint UNIQUE로 upsert(중복 방지).
CREATE TABLE push_subscriptions (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    endpoint   TEXT        NOT NULL UNIQUE,
    p256dh     TEXT        NOT NULL,   -- 구독 공개키(클라 → 페이로드 암호화용)
    auth       TEXT        NOT NULL,   -- 구독 auth secret
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 유저의 구독 조회(발송 시 fan-out) — 유저당 기기 여러 개 가능.
CREATE INDEX idx_push_subscriptions_user ON push_subscriptions (user_id);
