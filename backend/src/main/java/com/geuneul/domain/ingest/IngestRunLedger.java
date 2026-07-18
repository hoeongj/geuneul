package com.geuneul.domain.ingest;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 배치 실행 상태·재실행 계보·집계형 dead letter를 Postgres에 영속화한다(ADR-0030).
 * one-off ECS 태스크가 종료돼도 상시 API 프로세스가 이 원장을 읽어 freshness 메트릭을 노출한다.
 */
@Repository
public class IngestRunLedger {

    public static final List<String> MONITORED_SOURCES = List.of(
            "cooling_shelter_std", "library_api", "public_toilet_std", "stores_api");

    private final JdbcTemplate jdbcTemplate;

    public IngestRunLedger(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public enum Trigger {
        MANUAL, SCHEDULED, BACKFILL;

        public static Trigger parse(String value) {
            if (value == null || value.isBlank()) {
                return MANUAL;
            }
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                throw new IllegalArgumentException("지원하지 않는 ingest.trigger: " + value
                        + " (지원: " + Arrays.toString(values()).toLowerCase(Locale.ROOT) + ")");
            }
        }
    }

    public enum Status {
        RUNNING, SUCCEEDED, PARTIAL, FAILED, SKIPPED
    }

    private enum DeadLetterCategory {
        GEOCODE_FAILED, RECORD_SKIPPED, SOURCE_INCOMPLETE, RUN_FAILED
    }

    private record PreviousRun(String source, String inputFingerprint, Status status) {
    }

    public record Snapshot(String source, String lastStatus, Instant lastStartedAt, Instant lastFinishedAt,
                           Instant lastSuccessAt, long lastUpsertedRecords, long lastBackfilledRecords,
                           long lastDurationMs, long openDeadLetterRecords, long retryRuns) {

        static Snapshot empty(String source) {
            return new Snapshot(source, "NONE", null, null, null, 0, 0, 0, 0, 0);
        }
    }

