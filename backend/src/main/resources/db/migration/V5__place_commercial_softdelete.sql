-- ADR-0006: 공부 가능 공간 커버리지 확장 — 상업/커먼스 분리 + 폐업 회전 대응 soft-delete.
--
-- is_commercial: CAFE/STUDY_CAFE 같은 상업 POI 대량 유입이 "공개 커먼스"(도서관·공공시설·쉼터 등)
--   정체성을 흐리지 않도록 지도 필터·표시에서 분리할 수 있게 한다. 값은 PlaceCategory.commercial()이
--   인제스천 시점에 채운다(카테고리로 결정되는 파생값이라 별도 입력 없이 upsert가 계산).
-- deleted_at: 카페는 폐업 회전이 커, 스냅샷 재적재 시 사라진 행을 즉시 DELETE하지 않고 비활성화한다
--   (로드맵 P3 "스냅샷에서 사라진 행 soft-delete 비활성화"를 앞당겨 구현). NULL=활성.
ALTER TABLE places
    ADD COLUMN is_commercial BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN deleted_at    TIMESTAMP(6) WITH TIME ZONE;

COMMENT ON COLUMN places.is_commercial IS
    '상업 POI(카페/스터디카페) vs 공개 커먼스 분리 — ADR-0006. PlaceCategory.commercial()로 인제스천이 채운다.';
COMMENT ON COLUMN places.deleted_at IS
    '스냅샷 재적재 시 사라진 행의 soft-delete 시각(폐업 회전 대응) — ADR-0006, 로드맵 P3. NULL=활성.';

-- 활성 장소만 스캔하는 검색 경로(반경/bounds/단건)를 위한 부분 인덱스.
-- (공간 GiST(V3)가 1차 필터라 selectivity 이득은 적지만, soft-delete 비율이 커지는 카페 재적재 이후를
--  대비한 플래너 힌트이자 "soft-delete 인지 인덱스"를 명시적으로 남겨둔다.)
CREATE INDEX idx_places_active ON places (id) WHERE deleted_at IS NULL;
