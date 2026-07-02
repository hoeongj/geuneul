-- 숭실대·상도·노량진 데모 시드 (source='seed') — P5 필드테스트 전 지도를 비워두지 않기 위한 샘플.
-- 좌표는 근사치이며, 공공데이터 인제스천(cooling_shelter_std 등)이 본 데이터를 대체한다.
-- Repeatable 마이그레이션: 파일 체크섬이 바뀔 때마다 재실행 → upsert로 수렴(멱등).
INSERT INTO places (name, category, address, geom, source, source_external_id, geocoded)
VALUES
  ('숭실대학교 중앙도서관',        'LIBRARY',         '서울 동작구 상도로 369',        ST_SetSRID(ST_MakePoint(126.9573, 37.4962), 4326), 'seed', 'seed-001', false),
  ('숭실대학교 학생회관',          'ETC',             '서울 동작구 상도로 369',        ST_SetSRID(ST_MakePoint(126.9566, 37.4959), 4326), 'seed', 'seed-002', false),
  ('숭실대입구역 지하공간',        'UNDERGROUND',     '서울 동작구 상도로 지하 378',   ST_SetSRID(ST_MakePoint(126.9538, 37.4963), 4326), 'seed', 'seed-003', false),
  ('상도역',                       'UNDERGROUND',     '서울 동작구 상도로 지하 272',   ST_SetSRID(ST_MakePoint(126.9479, 37.5028), 4326), 'seed', 'seed-004', false),
  ('노량진역',                     'UNDERGROUND',     '서울 동작구 노량진로 151',      ST_SetSRID(ST_MakePoint(126.9420, 37.5139), 4326), 'seed', 'seed-005', false),
  ('동작구청',                     'CIVIC',           '서울 동작구 장승배기로 161',    ST_SetSRID(ST_MakePoint(126.9393, 37.5124), 4326), 'seed', 'seed-006', false),
  ('상도1동 주민센터 무더위쉼터',  'COOLING_SHELTER', '서울 동작구 상도로30길 7',      ST_SetSRID(ST_MakePoint(126.9531, 37.4986), 4326), 'seed', 'seed-007', false),
  ('보라매공원',                   'PARK',            '서울 동작구 여의대방로20길 33', ST_SetSRID(ST_MakePoint(126.9195, 37.4929), 4326), 'seed', 'seed-008', false),
  ('보라매공원 음수대',            'WATER',           '서울 동작구 여의대방로20길 33', ST_SetSRID(ST_MakePoint(126.9200, 37.4925), 4326), 'seed', 'seed-009', false),
  ('사육신공원 공중화장실',        'TOILET',          '서울 동작구 노량진로 191',      ST_SetSRID(ST_MakePoint(126.9364, 37.5147), 4326), 'seed', 'seed-010', false)
ON CONFLICT (source, source_external_id) DO UPDATE SET
  name = EXCLUDED.name,
  category = EXCLUDED.category,
  address = EXCLUDED.address,
  geom = EXCLUDED.geom,
  updated_at = now();
