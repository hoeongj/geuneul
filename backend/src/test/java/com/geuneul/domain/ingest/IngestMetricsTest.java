package com.geuneul.domain.ingest;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngestMetricsTest {

    private static final Instant NOW = Instant.parse("2026-07-18T12:00:00Z");

    @Test
    @DisplayName("한 scrape의 여러 gauge는 원장 snapshot 한 번을 공유하고 freshness/status/count를 노출한다")
    void exposesCachedLedgerSnapshot() {
        IngestRunLedger ledger = mock(IngestRunLedger.class);
        var library = new IngestRunLedger.Snapshot(
                "library_api", "SUCCEEDED", NOW.minusSeconds(120), NOW.minusSeconds(60),
                NOW.minusSeconds(3_600), 55, 8, 2_500, 3, 2);
        when(ledger.latestSnapshots()).thenReturn(Map.of("library_api", library));
        IngestMetrics metrics = new IngestMetrics(ledger, Clock.fixed(NOW, ZoneOffset.UTC));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        metrics.bindTo(registry);

        assertThat(registry.get("geuneul.ingest.ledger.readable").gauge().value()).isEqualTo(1);
        assertThat(registry.get("geuneul.ingest.last.success.age").tag("source", "library_api")
                .gauge().value()).isEqualTo(3_600);
        assertThat(registry.get("geuneul.ingest.has.success").tag("source", "library_api")
                .gauge().value()).isEqualTo(1);
        assertThat(registry.get("geuneul.ingest.last.run.status")
                .tags("source", "library_api", "status", "SUCCEEDED").gauge().value()).isEqualTo(1);
        assertThat(registry.get("geuneul.ingest.last.run.status")
                .tags("source", "library_api", "status", "FAILED").gauge().value()).isZero();
        assertThat(registry.get("geuneul.ingest.open.dead.letter.records").tag("source", "library_api")
                .gauge().value()).isEqualTo(3);
        assertThat(registry.get("geuneul.ingest.last.duration").tag("source", "library_api")
                .gauge().value()).isEqualTo(2.5);
        verify(ledger, times(1)).latestSnapshots();
    }

    @Test
    @DisplayName("원장 조회 실패는 scrape를 깨뜨리지 않고 readable=0과 빈 상태로 강등한다")
    void degradesWhenLedgerReadFails() {
        IngestRunLedger ledger = mock(IngestRunLedger.class);
        when(ledger.latestSnapshots()).thenThrow(new IllegalStateException("db unavailable"));
        IngestMetrics metrics = new IngestMetrics(ledger, Clock.fixed(NOW, ZoneOffset.UTC));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        metrics.bindTo(registry);

        assertThat(registry.get("geuneul.ingest.ledger.readable").gauge().value()).isZero();
        assertThat(registry.get("geuneul.ingest.last.run.status")
                .tags("source", "library_api", "status", "NONE").gauge().value()).isEqualTo(1);
        assertThat(registry.get("geuneul.ingest.last.success.age").tag("source", "library_api")
                .gauge().value()).isNaN();
        assertThat(registry.get("geuneul.ingest.has.success").tag("source", "library_api")
                .gauge().value()).isZero();
        verify(ledger, times(1)).latestSnapshots();
    }
}
