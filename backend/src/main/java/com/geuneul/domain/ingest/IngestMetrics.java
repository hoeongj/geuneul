package com.geuneul.domain.ingest;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** DB 원장을 짧게 캐시해 Prometheus 한 번의 scrape가 SQL 한 번만 실행되게 하는 고정 카디널리티 gauge 묶음. */
@Component
public class IngestMetrics implements MeterBinder {

    private static final Logger log = LoggerFactory.getLogger(IngestMetrics.class);
    private static final Duration CACHE_TTL = Duration.ofSeconds(10);
    private static final List<String> STATUSES = List.of("NONE", "RUNNING", "SUCCEEDED", "PARTIAL", "FAILED", "SKIPPED");

    private final IngestRunLedger ledger;
    private final Clock clock;
    private volatile CachedSnapshot cache;

    public IngestMetrics(IngestRunLedger ledger, Clock clock) {
        this.ledger = ledger;
        this.clock = clock;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("geuneul.ingest.ledger.readable", this, metrics -> metrics.snapshot().readable() ? 1 : 0)
                .description("Whether the persisted ingest ledger was readable during the latest cache refresh")
                .register(registry);

        for (String source : IngestRunLedger.MONITORED_SOURCES) {
            Gauge.builder("geuneul.ingest.last.success.age", this, metrics -> metrics.lastSuccessAge(source))
                    .tag("source", source).baseUnit("seconds")
                    .description("Age of the last fully successful ingest run")
                    .register(registry);
            Gauge.builder("geuneul.ingest.has.success", this,
                            metrics -> metrics.value(source).lastSuccessAt() == null ? 0 : 1)
                    .tag("source", source)
                    .description("Whether at least one fully successful ingest run exists")
                    .register(registry);
            Gauge.builder("geuneul.ingest.open.dead.letter.records", this,
                            metrics -> metrics.value(source).openDeadLetterRecords())
                    .tag("source", source)
                    .description("Unresolved aggregate dead-letter record count")
                    .register(registry);
            Gauge.builder("geuneul.ingest.last.upserted.records", this,
                            metrics -> metrics.value(source).lastUpsertedRecords())
                    .tag("source", source)
                    .description("Upserted records in the latest ingest run")
                    .register(registry);
            Gauge.builder("geuneul.ingest.last.backfilled.records", this,
                            metrics -> metrics.value(source).lastBackfilledRecords())
                    .tag("source", source)
                    .description("Feature records backfilled in the latest ingest run")
                    .register(registry);
            Gauge.builder("geuneul.ingest.last.duration", this,
                            metrics -> metrics.value(source).lastDurationMs() / 1_000.0)
                    .tag("source", source).baseUnit("seconds")
                    .description("Duration of the latest ingest run")
                    .register(registry);
            Gauge.builder("geuneul.ingest.retry.runs", this, metrics -> metrics.value(source).retryRuns())
                    .tag("source", source)
                    .description("Persisted ingest runs linked to an earlier run")
                    .register(registry);
            for (String status : STATUSES) {
                Gauge.builder("geuneul.ingest.last.run.status", this,
                                metrics -> status.equals(metrics.value(source).lastStatus()) ? 1 : 0)
                        .tags("source", source, "status", status)
                        .description("One-hot status of the latest ingest run")
                        .register(registry);
            }
        }
    }

    private double lastSuccessAge(String source) {
        Instant lastSuccess = value(source).lastSuccessAt();
        if (lastSuccess == null) {
            return Double.NaN;
        }
        return Math.max(0, Duration.between(lastSuccess, clock.instant()).toSeconds());
    }

    private IngestRunLedger.Snapshot value(String source) {
        return snapshot().values().getOrDefault(source, IngestRunLedger.Snapshot.empty(source));
    }

    private CachedSnapshot snapshot() {
        Instant now = clock.instant();
        CachedSnapshot current = cache;
        if (current != null && now.isBefore(current.expiresAt())) {
            return current;
        }
        synchronized (this) {
            current = cache;
            if (current != null && now.isBefore(current.expiresAt())) {
                return current;
            }
            try {
                current = new CachedSnapshot(ledger.latestSnapshots(), now.plus(CACHE_TTL), true);
            } catch (RuntimeException failure) {
                log.warn("[ingest-metrics] 원장 조회 실패(errorCode={})", failure.getClass().getSimpleName());
                Map<String, IngestRunLedger.Snapshot> fallback = current == null ? Map.of() : current.values();
                current = new CachedSnapshot(fallback, now.plus(CACHE_TTL), false);
            }
            cache = current;
            return current;
        }
    }

    private record CachedSnapshot(Map<String, IngestRunLedger.Snapshot> values, Instant expiresAt,
                                  boolean readable) {
    }
}
