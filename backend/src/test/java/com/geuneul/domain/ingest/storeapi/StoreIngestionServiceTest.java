package com.geuneul.domain.ingest.storeapi;

import com.geuneul.domain.ingest.PlaceBulkUpsertRepository;
import com.geuneul.domain.ingest.PlaceRow;
import com.geuneul.domain.ingest.geocode.GeocodingClient;
import com.geuneul.domain.place.PlaceCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 상권정보 인제스천 오케스트레이션 — <b>계약 검증 후(TS-026)</b> 서버측 업종코드 필터(I21201/R10202)로
 * 카테고리별로 나눠 페이지네이션·upsert하는지, 격자(ingestArea)가 셀을 순회·집계하는지 실 DB 없이 검증한다.
 */
class StoreIngestionServiceTest {

    private StoreApiClient apiClient;
    private PlaceBulkUpsertRepository upsertRepository;
    private GeocodingClient geocodingClient;
    private StoreIngestionService service;

    @BeforeEach
    void setUp() {
        apiClient = mock(StoreApiClient.class);
        upsertRepository = mock(PlaceBulkUpsertRepository.class);
        geocodingClient = mock(GeocodingClient.class);
        service = new StoreIngestionService(apiClient, upsertRepository, geocodingClient);
        // 기본: 어떤 코드/페이지든 빈 페이지 — 각 테스트가 관심 코드만 덮어쓴다(둘 다 stub되어야 NPE 없음).
        when(apiClient.searchByRadius(anyDouble(), anyDouble(), anyInt(), nullable(String.class), anyInt(), anyInt()))
                .thenReturn(StorePage.empty());
        when(upsertRepository.upsertPlaces(any(), anyString(), any(), eq(false))).thenReturn(0);
        when(upsertRepository.upsertPlaces(any(), anyString(), any(), eq(true))).thenReturn(0);
        when(upsertRepository.findGeocodedAddresses(anyString())).thenReturn(Map.of());
        when(upsertRepository.backfillFeatures(anyString(), any(), any())).thenReturn(0);
    }

    private static StoreRecord store(String id, String name, String code, String addr, Double lon, Double lat) {
        return new StoreRecord(id, name, code, code, addr, null, lon, lat);
    }

    @Test
    @DisplayName("업종코드별 서버측 필터 결과를 각 카테고리 소스로 나눠 upsert한다")
    void splitsByCategoryCode() {
        when(apiClient.searchByRadius(37.5, 127.0, 500, "I21201", 1, 1000)).thenReturn(new StorePage(List.of(
                store("S-1", "그늘카페", "I21201", "서울 동작구 A", 126.95, 37.50)), 1));
        when(apiClient.searchByRadius(37.5, 127.0, 500, "R10202", 1, 1000)).thenReturn(new StorePage(List.of(
                store("S-2", "상도독서실", "R10202", "서울 동작구 B", 126.96, 37.51)), 1));

        var summary = service.ingestRegion(37.5, 127.0, 500);

        assertThat(summary.totalFetched()).isEqualTo(2);
        assertThat(summary.byCategory()).containsEntry(PlaceCategory.CAFE, 1)
                .containsEntry(PlaceCategory.STUDY_CAFE, 1);
        verify(upsertRepository).upsertPlaces(any(), eq("store_cafe_api"), eq(PlaceCategory.CAFE), eq(false));
        verify(upsertRepository).upsertPlaces(any(), eq("store_study_cafe_api"), eq(PlaceCategory.STUDY_CAFE), eq(false));
    }

    @Test
    @DisplayName("STUDY_CAFE만 study_ok/quiet 균일 백필 대상 — CAFE는 백필하지 않는다(UGC 전용)")
    void onlyStudyCafeGetsDefaultFeatureBackfill() {
        when(apiClient.searchByRadius(37.5, 127.0, 500, "I21201", 1, 1000)).thenReturn(new StorePage(List.of(
                store("S-1", "그늘카페", "I21201", "서울 동작구 A", 126.95, 37.50)), 1));
        when(apiClient.searchByRadius(37.5, 127.0, 500, "R10202", 1, 1000)).thenReturn(new StorePage(List.of(
                store("S-2", "상도독서실", "R10202", "서울 동작구 B", 126.96, 37.51)), 1));
        when(upsertRepository.backfillFeatures(eq("store_study_cafe_api"), any(),
                eq(com.geuneul.domain.ingest.DefaultFeatureBackfill.forCategory(PlaceCategory.STUDY_CAFE))))
                .thenReturn(3);

        var summary = service.ingestRegion(37.5, 127.0, 500);

        assertThat(summary.featuresBackfilled()).isEqualTo(3);
        verify(upsertRepository).backfillFeatures(eq("store_study_cafe_api"), any(),
                eq(com.geuneul.domain.ingest.DefaultFeatureBackfill.forCategory(PlaceCategory.STUDY_CAFE)));
        verify(upsertRepository).backfillFeatures(eq("store_cafe_api"), any(), eq(List.of()));
    }

