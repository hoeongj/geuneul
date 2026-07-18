-- 공공데이터 인제스천 운영 원장(ADR-0030).
-- 원본 행·주소·외부 API 응답·예외 메시지는 저장하지 않고 실행 단위 카운터와 안전한 오류 코드만 보존한다.

CREATE TABLE ingest_runs (
    id                       UUID PRIMARY KEY,
    source                   VARCHAR(64) NOT NULL,
    input_fingerprint        VARCHAR(64) NOT NULL,
    trigger_type             VARCHAR(16) NOT NULL,
    retry_of                 UUID REFERENCES ingest_runs(id),
    status                   VARCHAR(16) NOT NULL,
    started_at               TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    finished_at              TIMESTAMPTZ,
    expected_records         BIGINT,
    total_records            BIGINT NOT NULL DEFAULT 0,
    upserted_records         BIGINT NOT NULL DEFAULT 0,
    skipped_records          BIGINT NOT NULL DEFAULT 0,
    geocoded_records         BIGINT NOT NULL DEFAULT 0,
    geocode_reused_records   BIGINT NOT NULL DEFAULT 0,
    geocode_failed_records   BIGINT NOT NULL DEFAULT 0,
    deactivated_records      BIGINT NOT NULL DEFAULT 0,
    backfilled_records       BIGINT NOT NULL DEFAULT 0,
    duration_ms              BIGINT NOT NULL DEFAULT 0,
    complete                 BOOLEAN,
    error_code               VARCHAR(120),
    CONSTRAINT ck_ingest_runs_trigger
        CHECK (trigger_type IN ('MANUAL', 'SCHEDULED', 'BACKFILL')),
    CONSTRAINT ck_ingest_runs_input_fingerprint
        CHECK (input_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_ingest_runs_status
        CHECK (status IN ('RUNNING', 'SUCCEEDED', 'PARTIAL', 'FAILED', 'SKIPPED')),
    CONSTRAINT ck_ingest_runs_finished
        CHECK ((status = 'RUNNING' AND finished_at IS NULL) OR (status <> 'RUNNING' AND finished_at IS NOT NULL)),
    CONSTRAINT ck_ingest_runs_nonnegative
        CHECK (COALESCE(expected_records, 0) >= 0
            AND total_records >= 0
            AND upserted_records >= 0
            AND skipped_records >= 0
            AND geocoded_records >= 0
            AND geocode_reused_records >= 0
            AND geocode_failed_records >= 0
            AND deactivated_records >= 0
            AND backfilled_records >= 0
            AND duration_ms >= 0)
);

CREATE INDEX idx_ingest_runs_source_started
    ON ingest_runs (source, started_at DESC);

CREATE INDEX idx_ingest_runs_status_started
    ON ingest_runs (status, started_at DESC);

CREATE TABLE ingest_dead_letters (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_id         UUID NOT NULL REFERENCES ingest_runs(id) ON DELETE CASCADE,
    source         VARCHAR(64) NOT NULL,
    category       VARCHAR(32) NOT NULL,
    record_count   BIGINT NOT NULL,
    retryable      BOOLEAN NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    resolved_at    TIMESTAMPTZ,
    CONSTRAINT uq_ingest_dead_letters_run_category UNIQUE (run_id, category),
    CONSTRAINT ck_ingest_dead_letters_category
        CHECK (category IN ('GEOCODE_FAILED', 'RECORD_SKIPPED', 'SOURCE_INCOMPLETE', 'RUN_FAILED')),
    CONSTRAINT ck_ingest_dead_letters_count CHECK (record_count > 0)
);

CREATE INDEX idx_ingest_dead_letters_open_source
    ON ingest_dead_letters (source, created_at DESC)
    WHERE resolved_at IS NULL;
