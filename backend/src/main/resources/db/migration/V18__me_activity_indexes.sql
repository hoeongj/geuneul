-- N9 대규모 대비 — EXPLAIN 재튜닝(ADR-0025). N6 "내 글 관리"의 유저필터 조회가 user_id 인덱스가 없어
-- seq scan이던 것을 인덱스 경로로 바꾼다. 실 PostGIS EXPLAIN 증빙: perf/explain/n9-me-activity-tuning.txt.
--   · /me/comments : review_comments를 c.user_id로 필터 → 인덱스 없어 Seq Scan(전 테이블).
--   · /me/reactions: reactions를 rx.user_id로 필터 → 인덱스 없어 Seq Scan.
-- (/me/reviews는 idx_reviews_user(V6), /me/following은 idx_follows_follower(V17)로 이미 인덱스 경로라 무변경.)
-- 테이블이 커질수록 seq scan 비용은 선형으로 늘지만 인덱스는 log에 머문다 → "대규모 대비"의 핵심 튜닝.

-- 내 댓글: WHERE user_id + ORDER BY created_at DESC → 복합 인덱스로 필터+정렬 동시 커버(별도 sort 제거).
CREATE INDEX idx_review_comments_user ON review_comments (user_id, created_at DESC);

-- 내 유용해요: WHERE user_id AND target_type='REVIEW' + ORDER BY created_at DESC → 등치+정렬 커버.
CREATE INDEX idx_reactions_user ON reactions (user_id, target_type, created_at DESC);
