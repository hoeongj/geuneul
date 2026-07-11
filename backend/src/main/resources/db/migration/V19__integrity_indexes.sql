-- 무결성 보강(B7/B11): 리뷰 자연키, push FK, 알림 규칙 활성 타입 인덱스.

-- 같은 유저가 같은 장소에 남긴 중복 리뷰는 최신 updated_at, 동률이면 최대 id만 남긴다.
-- hidden 여부와 무관하게 자연키 정책(user_id, place_id) 기준으로 정리한다.
WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY user_id, place_id
               ORDER BY updated_at DESC, id DESC
           ) AS rn
    FROM reviews
)
DELETE FROM reviews r
USING ranked d
WHERE r.id = d.id
  AND d.rn > 1;

CREATE UNIQUE INDEX uq_reviews_user_place ON reviews (user_id, place_id);

-- 기존 orphan push 구독을 먼저 제거한 뒤 user FK를 추가한다.
DELETE FROM push_subscriptions ps
WHERE NOT EXISTS (
    SELECT 1 FROM users u WHERE u.id = ps.user_id
);

ALTER TABLE push_subscriptions
    ADD CONSTRAINT fk_push_subscriptions_user
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

CREATE INDEX idx_notification_rules_active_type
    ON notification_rules (type)
    WHERE is_active;
