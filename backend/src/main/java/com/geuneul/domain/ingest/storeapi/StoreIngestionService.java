package com.geuneul.domain.ingest.storeapi;

import com.geuneul.domain.ingest.DefaultFeatureBackfill;
import com.geuneul.domain.ingest.GeocodeCandidate;
import com.geuneul.domain.ingest.IngestIds;
import com.geuneul.domain.ingest.PlaceBulkUpsertRepository;
import com.geuneul.domain.ingest.PlaceRow;
import com.geuneul.domain.ingest.geocode.GeocodingClient;
import com.geuneul.domain.place.PlaceCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 상권정보 오픈API 기반 STUDY_CAFE/CAFE 인제스천(ADR-0006 §2 우선순위 ②③).
 *
 * <p><b>계약 검증 완료(2026-07-10, TS-026)</b> — 활용신청 승인 후 실 호출로 계약을 확정했다.
 * 승인 전 임시로 "전체 상가를 받아 분류명으로 필터"하던 것을, 확정된 소분류코드
 * ({@link StoreCategoryMapper#targetCodes()}: 카페=I21201, 독서실/스터디카페=R10202)로 <b>서버측
 * 필터</b>해 대상 업종만 페이지네이션하도록 바꿨다 — 광화문 1.5km에 상가 6천 중 카페 수백뿐이라
 * 서버 필터가 API 호출량을 한 자릿수 배 줄인다.
 *
 * <p><b>반경 단위 → 격자 커버리지</b>: {@link #ingestRegion}은 한 중심좌표+반경을 멱등 수집하고,
 * {@link #ingestArea}는 bbox를 반경 원들로 격자 순회해(원이 격자칸을 내접 커버, dedup은 멱등 upsert가
 * 담당) 한 번의 실행으로 넓은 지역(예: 서울)을 덮는다 — docs/SPEC.md §3 "전국 표준데이터 적재"·PostGIS
 * 반경검색과 동일한 정신모델.
 *
 * <p><b>soft-delete 미지원(의도적)</b> — 반경/격자 호출은 전국 스냅샷의 부분집합이라, 여기서
 * deactivateStale을 걸면 "이번에 안 덮은 다른 지역 장소"까지 지우는 사고가 난다. 전국 완전 커버리지가
 * 완성되는 P3 무인화(스냅샷 합쳐 diff)에서 다시 다룬다.
 */
@Service
public class StoreIngestionService {

    private static final Logger log = LoggerFactory.getLogger(StoreIngestionService.class);
    private static final int PAGE_SIZE = 1000; // 실측 상한(numOfRows=1000 동작)
    private static final int MAX_PAGES = 50;

    /**
     * 격자 스텝 = 반경 × 이 계수. 반경 r 원은 한 변 r√2(≈1.414r)의 정사각형을 내접 커버하므로
     * 계수 ≤ √2면 인접 원들이 격자칸을 빈틈없이 덮는다. 1.3은 살짝 겹치게 잡아 경계 누락을 막는다.
     */
    private static final double STEP_FACTOR = 1.3;
    private static final double METERS_PER_DEG_LAT = 111_320.0;

    private static final Map<PlaceCategory, String> SOURCE_KEYS = Map.of(
            PlaceCategory.STUDY_CAFE, "store_study_cafe_api",
            PlaceCategory.CAFE, "store_cafe_api");

    private final StoreApiClient apiClient;
    private final PlaceBulkUpsertRepository upsertRepository;
    private final GeocodingClient geocodingClient;

    public StoreIngestionService(StoreApiClient apiClient, PlaceBulkUpsertRepository upsertRepository,
                                 GeocodingClient geocodingClient) {
        this.apiClient = apiClient;
        this.upsertRepository = upsertRepository;
        this.geocodingClient = geocodingClient;
    }

    public record StoreIngestSummary(int totalFetched, int classified, int upserted, int geocoded,
                                     int geocodeFailed, Map<PlaceCategory, Integer> byCategory) {

        static StoreIngestSummary zero() {
            return new StoreIngestSummary(0, 0, 0, 0, 0, new EnumMap<>(PlaceCategory.class));
        }

        StoreIngestSummary plus(StoreIngestSummary o) {
            Map<PlaceCategory, Integer> merged = new EnumMap<>(PlaceCategory.class);
            merged.putAll(byCategory);
            o.byCategory.forEach((k, v) -> merged.merge(k, v, Integer::sum));
            return new StoreIngestSummary(
                    totalFetched + o.totalFetched, classified + o.classified, upserted + o.upserted,
                    geocoded + o.geocoded, geocodeFailed + o.geocodeFailed, merged);
        }
    }

    /**
     * bbox 지역을 반경 원 격자로 순회 적재한다(한 실행으로 넓은 지역 커버). dedup은 (source, external_id)
     * 멱등 upsert가 담당하므로 원들이 겹쳐도 안전하다.
     */
    public StoreIngestSummary ingestArea(double minLng, double minLat, double maxLng, double maxLat,
                                         int radiusMeters) {
        double midLat = (minLat + maxLat) / 2.0;
        double stepLatDeg = (radiusMeters * STEP_FACTOR) / METERS_PER_DEG_LAT;
        double metersPerDegLng = METERS_PER_DEG_LAT * Math.cos(Math.toRadians(midLat));
        double stepLngDeg = (radiusMeters * STEP_FACTOR) / metersPerDegLng;

        int rows = (int) Math.floor((maxLat - minLat) / stepLatDeg) + 1;
        int cols = (int) Math.floor((maxLng - minLng) / stepLngDeg) + 1;
        int totalCells = rows * cols;
        log.info("[store-api] ingestArea bbox=({},{})~({},{}) radius={}m grid={}x{}={}cells",
                minLng, minLat, maxLng, maxLat, radiusMeters, cols, rows, totalCells);

        StoreIngestSummary total = StoreIngestSummary.zero();
        int cell = 0;
        for (double cy = minLat; cy <= maxLat + 1e-9; cy += stepLatDeg) {
            for (double cx = minLng; cx <= maxLng + 1e-9; cx += stepLngDeg) {
                cell++;
                StoreIngestSummary s = ingestRegion(cy, cx, radiusMeters);
                total = total.plus(s);
                if (cell % 20 == 0 || cell == totalCells) {
                    log.info("[store-api] ingestArea 진행 {}/{} cells — 누적 upserted={} byCategory={}",
                            cell, totalCells, total.upserted(), total.byCategory());
                }
            }
        }
        log.info("[store-api] ingestArea 완료 cells={} totalFetched={} upserted={} geocoded={} byCategory={}",
                cell, total.totalFetched(), total.upserted(), total.geocoded(), total.byCategory());
        return total;
    }

    /**
     * 한 지역(중심좌표+반경)을 대상 업종코드별 서버측 필터로 수집·upsert한다. 각 코드는 이미
     * 카테고리가 확정돼 있어(요청한 코드 = 그 카테고리) 별도 분류가 필요 없다.
     */
    public StoreIngestSummary ingestRegion(double lat, double lng, int radiusMeters) {
        int totalFetched = 0;
        int classified = 0;
        int upserted = 0;
        int geocoded = 0;
        int geocodeFailed = 0;
        Map<PlaceCategory, Integer> byCategory = new EnumMap<>(PlaceCategory.class);

        for (Map.Entry<String, PlaceCategory> target : StoreCategoryMapper.targetCodes().entrySet()) {
            String code = target.getKey();
            PlaceCategory cat = target.getValue();
            String sourceKey = SOURCE_KEYS.get(cat);

            List<StoreRecord> records = fetchAll(lat, lng, radiusMeters, code);
            totalFetched += records.size();

            List<PlaceRow> rows = new ArrayList<>();
            List<GeocodeCandidate> candidates = new ArrayList<>();
            for (StoreRecord r : records) {
                if (r.bizesNm() == null || r.bizesNm().isBlank()) {
                    continue;
                }
                classified++;
                String address = hasText(r.rdnmAdr()) ? r.rdnmAdr() : r.lnoAdr();
                String externalId = hasText(r.bizesId())
                        ? r.bizesId() : IngestIds.fallbackId(r.bizesNm(), address);

                if (validCoords(r.lat(), r.lon())) {
                    rows.add(new PlaceRow(externalId, r.bizesNm(), address, r.lat(), r.lon()));
                } else if (hasText(address)) {
                    candidates.add(new GeocodeCandidate(externalId, r.bizesNm(), address));
                }
            }

            int catUpserted = upsertRepository.upsertPlaces(rows, sourceKey, cat, false);
            GeocodeOutcome outcome = geocodeAndUpsert(sourceKey, cat, candidates);

            upserted += catUpserted + outcome.upserted();
            geocoded += outcome.geocoded();
            geocodeFailed += outcome.failed();
            byCategory.merge(cat, rows.size() + candidates.size(), Integer::sum);

            // ADR-0006: STUDY_CAFE만 균일 study_ok/quiet 백필. CAFE는 UGC 전용(빈 규칙 → no-op).
            Set<String> allIds = new HashSet<>();
            rows.forEach(row -> allIds.add(row.externalId()));
            candidates.forEach(c -> allIds.add(c.externalId()));
            upsertRepository.backfillFeatures(sourceKey, allIds, DefaultFeatureBackfill.forCategory(cat));
        }

        StoreIngestSummary summary = new StoreIngestSummary(totalFetched, classified, upserted, geocoded,
                geocodeFailed, byCategory);
        log.debug("[store-api] region=({},{},{}m) fetched={} upserted={} geocoded={} geocodeFailed={} byCategory={}",
                lat, lng, radiusMeters, totalFetched, upserted, geocoded, geocodeFailed, byCategory);
        return summary;
    }

    private List<StoreRecord> fetchAll(double lat, double lng, int radiusMeters, String indsSclsCd) {
        List<StoreRecord> all = new ArrayList<>();
        for (int pageNo = 1; pageNo <= MAX_PAGES; pageNo++) {
            StorePage page = apiClient.searchByRadius(lat, lng, radiusMeters, indsSclsCd, pageNo, PAGE_SIZE);
            if (page.items().isEmpty()) {
                break;
            }
            all.addAll(page.items());
            if (page.items().size() < PAGE_SIZE) {
                break;
            }
        }
        return all;
    }

    private record GeocodeOutcome(int upserted, int geocoded, int failed) {
    }

    private GeocodeOutcome geocodeAndUpsert(String sourceKey, PlaceCategory category, List<GeocodeCandidate> candidates) {
        if (candidates.isEmpty()) {
            return new GeocodeOutcome(0, 0, 0);
        }
        Map<String, String> existing = upsertRepository.findGeocodedAddresses(sourceKey);
        List<PlaceRow> geocodedRows = new ArrayList<>();
        int failed = 0;
        for (GeocodeCandidate c : candidates) {
            if (Objects.equals(existing.get(c.externalId()), c.address())) {
                continue; // 이미 지오코딩된 주소 재사용(ADR-0003)
            }
            var coords = geocodingClient.geocode(c.address());
            if (coords.isPresent()) {
                geocodedRows.add(new PlaceRow(c.externalId(), c.name(), c.address(),
                        coords.get().lat(), coords.get().lng()));
            } else {
                failed++;
            }
        }
        int upserted = upsertRepository.upsertPlaces(geocodedRows, sourceKey, category, true);
        return new GeocodeOutcome(upserted, geocodedRows.size(), failed);
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    /** 한국 영역 WGS84 좌표만 유효로 본다(오염 좌표·0,0 방어). */
    private static boolean validCoords(Double lat, Double lng) {
        return lat != null && lng != null
                && lat >= 33 && lat <= 39 && lng >= 124 && lng <= 132;
    }
}
