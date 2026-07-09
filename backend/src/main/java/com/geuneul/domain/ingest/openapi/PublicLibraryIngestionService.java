package com.geuneul.domain.ingest.openapi;

import com.geuneul.domain.ingest.FeatureSpec;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 전국도서관표준데이터 오픈API 인제스천(ADR-0006 §2 우선순위 ①) — CSV 다운로드가 아니라
 * {@link DataGoKrPublicLibraryClient}로 페이지네이션 전량 수집한다(2026-07-09 실측: 지역 파라미터
 * 없이 pageNo/numOfRows만으로 전국 3,555건 순회 가능, HANDOFF의 "오픈API는 경기도만" 추정을 정정).
 *
 * {@link com.geuneul.domain.ingest.IngestionService}(CSV 파이프라인)와 나란한 별도 서비스인 이유:
 * 소스가 파일이 아니라 페이지네이션 API라 파싱 단계가 없고, study_ok 백필이 카테고리 균일 규칙이
 * 아니라 레코드별 조건(seatCo&gt;0)이라 {@code DefaultFeatureBackfill}을 거치지 않는다.
 * 업서트(PlaceBulkUpsertRepository)·지오코딩(GeocodingClient)·soft-delete(deactivateStale)는
 * CSV 파이프라인과 동일 컴포넌트를 그대로 재사용한다(DRY, ADR-0002/0003 멱등 규약 승계).
 */
@Service
public class PublicLibraryIngestionService {

    private static final Logger log = LoggerFactory.getLogger(PublicLibraryIngestionService.class);

    /** places.source 값 — CSV 소스(예: public_toilet_std)와 구분되게 API 유래임을 명시. */
    public static final String SOURCE_KEY = "library_api";
    private static final int PAGE_SIZE = 500;
    // 안전 상한: 실측 3,555건/500=8페이지. 데이터가 몇 배 늘어도 감당하되, 응답 이상으로 무한루프에
    // 빠지는 사고를 막는다(예: totalCount 파싱 실패로 종료 조건이 안 걸리는 경우).
    private static final int MAX_PAGES = 200;

    private final PublicLibraryApiClient apiClient;
    private final PlaceBulkUpsertRepository upsertRepository;
    private final GeocodingClient geocodingClient;

    public PublicLibraryIngestionService(PublicLibraryApiClient apiClient,
                                         PlaceBulkUpsertRepository upsertRepository,
                                         GeocodingClient geocodingClient) {
        this.apiClient = apiClient;
        this.upsertRepository = upsertRepository;
        this.geocodingClient = geocodingClient;
    }

    public record LibraryIngestSummary(int totalFetched, int pages, int upserted, int geocoded,
                                       int geocodeReused, int geocodeFailed,
                                       int deactivated, int featuresBackfilled) {
    }

    /**
     * @param deactivateStale true면 이번 전체 수집에 없는 기존 활성 도서관을 soft-delete한다.
     *                        이 서비스는 항상 "페이지네이션으로 전량 수집"이라 부분 파일 재실행 걱정이
     *                        없는 CSV 경로와 다르지만, 호출부가 명시적으로 켜도록 기본은 false 유지
     *                        (IngestionService의 안전 규약과 동일하게 opt-in).
     */
    public LibraryIngestSummary ingestAll(boolean deactivateStale) {
        List<PublicLibraryRecord> all = fetchAll();

        List<PlaceRow> rows = new ArrayList<>();
        List<GeocodeCandidate> needGeocode = new ArrayList<>();
        Set<String> studyOkIds = new HashSet<>();
        Set<String> currentExternalIds = new HashSet<>();

        for (PublicLibraryRecord r : all) {
            if (r.lbrryNm() == null || r.lbrryNm().isBlank()) {
                continue;
            }
            String externalId = IngestIds.fallbackId(r.lbrryNm(), r.rdnmadr());
            currentExternalIds.add(externalId);
            if (parseSeatCount(r.seatCo()) > 0) {
                studyOkIds.add(externalId);
            }

            Double lat = parseDouble(r.latitude());
            Double lng = parseDouble(r.longitude());
            boolean validCoords = lat != null && lng != null
                    && lat >= 33 && lat <= 39 && lng >= 124 && lng <= 132;
            if (validCoords) {
                rows.add(new PlaceRow(externalId, r.lbrryNm(), r.rdnmadr(), lat, lng));
            } else if (r.rdnmadr() != null && !r.rdnmadr().isBlank()) {
                needGeocode.add(new GeocodeCandidate(externalId, r.lbrryNm(), r.rdnmadr()));
            }
        }

        int upserted = upsertRepository.upsertPlaces(rows, SOURCE_KEY, PlaceCategory.LIBRARY, false);
        GeocodeOutcome outcome = geocodeAndUpsert(needGeocode);

        int deactivated = deactivateStale
                ? upsertRepository.deactivateStale(SOURCE_KEY, currentExternalIds)
                : 0;

        // ADR-0006: 열람좌석수(seatCo)>0인 도서관만 study_ok/quiet — 레코드 조건부라 DefaultFeatureBackfill(카테고리 균일)을 쓰지 않는다.
        int featuresBackfilled = upsertRepository.backfillFeatures(SOURCE_KEY, studyOkIds,
                List.of(new FeatureSpec("study_ok", "true", 0.6), new FeatureSpec("quiet", "true", 0.5)));

        LibraryIngestSummary summary = new LibraryIngestSummary(all.size(), Math.min(MAX_PAGES, pagesFor(all.size())),
                upserted + outcome.upserted(), outcome.geocoded(), outcome.reused(), outcome.failed(),
                deactivated, featuresBackfilled);
        log.info("[library-api] fetched={} upserted={} geocoded={} geocodeReused={} geocodeFailed={} "
                        + "deactivated={} featuresBackfilled={}",
                summary.totalFetched(), summary.upserted(), summary.geocoded(), summary.geocodeReused(),
                summary.geocodeFailed(), summary.deactivated(), summary.featuresBackfilled());
        return summary;
    }

    private List<PublicLibraryRecord> fetchAll() {
        List<PublicLibraryRecord> all = new ArrayList<>();
        for (int pageNo = 1; pageNo <= MAX_PAGES; pageNo++) {
            LibraryPage page = apiClient.fetchPage(pageNo, PAGE_SIZE);
            if (page.items().isEmpty()) {
                break; // 정상 종료(NODATA_ERROR)든 오류든 — 더 이상 가져올 페이지가 없다는 뜻으로 취급.
            }
            all.addAll(page.items());
            if (page.items().size() < PAGE_SIZE) {
                break; // 마지막 페이지(요청보다 적게 옴)
            }
        }
        return all;
    }

    private record GeocodeOutcome(int upserted, int geocoded, int reused, int failed) {
    }

    /**
     * 지오코딩 대상이 소량(실측 100건당 2건 결측, 전국 3,555건 기준 수십 건 규모)이라 IngestionService의
     * 가상스레드+세마포어 동시성 없이 순차 호출한다 — 단순함이 이 규모에서는 더 낫다.
     */
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
        int upserted = upsertRepository.upsertPlaces(geocodedRows, SOURCE_KEY, PlaceCategory.LIBRARY, true);
        return new GeocodeOutcome(upserted, geocodedRows.size(), reused, failed);
    }

    private static int pagesFor(int totalFetched) {
        return (int) Math.ceil(totalFetched / (double) PAGE_SIZE);
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

    private static int parseSeatCount(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
