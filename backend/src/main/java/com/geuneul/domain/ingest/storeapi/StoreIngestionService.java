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
 * <p>⚠️ <b>계약 미검증</b> — {@link StoreRecord} 주석 참고. 상가업소정보 활용신청 승인 전까지는
 * 코드 경로만 준비된 상태이고, 실사용 전 승인 + 실 호출 재검증이 필요하다.
 *
 * <p><b>반경 단위 호출</b>: 행정동코드 목록 없이도 lat/lng/radius로 한 지역을 수집한다(전국 커버리지는
 * 호출부가 격자 좌표를 순회해 여러 번 호출 — CLAUDE.md §3 "전국 표준데이터 그대로 적재"와 정합하되,
 * 이 서비스 자체는 "한 지역 한 호출" 단위로 멱등하다). 소스가 여러 카테고리(커피점·독서실 등)를 섞어
 * 반환하므로 {@link StoreCategoryMapper}로 클라이언트에서 분류 후 카테고리별로 나눠 upsert한다.
 *
 * <p><b>soft-delete 미지원(의도적)</b> — 반경 호출 1건은 전국 스냅샷의 부분집합이라, 여기서
 * deactivateStale을 걸면 "이번에 안 보인 다른 지역 장소"까지 지우는 사고가 난다. 전국 커버리지가
 * 완성되는 P3 무인화(여러 반경 호출을 하나의 스냅샷으로 합쳐 diff)에서 다시 다룬다.
 */
@Service
public class StoreIngestionService {

    private static final Logger log = LoggerFactory.getLogger(StoreIngestionService.class);
    private static final int PAGE_SIZE = 500;
    private static final int MAX_PAGES = 50;

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
    }

    /** 한 지역(중심좌표+반경)을 수집·분류·upsert한다. 전국 커버리지는 호출부가 격자로 반복 호출. */
    public StoreIngestSummary ingestRegion(double lat, double lng, int radiusMeters) {
        List<StoreRecord> all = fetchAll(lat, lng, radiusMeters);

        Map<PlaceCategory, List<PlaceRow>> rowsByCategory = new EnumMap<>(PlaceCategory.class);
        Map<PlaceCategory, List<GeocodeCandidate>> needGeocodeByCategory = new EnumMap<>(PlaceCategory.class);
        int classified = 0;

        for (StoreRecord r : all) {
            var category = StoreCategoryMapper.classify(r.indsSclsNm());
            if (category.isEmpty() || r.bizesNm() == null || r.bizesNm().isBlank()) {
                continue;
            }
            classified++;
            PlaceCategory cat = category.get();
            String address = hasText(r.rdnmAdr()) ? r.rdnmAdr() : r.lnoAdr();
            String externalId = hasText(r.bizesId()) ? r.bizesId() : IngestIds.fallbackId(r.bizesNm(), address);

            Double parsedLat = parseDouble(r.lat());
            Double parsedLng = parseDouble(r.lon());
            boolean validCoords = parsedLat != null && parsedLng != null
                    && parsedLat >= 33 && parsedLat <= 39 && parsedLng >= 124 && parsedLng <= 132;

            if (validCoords) {
                rowsByCategory.computeIfAbsent(cat, k -> new ArrayList<>())
                        .add(new PlaceRow(externalId, r.bizesNm(), address, parsedLat, parsedLng));
            } else if (hasText(address)) {
                needGeocodeByCategory.computeIfAbsent(cat, k -> new ArrayList<>())
                        .add(new GeocodeCandidate(externalId, r.bizesNm(), address));
            }
        }

        int upserted = 0;
        int geocoded = 0;
        int geocodeFailed = 0;
        Map<PlaceCategory, Integer> byCategory = new EnumMap<>(PlaceCategory.class);

        for (PlaceCategory cat : Set.of(PlaceCategory.STUDY_CAFE, PlaceCategory.CAFE)) {
            String sourceKey = SOURCE_KEYS.get(cat);
            List<PlaceRow> rows = rowsByCategory.getOrDefault(cat, List.of());
            int catUpserted = upsertRepository.upsertPlaces(rows, sourceKey, cat, false);

            List<GeocodeCandidate> candidates = needGeocodeByCategory.getOrDefault(cat, List.of());
            GeocodeOutcome outcome = geocodeAndUpsert(sourceKey, cat, candidates);

            upserted += catUpserted + outcome.upserted();
            geocoded += outcome.geocoded();
            geocodeFailed += outcome.failed();
            byCategory.put(cat, rows.size() + candidates.size());

            // ADR-0006: STUDY_CAFE만 균일 study_ok/quiet 백필(DefaultFeatureBackfill). CAFE는 UGC 전용.
            Set<String> allIds = new HashSet<>();
            rows.forEach(row -> allIds.add(row.externalId()));
            candidates.forEach(c -> allIds.add(c.externalId()));
            upsertRepository.backfillFeatures(sourceKey, allIds, DefaultFeatureBackfill.forCategory(cat));
        }

        StoreIngestSummary summary = new StoreIngestSummary(all.size(), classified, upserted, geocoded,
                geocodeFailed, byCategory);
        log.info("[store-api] region=({},{},{}m) fetched={} classified={} upserted={} geocoded={} geocodeFailed={} byCategory={}",
                lat, lng, radiusMeters, summary.totalFetched(), summary.classified(), summary.upserted(),
                summary.geocoded(), summary.geocodeFailed(), summary.byCategory());
        return summary;
    }

    private List<StoreRecord> fetchAll(double lat, double lng, int radiusMeters) {
        List<StoreRecord> all = new ArrayList<>();
        for (int pageNo = 1; pageNo <= MAX_PAGES; pageNo++) {
            StorePage page = apiClient.searchByRadius(lat, lng, radiusMeters, null, pageNo, PAGE_SIZE);
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

    private static Double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
