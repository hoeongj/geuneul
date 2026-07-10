package com.geuneul.domain.ingest.safetydata;

import com.geuneul.domain.ingest.FeatureSpec;
import com.geuneul.domain.ingest.GeocodeCandidate;
import com.geuneul.domain.ingest.IngestIds;
import com.geuneul.domain.ingest.PlaceBulkUpsertRepository;
import com.geuneul.domain.ingest.PlaceRow;
import com.geuneul.domain.ingest.SourceSpec;
import com.geuneul.domain.ingest.geocode.GeocodingClient;
import com.geuneul.domain.place.PlaceCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 행정안전부_무더위쉼터 오픈API(safetydata.go.kr {@code DSSP-IF-10942}) 인제스천 — 전국 무더위쉼터를
 * 페이지네이션으로 전량 수집한다(2026-07-10 실측 60,297건). 도서관 오픈API 인제스천
 * ({@code openapi.PublicLibraryIngestionService})과 동일한 패턴: 파일 없음, 좌표 내장(LA/LO, 지오코딩
 * 거의 불필요), 업서트·soft-delete·지오코딩은 CSV 파이프라인 컴포넌트를 그대로 재사용(ADR-0002/0003).
 *
 * <p><b>기존 100건 샘플 대체</b>: {@code places.source}는 CSV 쉼터 샘플과 같은
 * {@code cooling_shelter_std}를 쓰고, deactivateStale=true면 이번 전국 스냅샷에 없는 샘플 external_id가
 * soft-delete돼 쉼터 레이어가 실데이터로 수렴한다. deactivateStale은 <b>전량 수집이 완료된 경우에만</b>
 * 켜진다(부분 수집 시 멀쩡한 행을 지우는 사고 방지 — totalCount 대비 수집량으로 게이트).
 *
 * <p><b>air_conditioned 조건부 백필</b>: 냉방기 보유수(COLR_HOLD_ARCNDTN)&gt;0인 쉼터에 낮은 confidence의
 * air_conditioned를 부여한다(도서관 seatCo&gt;0 → study_ok와 동형). "시원함"은 무더위쉼터의 정의적 속성이라
 * comfort_score·냉방 필터가 콜드스타트에서도 동작한다(간판 수치는 UGC가 확정, §9).
 */
@Service
public class ShelterIngestionService {

    private static final Logger log = LoggerFactory.getLogger(ShelterIngestionService.class);

    private static final String SOURCE_KEY = SourceSpec.COOLING_SHELTER.sourceKey(); // "cooling_shelter_std"
    private static final int PAGE_SIZE = 1000; // 실측 상한(numOfRows=1000 동작)
    // 안전 상한: 60,297/1000≈61페이지. 데이터가 몇 배 늘어도 감당하되 무한루프 사고를 막는다.
    private static final int MAX_PAGES = 300;

    private final SafetyDataApiClient apiClient;
    private final PlaceBulkUpsertRepository upsertRepository;
    private final GeocodingClient geocodingClient;

    public ShelterIngestionService(SafetyDataApiClient apiClient,
                                   PlaceBulkUpsertRepository upsertRepository,
                                   GeocodingClient geocodingClient) {
        this.apiClient = apiClient;
        this.upsertRepository = upsertRepository;
        this.geocodingClient = geocodingClient;
    }

    public record ShelterIngestSummary(int totalFetched, int reportedTotal, boolean complete, int upserted,
                                       int geocoded, int geocodeReused, int geocodeFailed,
                                       int deactivated, int airConditionedBackfilled) {
    }

