-- 그늘 코어 스키마 (ERD §8). 시각 컬럼은 mp 컨벤션과 동일하게 TIMESTAMP(6) WITH TIME ZONE.

-- 사용자 (소셜 로그인: 카카오/구글)
CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider      VARCHAR(16)  NOT NULL,                      -- KAKAO | GOOGLE
    provider_id   VARCHAR(128) NOT NULL,
    email         VARCHAR(255),
    nickname      VARCHAR(64)  NOT NULL,
    profile_image VARCHAR(512),
    trust_score   DOUBLE PRECISION NOT NULL DEFAULT 0,
    role          VARCHAR(16)  NOT NULL DEFAULT 'USER',       -- USER | ADMIN
    created_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_provider UNIQUE (provider, provider_id)
);

-- 장소 (공공데이터 + 지오코딩 보완).
-- (source, source_external_id) = 공공데이터 재적재 시 멱등 upsert를 위한 자연키.
CREATE TABLE places (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name               VARCHAR(255) NOT NULL,
    category           VARCHAR(32)  NOT NULL,
    address            VARCHAR(512),
    geom               geometry(Point, 4326) NOT NULL,        -- WGS84 위경도
    source             VARCHAR(64)  NOT NULL,                 -- 예: cooling_shelter, public_toilet_std
    source_external_id VARCHAR(128),
    external_map_url   VARCHAR(512),
    open_hours_json    JSONB,
    geocoded           BOOLEAN NOT NULL DEFAULT FALSE,        -- 주소 지오코딩으로 좌표 보완했는지
    created_at         TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at         TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_places_source UNIQUE (source, source_external_id)
);
-- 반경/kNN 공간검색용 GiST 인덱스 (그늘의 핵심 인덱스 — 전체스캔 방지).
CREATE INDEX idx_places_geom ON places USING GIST (geom);
CREATE INDEX idx_places_category ON places (category);

-- 장소 편의 피처
CREATE TABLE place_features (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    place_id     BIGINT NOT NULL REFERENCES places (id) ON DELETE CASCADE,
    feature_type VARCHAR(32) NOT NULL,   -- air_conditioned, outlet, wifi, restroom, water, seating, no_eyes
    value        VARCHAR(64),
    source       VARCHAR(64),
    confidence   DOUBLE PRECISION,
    CONSTRAINT uq_place_features UNIQUE (place_id, feature_type)
);

-- 제보 (휘발성 실시간 상태). expires_at 지나면 freshness/score 계산에서 제외.
CREATE TABLE reports (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      BIGINT REFERENCES users (id) ON DELETE SET NULL,
    place_id     BIGINT NOT NULL REFERENCES places (id) ON DELETE CASCADE,
    report_type  VARCHAR(24) NOT NULL,   -- COOL, HOT, BUG, ODOR, SMOKE, FLOOD, SLIPPERY, WATER_OK, RESTROOM_CLEAN
    status_value VARCHAR(64),
    comment      VARCHAR(500),
    photo_url    VARCHAR(512),
    confidence   DOUBLE PRECISION,
    is_anonymous BOOLEAN NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now(),
    expires_at   TIMESTAMP(6) WITH TIME ZONE
);
-- 장소별 최근 제보 조회 최적화.
CREATE INDEX idx_reports_place_created ON reports (place_id, created_at DESC);

-- 후기 (영구 평판). survival_score와 분리된 장소 평판 콘텐츠. 로그인 필요.
CREATE TABLE reviews (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    place_id    BIGINT NOT NULL REFERENCES places (id) ON DELETE CASCADE,
    rating      SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment     VARCHAR(1000),
    photos_json JSONB,
    created_at  TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX idx_reviews_place ON reviews (place_id);
