package com.geuneul.domain.ingest;

import com.geuneul.domain.ingest.geocode.GeocodingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 공공데이터 인제스천 오케스트레이션 — 4단계 파이프라인 (ADR-0002/0003):
 *   ① 파싱(범용 파서) → ② 좌표 보유분 배치 upsert → ③ 좌표 결측분 지오코딩 → ④ 산출 좌표 upsert
 *
 * 트랜잭션 경계: upsert 단위(②/④)로 잡는다 — 60k행 지오코딩(외부 HTTP, 수십 분)을
 * 한 트랜잭션에 넣으면 커넥션 점유·롤백 폭이 과대해진다. 부분 실패해도 멱등 upsert라
 * 재실행하면 수렴하므로 파일 단위 원자성은 불필요 (ADR-0002 개정).
 *
 * 지오코딩 재사용: 이미 geocoded=true로 저장된 행은 주소가 안 바뀌었으면 건너뛴다
 * → 재적재가 카카오 쿼터를 다시 태우지 않는다 (CLAUDE.md §7 "결과 좌표는 저장").
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);
    private static final int GEOCODE_CONCURRENCY = 8; // 카카오 QPS 보호 상한

    private final StandardCsvParser parser;
    private final PlaceBulkUpsertRepository upsertRepository;
    private final GeocodingClient geocodingClient;

    public IngestionService(StandardCsvParser parser,
                            PlaceBulkUpsertRepository upsertRepository,
                            GeocodingClient geocodingClient) {
        this.parser = parser;
        this.upsertRepository = upsertRepository;
        this.geocodingClient = geocodingClient;
    }

    /** 인제스천 실행 요약 — 도메인의 휘발성 제보 {@code report.Report}와 구분되게 IngestSummary로 명명. */
    public record IngestSummary(String source, int totalRecords, int upserted, int skipped,
                                int geocoded, int geocodeReused, int geocodeFailed,
                                int deactivated, int featuresBackfilled, long tookMs) {
    }

    /** 기존 호출부(쉼터/화장실) 호환 — soft-delete diff 비활성(ADR-0006 안전장치, 부분 샘플 재적재 보호). */
    public IngestSummary ingest(SourceSpec spec, Path csvFile, Charset charset) {
        return ingest(spec, csvFile, charset, false);
    }

    /**
     * @param deactivateStale true면 이번 스냅샷에 없는 기존 활성 행을 soft-delete한다(ADR-0006).
     *                        전량 스냅샷을 매번 통째로 넣는 소스(도서관/카페 등)에서만 켠다 — 부분 파일을
     *                        재실행하는 소스(현재 쉼터 샘플·화장실 실패건 재시도)는 반드시 false로 유지.
     */
    public IngestSummary ingest(SourceSpec spec, Path csvFile, Charset charset, boolean deactivateStale) {
        long start = System.currentTimeMillis();

        // ① 파싱
        StandardCsvParser.Result parsed;
        try {
            parsed = parser.parse(csvFile, charset, spec);
        } catch (IOException e) {
            throw new UncheckedIOException("CSV 읽기 실패(" + spec.cliName() + "): " + csvFile, e);
        }

        // ② 좌표 보유분 upsert (원본 좌표 → geocoded=false)
        int upserted = upsertRepository.upsertPlaces(parsed.rows(), spec.sourceKey(), spec.category(), false);

        // ③④ 좌표 결측분 지오코딩 → upsert (geocoded=true)
        GeocodeOutcome outcome = geocodeAndUpsert(spec, parsed.needGeocode());

        // 이번 스냅샷 전체 external_id (좌표보유 + 지오코딩대상, 성공/실패 무관 — "소스에 존재"의 정의).
        Set<String> currentExternalIds = new HashSet<>();
        parsed.rows().forEach(r -> currentExternalIds.add(r.externalId()));
        parsed.needGeocode().forEach(c -> currentExternalIds.add(c.externalId()));

        int deactivated = deactivateStale
                ? upsertRepository.deactivateStale(spec.sourceKey(), currentExternalIds)
                : 0;

        // ADR-0006: 신규 카테고리(LIBRARY/STUDY_CAFE)의 study_ok/quiet 기본 백필 — 대상 없으면 no-op.
        int featuresBackfilled = upsertRepository.backfillFeatures(
                spec.sourceKey(), currentExternalIds, DefaultFeatureBackfill.forCategory(spec.category()));

        // A3: 조건부 백필 — CSV에 조건 컬럼(냉방기 보유수 등)이 있고 값>0인 external_id에만 feature를 심는다.
        // CSV에 컬럼이 없으면 conditionalFeatureIds가 비어 no-op(기존 별칭 헤더 스냅샷과 하위호환).
        SourceSpec.ConditionalFeature conditional = spec.conditionalFeature();
        if (conditional != null && !parsed.conditionalFeatureIds().isEmpty()) {
            featuresBackfilled += upsertRepository.backfillFeatures(
                    spec.sourceKey(), parsed.conditionalFeatureIds(), List.of(conditional.feature()));
        }

        IngestSummary summary = new IngestSummary(spec.sourceKey(), parsed.totalRecords(),
                upserted + outcome.upserted(), parsed.skipped(),
                outcome.geocoded(), outcome.reused(), outcome.failed(),
                deactivated, featuresBackfilled,
                System.currentTimeMillis() - start);
        log.info("[ingest] source={} total={} upserted={} skipped={} geocoded={} geocodeReused={} geocodeFailed={} "
                        + "deactivated={} featuresBackfilled={} took={}ms",
                summary.source(), summary.totalRecords(), summary.upserted(), summary.skipped(),
                summary.geocoded(), summary.geocodeReused(), summary.geocodeFailed(),
                summary.deactivated(), summary.featuresBackfilled(), summary.tookMs());
        return summary;
    }

    private record GeocodeOutcome(int upserted, int geocoded, int reused, int failed) {
    }

    private GeocodeOutcome geocodeAndUpsert(SourceSpec spec, List<GeocodeCandidate> candidates) {
        if (candidates.isEmpty()) {
            return new GeocodeOutcome(0, 0, 0, 0);
        }
        // 재사용: 이미 지오코딩돼 있고 주소가 같으면 스킵 (좌표는 DB에 이미 있음)
        Map<String, String> existing = upsertRepository.findGeocodedAddresses(spec.sourceKey());
        List<GeocodeCandidate> toGeocode = candidates.stream()
                .filter(c -> !Objects.equals(existing.get(c.externalId()), c.address()))
                .toList();
        int reused = candidates.size() - toGeocode.size();
        log.info("[ingest] 지오코딩 대상 {}건 (재사용 스킵 {}건) — 동시성 {}",
                toGeocode.size(), reused, GEOCODE_CONCURRENCY);

        // 가상 스레드 + 세마포어: I/O 대기는 넓게, 카카오 QPS는 상한으로 보호
        ConcurrentLinkedQueue<PlaceRow> geocodedRows = new ConcurrentLinkedQueue<>();
        AtomicInteger failed = new AtomicInteger();
        AtomicInteger progress = new AtomicInteger();
        Semaphore limiter = new Semaphore(GEOCODE_CONCURRENCY);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (GeocodeCandidate c : toGeocode) {
                executor.submit(() -> {
                    try {
                        limiter.acquire();
                        try {
                            Optional<GeocodingClient.LatLng> coords = geocodingClient.geocode(c.address());
                            if (coords.isPresent()) {
                                geocodedRows.add(new PlaceRow(c.externalId(), c.name(), c.address(),
                                        coords.get().lat(), coords.get().lng()));
                            } else {
                                failed.incrementAndGet();
                            }
                        } finally {
                            limiter.release();
                        }
                        int done = progress.incrementAndGet();
                        if (done % 5_000 == 0) {
                            log.info("[ingest] 지오코딩 진행 {}/{}", done, toGeocode.size());
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        failed.incrementAndGet();
                    } catch (RuntimeException e) {
                        // geocode()는 계약상 오류 시 Optional.empty()지만, 예상외 런타임 예외가 새면
                        // 이 태스크가 조용히 사라져 카운터 합(geocoded+reused+failed)이 안 맞는다 → failed로 집계.
                        failed.incrementAndGet();
                        log.warn("[ingest] 지오코딩 태스크 실패(예상외) addr={}", c.address(), e);
                    }
                });
            }
        } // close() = 전체 완료 대기

        int upserted = upsertRepository.upsertPlaces(
                List.copyOf(geocodedRows), spec.sourceKey(), spec.category(), true);
        return new GeocodeOutcome(upserted, geocodedRows.size(), reused, failed.get());
    }
}