    @Test
    @DisplayName("좌표 결측 + 주소 보유 상가는 지오코딩 경로로 넘어간다")
    void geocodesMissingCoordinates() {
        when(apiClient.searchByRadius(37.5, 127.0, 500, "I21201", 1, 1000)).thenReturn(new StorePage(List.of(
                store("S-1", "그늘카페", "I21201", "서울 동작구 좌표없음", null, null)), 1));
        when(geocodingClient.geocode("서울 동작구 좌표없음"))
                .thenReturn(Optional.of(new GeocodingClient.LatLng(37.5, 126.9)));

        service.ingestRegion(37.5, 127.0, 500);

        verify(geocodingClient).geocode("서울 동작구 좌표없음");
        var captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(upsertRepository).upsertPlaces(captor.capture(), eq("store_cafe_api"), eq(PlaceCategory.CAFE), eq(true));
        List<PlaceRow> geocodedRows = captor.getValue();
        assertThat(geocodedRows).hasSize(1);
        assertThat(geocodedRows.get(0).lat()).isEqualTo(37.5);
    }

    @Test
    @DisplayName("deactivateStale 관련 메서드를 호출하지 않는다(반경 단위 부분 스냅샷이라 의도적으로 미지원)")
    void doesNotCallDeactivateStale() {
        service.ingestRegion(37.5, 127.0, 500);

        verify(upsertRepository, never()).deactivateStale(anyString(), any());
    }

    @Test
    @DisplayName("ingestArea는 bbox를 격자로 순회하며 셀별 결과를 집계한다")
    void ingestAreaTraversesGridAndAggregates() {
        // radius=1500, STEP_FACTOR=1.3 → stepLat≈0.0175° → 위도 span 0.02°는 2행, 경도 span 0.001°는 1열 = 2셀.
        when(apiClient.searchByRadius(anyDouble(), anyDouble(), eq(1500), eq("I21201"), eq(1), eq(1000)))
                .thenReturn(new StorePage(List.of(
                        store("S-1", "그늘카페", "I21201", "서울 동작구 A", 126.95, 37.50)), 1));

        var summary = service.ingestArea(126.950, 37.490, 126.951, 37.510, 1500);

        // 셀마다 카페 1건씩 → 2셀이면 CAFE 집계 2. (upsert 반환값은 목이라 0이지만 byCategory는 rows.size 누적)
        assertThat(summary.byCategory()).containsEntry(PlaceCategory.CAFE, 2);
        verify(apiClient, atLeast(2)).searchByRadius(anyDouble(), anyDouble(), eq(1500), eq("I21201"), eq(1), eq(1000));
    }

    @Test
    @DisplayName("reportedTotal 전 두 번째 페이지 장애는 해당 업종을 mutation 전에 실패시킨다")
    void secondPageFailureStopsBeforeMutation() {
        List<StoreRecord> firstPage = IntStream.range(0, 1000)
                .mapToObj(i -> store("S-" + i, "상가" + i, "R10202", "서울 " + i, 126.95, 37.5))
                .toList();
        when(apiClient.searchByRadius(anyDouble(), anyDouble(), anyInt(), anyString(), eq(1), eq(1000)))
                .thenReturn(new StorePage(firstPage, 1001));
        when(apiClient.searchByRadius(anyDouble(), anyDouble(), anyInt(), anyString(), eq(2), eq(1000)))
                .thenThrow(new StoreApiException(2, "HTTP 또는 응답 파싱 오류"));

        assertThatThrownBy(() -> service.ingestRegion(37.5, 127.0, 500))
                .isInstanceOf(StoreApiException.class)
                .hasMessageContaining("page=2");

        verify(upsertRepository, never()).upsertPlaces(any(), anyString(), any(), anyBoolean());
        verify(upsertRepository, never()).backfillFeatures(anyString(), any(), any());
    }

    @Test
    @DisplayName("이름은 있지만 좌표와 주소가 모두 없는 상가는 skipped인 PARTIAL 결과가 된다")
    void invalidLocationIsSkipped() {
        when(apiClient.searchByRadius(37.5, 127.0, 500, "R10202", 1, 1000)).thenReturn(new StorePage(List.of(
                store("S-1", "위치없는 독서실", "R10202", "", null, null)), 1));

        var summary = service.ingestRegion(37.5, 127.0, 500);

        assertThat(summary.totalFetched()).isEqualTo(1);
        assertThat(summary.classified()).isZero();
        assertThat(com.geuneul.domain.ingest.IngestRunResult.from(summary).partial()).isTrue();
        verify(geocodingClient, never()).geocode(anyString());
    }
}
