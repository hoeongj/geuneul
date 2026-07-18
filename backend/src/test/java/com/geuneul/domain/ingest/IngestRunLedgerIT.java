package com.geuneul.domain.ingest;

import com.geuneul.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IngestRunLedgerIT extends AbstractIntegrationTest {

    private static final String INPUT_A = "a".repeat(64);
    private static final String INPUT_B = "b".repeat(64);

    @Autowired
    IngestRunLedger ledger;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearLedger() {
        jdbcTemplate.update("DELETE FROM ingest_runs");
    }

    @Test
    @DisplayName("성공한 재실행은 retry 전체 계보의 집계형 dead letter를 해결 처리한다")
    void retryResolvesAggregateDeadLetters() {
        UUID partialRun = ledger.start("library_api", INPUT_A, IngestRunLedger.Trigger.SCHEDULED, null);
        ledger.complete(partialRun,
                new IngestRunResult(100L, 90, 80, 2, 5, 1, 3, 0, 4, false), 321);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM ingest_runs WHERE id = ?", String.class, partialRun)).isEqualTo("PARTIAL");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT SUM(record_count) FROM ingest_dead_letters WHERE run_id = ?", Long.class, partialRun))
                .isEqualTo(15L);

        UUID partialRetry = ledger.start("library_api", INPUT_A, IngestRunLedger.Trigger.BACKFILL, partialRun);
        ledger.complete(partialRetry,
                new IngestRunResult(100L, 99, 98, 0, 0, 0, 1, 0, 6, true), 275);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM ingest_dead_letters
                 WHERE run_id IN (?, ?) AND resolved_at IS NULL
                """, Long.class, partialRun, partialRetry)).isEqualTo(4L);

        UUID successfulRetry = ledger.start("library_api", INPUT_A, IngestRunLedger.Trigger.BACKFILL, partialRetry);
        ledger.complete(successfulRetry,
                new IngestRunResult(100L, 100, 100, 0, 0, 0, 0, 0, 7, true), 250);

        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM ingest_dead_letters
                 WHERE run_id IN (?, ?) AND resolved_at IS NULL
                """, Long.class, partialRun, partialRetry)).isZero();
        IngestRunLedger.Snapshot snapshot = ledger.latestSnapshots().get("library_api");
        assertThat(snapshot.lastStatus()).isEqualTo("SUCCEEDED");
        assertThat(snapshot.lastSuccessAt()).isNotNull();
        assertThat(snapshot.lastBackfilledRecords()).isEqualTo(7);
        assertThat(snapshot.openDeadLetterRecords()).isZero();
        assertThat(snapshot.retryRuns()).isEqualTo(2);
    }

    @Test
    @DisplayName("실패 원장에는 예외 메시지 대신 클래스 기반 안전한 오류 코드만 남긴다")
    void failureStoresOnlySafeErrorCode() {
        UUID runId = ledger.start("stores_api", INPUT_A, IngestRunLedger.Trigger.MANUAL, null);

        ledger.fail(runId, new IllegalStateException("secret-value-must-not-be-persisted"), 12);

        assertThat(jdbcTemplate.queryForMap(
                "SELECT status, error_code FROM ingest_runs WHERE id = ?", runId))
                .containsEntry("status", "FAILED")
                .containsEntry("error_code", "IllegalStateException")
                .doesNotContainValue("secret-value-must-not-be-persisted");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT record_count FROM ingest_dead_letters WHERE run_id = ?", Long.class, runId))
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("RUNNING 실행과 다른 source 실행은 retry 대상으로 연결할 수 없다")
    void rejectsUnsafeRetryLineage() {
        UUID running = ledger.start("library_api", INPUT_A, IngestRunLedger.Trigger.MANUAL, null);

        assertThatThrownBy(() -> ledger.start("library_api", INPUT_A, IngestRunLedger.Trigger.BACKFILL, running))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RUNNING");

        ledger.skip(running, 1);
        assertThatThrownBy(() -> ledger.start("stores_api", INPUT_A, IngestRunLedger.Trigger.BACKFILL, running))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source");

        assertThatThrownBy(() -> ledger.start("library_api", INPUT_B,
                IngestRunLedger.Trigger.BACKFILL, running))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fingerprint");
    }

    @Test
    @DisplayName("DB에서 오염된 retry 조상은 다음 조상 행의 fingerprint가 다르면 해결하지 않는다")
    void doesNotResolveMismatchedDatabaseAncestor() {
        UUID oldest = ledger.start("library_api", INPUT_A, IngestRunLedger.Trigger.MANUAL, null);
        ledger.complete(oldest,
                new IngestRunResult(10L, 9, 9, 0, 0, 0, 0, 0, 0, false), 10);
        UUID middle = ledger.start("library_api", INPUT_A, IngestRunLedger.Trigger.BACKFILL, oldest);
        ledger.complete(middle,
                new IngestRunResult(10L, 9, 9, 0, 0, 0, 0, 0, 0, false), 10);

        // 애플리케이션 start() 검증을 우회한 DB-level 오염도 재귀 CTE가 안전하게 차단해야 한다.
        jdbcTemplate.update("UPDATE ingest_runs SET input_fingerprint = ? WHERE id = ?", INPUT_B, oldest);

        UUID newest = ledger.start("library_api", INPUT_A, IngestRunLedger.Trigger.BACKFILL, middle);
        ledger.complete(newest,
                new IngestRunResult(10L, 10, 10, 0, 0, 0, 0, 0, 0, true), 10);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ingest_dead_letters WHERE run_id = ? AND resolved_at IS NULL",
                Long.class, middle)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ingest_dead_letters WHERE run_id = ? AND resolved_at IS NULL",
                Long.class, oldest)).isEqualTo(1L);
    }
}
