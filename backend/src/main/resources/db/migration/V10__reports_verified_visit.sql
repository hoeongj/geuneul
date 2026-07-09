-- GPS 방문 인증(ADR-0005 §④ "간판·차별점") — 제보자가 실제로 그 장소에 있었는지(ST_DWithin 100m)를
-- verified 플래그로 남겨 허위 제보를 억제한다. verified 제보는 place_report_signals 뷰에서 더 높은
-- 신뢰도로 가중된다(아래 CREATE OR REPLACE).
--
-- 기본값 false: 기존 제보·좌표 미제공 제보는 비검증으로 남아 기존 스코어가 그대로 유지된다(단조 개선 —
-- verified만 보너스, 비검증은 불변). 사진 EXIF 기반 보조 인증은 사진 바이트가 presign S3라 서버에 없어
-- 후속(이번은 GPS 근접만 — 앱이 보낸 제보자 좌표 vs 장소 좌표).

ALTER TABLE reports ADD COLUMN verified BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN reports.verified IS
    'GPS 방문 인증(ADR-0005 §④): 제보 시 제보자 좌표가 장소 100m 이내면 true. 허위제보 억제·신뢰도 가중.';

-- place_report_signals 뷰를 verified 가중이 반영되게 교체(V4 정의 + verified 보너스).
-- 변경점은 tf(신뢰도 factor) 서브쿼리 하나뿐: 기존 factor에 verified면 1.3배(1.0 상한)를 곱한다.
--   · 비검증 제보(기존/좌표 미제공 포함): 배수 1.0 → 가중치 완전 불변(회귀 없음)
--   · 검증 제보: 익명 0.7→0.91, 로그인 최대 1.0 — "실제로 가본 사람"의 신호를 더 신뢰(허위제보 억제)
-- freshness_score(MAX recency)는 신뢰도와 무관하므로 그대로. 나머지 정의는 V4와 동일.
CREATE OR REPLACE VIEW place_report_signals AS
SELECT
    s.place_id                                       AS place_id,
    COUNT(*)                                         AS report_count,
    MAX(s.freshness_weight)::double precision        AS freshness_score,
    LEAST(SUM(s.pos_weight), 1.0)::double precision  AS comfort_score,
    LEAST(SUM(s.neg_weight), 1.0)::double precision  AS risk_score
FROM (
    SELECT
        r.place_id,
        fw.w AS freshness_weight,
        CASE WHEN r.report_type IN ('COOL', 'SEAT_OK', 'WATER_OK', 'RESTROOM_CLEAN')
             THEN fw.w * tf.f
             ELSE 0 END AS pos_weight,
        CASE WHEN r.report_type IN ('HOT', 'CROWDED', 'BUG', 'ODOR', 'SMOKE', 'FLOOD', 'SLIPPERY')
             THEN fw.w * tf.f * (CASE WHEN r.report_type IN ('FLOOD', 'SLIPPERY') THEN 1.0 ELSE 0.6 END)
             ELSE 0 END AS neg_weight
    FROM reports r
    LEFT JOIN users u ON u.id = r.user_id
    CROSS JOIN LATERAL (
        SELECT CASE
            WHEN r.created_at >= now() - interval '1 hour'  THEN 1.0
            WHEN r.created_at >= now() - interval '3 hours' THEN 0.8
            WHEN r.created_at >= date_trunc('day', now())   THEN 0.6
            WHEN r.created_at >= now() - interval '7 days'  THEN 0.3
            ELSE 0.1
        END AS w
    ) fw
    CROSS JOIN LATERAL (
        -- 신뢰도 factor: (익명 0.7 / 로그인 0.7~1.0) × verified 보너스(1.3, 1.0 상한). ADR-0005 §④.
        SELECT LEAST(1.0,
            (CASE
                WHEN r.user_id IS NULL THEN 0.7
                ELSE 0.7 + 0.3 * LEAST(COALESCE(u.trust_score, 0) / 100.0, 1.0)
            END)
            * (CASE WHEN r.verified THEN 1.3 ELSE 1.0 END)
        ) AS f
    ) tf
    WHERE r.expires_at > now()
) s
GROUP BY s.place_id;

COMMENT ON VIEW place_report_signals IS
    'survival_score 시공간 신호(장소별 유효제보 집계: freshness/comfort/risk). ADR-0007 + V10 verified 가중(ADR-0005 §④).';