    /**
     * @param deactivateStale true여도 전량 수집이 완료(complete)됐을 때만 실제로 soft-delete한다.
     */
    public ShelterIngestSummary ingestAll(boolean deactivateStale) {
        Fetch fetch = fetchAll();
        List<ShelterRecord> all = fetch.records();

        List<PlaceRow> rows = new ArrayList<>();
        List<GeocodeCandidate> needGeocode = new ArrayList<>();
        Set<String> airConditionedIds = new HashSet<>();
        Set<String> currentExternalIds = new HashSet<>();

        for (ShelterRecord r : all) {
            if (r.rstrNm() == null || r.rstrNm().isBlank()) {
                continue;
            }
            String address = hasText(r.rnDtlAdres()) ? r.rnDtlAdres() : r.dtlAdres();
            String externalId = r.rstrFcltyNo() != null
                    ? "shelter:" + r.rstrFcltyNo()
                    : IngestIds.fallbackId(r.rstrNm(), address);
            currentExternalIds.add(externalId);
            if (r.colrHoldArcndtn() != null && r.colrHoldArcndtn() > 0) {
                airConditionedIds.add(externalId);
            }

            if (validCoords(r.la(), r.lo())) {
                rows.add(new PlaceRow(externalId, r.rstrNm(), address, r.la(), r.lo()));
            } else if (hasText(address)) {
                needGeocode.add(new GeocodeCandidate(externalId, r.rstrNm(), address));
            }
        }

        int upserted = upsertRepository.upsertPlaces(rows, SOURCE_KEY, PlaceCategory.COOLING_SHELTER, false);
        GeocodeOutcome outcome = geocodeAndUpsert(needGeocode);

        // 부분 수집일 때 deactivateStale을 켜면 안 덮인 멀쩡한 쉼터가 비활성화되는 사고 → complete일 때만.
        int deactivated = (deactivateStale && fetch.complete())
                ? upsertRepository.deactivateStale(SOURCE_KEY, currentExternalIds)
                : 0;
        if (deactivateStale && !fetch.complete()) {
            log.warn("[shelter-api] deactivateStale 요청됐으나 전량 수집 미완료(fetched={} reportedTotal={}) → 건너뜀(사고 방지)",
                    all.size(), fetch.reportedTotal());
        }

        int airBackfilled = upsertRepository.backfillFeatures(SOURCE_KEY, airConditionedIds,
                List.of(new FeatureSpec("air_conditioned", "true", 0.6)));

        ShelterIngestSummary summary = new ShelterIngestSummary(all.size(), fetch.reportedTotal(),
                fetch.complete(), upserted + outcome.upserted(), outcome.geocoded(), outcome.reused(),
                outcome.failed(), deactivated, airBackfilled);
        log.info("[shelter-api] fetched={} reportedTotal={} complete={} upserted={} geocoded={} geocodeReused={} "
                        + "geocodeFailed={} deactivated={} airConditionedBackfilled={}",
                summary.totalFetched(), summary.reportedTotal(), summary.complete(), summary.upserted(),
                summary.geocoded(), summary.geocodeReused(), summary.geocodeFailed(), summary.deactivated(),
                summary.airConditionedBackfilled());
        return summary;
    }

    private record Fetch(List<ShelterRecord> records, int reportedTotal, boolean complete) {
    }

    private Fetch fetchAll() {
        List<ShelterRecord> all = new ArrayList<>();
        int reportedTotal = 0;
        boolean sawEmptyOrError = false;
        for (int pageNo = 1; pageNo <= MAX_PAGES; pageNo++) {
            ShelterPage page = apiClient.fetchPage(pageNo, PAGE_SIZE);
            if (pageNo == 1) {
                reportedTotal = page.totalCount();
            }
            if (page.items().isEmpty()) {
                sawEmptyOrError = true; // 오류/NODATA — 전량 수집을 보장할 수 없다
                break;
            }
            all.addAll(page.items());
            if (page.items().size() < PAGE_SIZE) {
                break; // 마지막 페이지(요청보다 적게 옴) — 정상 종료
            }
        }
        // complete = 첫 페이지 totalCount만큼(이상) 모았고, 중간에 빈/오류 페이지로 끊기지 않았다.
        boolean complete = reportedTotal > 0 && all.size() >= reportedTotal && !sawEmptyOrError;
        return new Fetch(all, reportedTotal, complete);
    }

    private record GeocodeOutcome(int upserted, int geocoded, int reused, int failed) {
    }

    private GeocodeOutcome geocodeAndUpsert(List<GeocodeCandidate> candidates) {
        if (candidates.isEmpty()) {
            return new GeocodeOutcome(0, 0, 0, 0);
        }
        Map<String, String> existing = upsertRepository.findGeocodedAddresses(SOURCE_KEY);
        List<GeocodeCandidate> toGeocode = candidates.stream()
                .filter(c -> !Objects.equals(existing.get(c.externalId()), c.address()))
                .toList();
        int reused = candidates.size() - toGeocode.size();

        List<PlaceRow> geocodedRows = new ArrayList<>();
        int failed = 0;
        for (GeocodeCandidate c : toGeocode) {
            var coords = geocodingClient.geocode(c.address());
            if (coords.isPresent()) {
                geocodedRows.add(new PlaceRow(c.externalId(), c.name(), c.address(),
                        coords.get().lat(), coords.get().lng()));
            } else {
                failed++;
            }
        }
        int upserted = upsertRepository.upsertPlaces(geocodedRows, SOURCE_KEY, PlaceCategory.COOLING_SHELTER, true);
        return new GeocodeOutcome(upserted, geocodedRows.size(), reused, failed);
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static boolean validCoords(Double lat, Double lng) {
        return lat != null && lng != null
                && lat >= 33 && lat <= 39 && lng >= 124 && lng <= 132;
    }
}