    @Transactional
    public UUID start(String source, String inputFingerprint, Trigger trigger, UUID retryOf) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("ingest source는 비어 있을 수 없습니다");
        }
        if (inputFingerprint == null || !inputFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("ingest input fingerprint는 SHA-256 hex 형식이어야 합니다");
        }
        if (retryOf != null) {
            PreviousRun previous = jdbcTemplate.query(
                    "SELECT source, input_fingerprint, status FROM ingest_runs WHERE id = ?",
                    (rs, rowNum) -> new PreviousRun(rs.getString("source"), rs.getString("input_fingerprint"),
                            Status.valueOf(rs.getString("status"))),
                    retryOf).stream().findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("retry-of 실행을 찾을 수 없습니다: " + retryOf));
            if (!source.equals(previous.source())) {
                throw new IllegalArgumentException("retry-of source가 현재 source와 다릅니다");
            }
            if (!inputFingerprint.equals(previous.inputFingerprint())) {
                throw new IllegalArgumentException("retry-of 입력 fingerprint가 현재 실행과 다릅니다");
            }
            if (previous.status() == Status.RUNNING) {
                throw new IllegalArgumentException("아직 RUNNING인 실행은 재실행 대상으로 지정할 수 없습니다");
            }
        }

        UUID runId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO ingest_runs (id, source, input_fingerprint, trigger_type, retry_of, status)
                VALUES (?, ?, ?, ?, ?, 'RUNNING')
                """, runId, source, inputFingerprint, trigger.name(), retryOf);
        return runId;
    }

    @Transactional
    public void complete(UUID runId, IngestRunResult result, long durationMs) {
        Status status = result.partial() ? Status.PARTIAL : Status.SUCCEEDED;
        int updated = jdbcTemplate.update("""
                UPDATE ingest_runs
                   SET status = ?, finished_at = clock_timestamp(), expected_records = ?, total_records = ?,
                       upserted_records = ?, skipped_records = ?, geocoded_records = ?,
                       geocode_reused_records = ?, geocode_failed_records = ?, deactivated_records = ?,
                       backfilled_records = ?, duration_ms = ?, complete = ?, error_code = NULL
                 WHERE id = ? AND status = 'RUNNING'
                """, status.name(), result.expectedRecords(), result.totalRecords(), result.upsertedRecords(),
                result.skippedRecords(), result.geocodedRecords(), result.geocodeReusedRecords(),
                result.geocodeFailedRecords(), result.deactivatedRecords(), result.backfilledRecords(),
                nonnegative(durationMs), result.complete(), runId);
        requireRunningTransition(runId, updated);

        if (result.geocodeFailedRecords() > 0) {
            addDeadLetter(runId, DeadLetterCategory.GEOCODE_FAILED, result.geocodeFailedRecords(), true);
        }
        if (result.skippedRecords() > 0) {
            addDeadLetter(runId, DeadLetterCategory.RECORD_SKIPPED, result.skippedRecords(), false);
        }
        if (!result.complete()) {
            addDeadLetter(runId, DeadLetterCategory.SOURCE_INCOMPLETE, result.incompleteRecords(), true);
        }
        if (status == Status.SUCCEEDED) {
            jdbcTemplate.update("""
                    WITH RECURSIVE retry_ancestors(id, input_fingerprint) AS (
                        SELECT parent.id, parent.input_fingerprint
                          FROM ingest_runs child
                          JOIN ingest_runs parent ON parent.id = child.retry_of
                         WHERE child.id = ?
                           AND parent.input_fingerprint = child.input_fingerprint
                        UNION ALL
                        SELECT next_parent.id, next_parent.input_fingerprint
                          FROM ingest_runs current_run
                          JOIN retry_ancestors child ON current_run.id = child.id
                          JOIN ingest_runs next_parent ON next_parent.id = current_run.retry_of
                         WHERE next_parent.input_fingerprint = child.input_fingerprint
                    )
                    UPDATE ingest_dead_letters dead
                       SET resolved_at = clock_timestamp()
                     WHERE dead.run_id IN (SELECT id FROM retry_ancestors)
                       AND dead.resolved_at IS NULL
                    """, runId);
        }
    }

    @Transactional
    public void fail(UUID runId, Throwable failure, long durationMs) {
        String errorCode = failure.getClass().getSimpleName();
        if (errorCode.isBlank()) {
            errorCode = failure.getClass().getName();
        }
        if (errorCode.length() > 120) {
            errorCode = errorCode.substring(0, 120);
        }
        int updated = jdbcTemplate.update("""
                UPDATE ingest_runs
                   SET status = 'FAILED', finished_at = clock_timestamp(), duration_ms = ?,
                       complete = FALSE, error_code = ?
                 WHERE id = ? AND status = 'RUNNING'
                """, nonnegative(durationMs), errorCode, runId);
        requireRunningTransition(runId, updated);
        addDeadLetter(runId, DeadLetterCategory.RUN_FAILED, 1, true);
    }

    @Transactional
    public void skip(UUID runId, long durationMs) {
        int updated = jdbcTemplate.update("""
                UPDATE ingest_runs
                   SET status = 'SKIPPED', finished_at = clock_timestamp(), duration_ms = ?, complete = FALSE
                 WHERE id = ? AND status = 'RUNNING'
                """, nonnegative(durationMs), runId);
        requireRunningTransition(runId, updated);
    }

    @Transactional(readOnly = true)
    public Map<String, Snapshot> latestSnapshots() {
        List<Snapshot> snapshots = jdbcTemplate.query("""
                WITH known_sources(source) AS (
                    VALUES ('cooling_shelter_std'), ('library_api'), ('public_toilet_std'), ('stores_api')
                )
                SELECT s.source,
                       COALESCE(latest.status, 'NONE') AS last_status,
                       latest.started_at AS last_started_at,
                       latest.finished_at AS last_finished_at,
                       success.last_success_at,
                       COALESCE(latest.upserted_records, 0) AS last_upserted_records,
                       COALESCE(latest.backfilled_records, 0) AS last_backfilled_records,
                       COALESCE(latest.duration_ms, 0) AS last_duration_ms,
                       COALESCE(dead.open_records, 0) AS open_dead_letter_records,
                       COALESCE(retries.retry_runs, 0) AS retry_runs
                  FROM known_sources s
                  LEFT JOIN LATERAL (
                      SELECT status, started_at, finished_at, upserted_records, backfilled_records, duration_ms
                        FROM ingest_runs r
                       WHERE r.source = s.source
                       ORDER BY r.started_at DESC, r.id DESC
                       LIMIT 1
                  ) latest ON TRUE
                  LEFT JOIN LATERAL (
                      SELECT MAX(finished_at) AS last_success_at
                        FROM ingest_runs r
                       WHERE r.source = s.source AND r.status = 'SUCCEEDED'
                  ) success ON TRUE
                  LEFT JOIN LATERAL (
                      SELECT COALESCE(SUM(record_count), 0) AS open_records
                        FROM ingest_dead_letters d
                       WHERE d.source = s.source AND d.resolved_at IS NULL
                  ) dead ON TRUE
                  LEFT JOIN LATERAL (
                      SELECT COUNT(*) AS retry_runs
                        FROM ingest_runs r
                       WHERE r.source = s.source AND r.retry_of IS NOT NULL
                  ) retries ON TRUE
                 ORDER BY s.source
                """, (rs, rowNum) -> new Snapshot(
                rs.getString("source"), rs.getString("last_status"),
                instant(rs.getTimestamp("last_started_at")), instant(rs.getTimestamp("last_finished_at")),
                instant(rs.getTimestamp("last_success_at")), rs.getLong("last_upserted_records"),
                rs.getLong("last_backfilled_records"), rs.getLong("last_duration_ms"),
                rs.getLong("open_dead_letter_records"), rs.getLong("retry_runs")));

        Map<String, Snapshot> bySource = new LinkedHashMap<>();
        MONITORED_SOURCES.forEach(source -> bySource.put(source, Snapshot.empty(source)));
        snapshots.forEach(snapshot -> bySource.put(snapshot.source(), snapshot));
        return Map.copyOf(bySource);
    }

    private void addDeadLetter(UUID runId, DeadLetterCategory category, long recordCount, boolean retryable) {
        jdbcTemplate.update("""
                INSERT INTO ingest_dead_letters (run_id, source, category, record_count, retryable)
                SELECT id, source, ?, ?, ? FROM ingest_runs WHERE id = ?
                """, category.name(), recordCount, retryable, runId);
    }

    private static void requireRunningTransition(UUID runId, int updated) {
        if (updated != 1) {
            throw new IllegalStateException("RUNNING ingest 실행만 종료할 수 있습니다: " + runId);
        }
    }

    private static long nonnegative(long durationMs) {
        return Math.max(0, durationMs);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
