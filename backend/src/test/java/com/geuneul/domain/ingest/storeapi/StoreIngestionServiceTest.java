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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 상권정보 인제스천 오케스트레이션 — 클라이언트가 STUDY_CAFE/CAFE/무관 업종을 섞어 반환해도
 * {@link StoreCategoryMapper}로 분류해 카테고리별로 나눠 upsert하는지 실 DB 없이 검증한다.
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
        when(upsertRepository.upsertPlaces(any(), anyString(), any(), eq(false))).thenReturn(0);
        when(upsertRepository.upsertPlaces(any(), anyString(), any(), eq(true))).thenReturn(0);
        when(upsertRepository.findGeocodedAddresses(anyString())).thenReturn(Map.of());
        when(upsertRepository.backfillFeatures(anyString(), any(), any())).thenReturn(0);
    }

    private static StoreRecord store(String id, String name, String indsSclsNm, String addr, String lon, String lat) {
        return new StoreRecord(id, name, indsSclsNm, "I00000", addr, null, lon, lat);
    }

    @Test
    @DisplayName("무관 업종은 버리고, 커피/독서실만 각 카테고리로 나눠 upsert한다")
    void classifiesAndSplitsByCategory() {
        when(apiClient.searchByRadius(37.5, 127.0, 500, null, 1, 500)).thenReturn(new StorePage(List.of(
                store("S-1", "그늘카페", "커피전문점/카페/다방", "서울 동작구 A", "126.95", "37.50"),
                store("S-2", "상도독서실", "독서실", "서울 동작구 B", "126.96", "37.51"),
                store("S-3", "치킨나라", "치킨전문점", "서울 동작구 C", "126.97", "37.52")
        ), 3));
        when(apiClient.searchByRadius(37.5, 127.0, 500, null, 2, 500)).thenReturn(StorePage.empty());

        var summary = service.ingestRegion(37.5, 127.0, 500);

        assertThat(summary.totalFetched()).isEqualTo(3);
        assertThat(summary.classified()).isEqualTo(2); // 치킨집 제외

        verify(upsertRepository).upsertPlaces(any(), eq("store_cafe_api"), eq(PlaceCategory.CAFE), eq(false));
        verify(upsertRepository).upsertPlaces(any(), eq("store_study_cafe_api"), eq(PlaceCategory.STUDY_CAFE), eq(false));
    }

    @Test
    @DisplayName("STUDY_CAFE만 study_ok/quiet 균일 백필 대상 — CAFE는 백필하지 않는다(UGC 전용)")
    void onlyStudyCafeGetsDefaultFeatureBackfill() {
        when(apiClient.searchByRadius(37.5, 127.0, 500, null, 1, 500)).thenReturn(new StorePage(List.of(
                store("S-1", "그늘카페", "커피전문점/카페/다방", "서울 동작구 A", "126.95", "37.50"),
                store("S-2", "상도독서실", "독서실", "서울 동작구 B", "126.96", "37.51")
        ), 2));

        service.ingestRegion(37.5, 127.0, 500);

        verify(upsertRepository).backfillFeatures(eq("store_study_cafe_api"), any(),
                eq(com.geuneul.domain.ingest.DefaultFeatureBackfill.forCategory(PlaceCategory.STUDY_CAFE)));
        verify(upsertRepository).backfillFeatures(eq("store_cafe_api"), any(), eq(List.of()));
    }

    @Test
    @DisplayName("좌표 결측 + 주소 보유 상가는 지오코딩 경로로 넘어간다")
    void geocodesMissingCoordinates() {
        when(apiClient.searchByRadius(37.5, 127.0, 500, null, 1, 500)).thenReturn(new StorePage(List.of(
                store("S-1", "그늘카페", "카페", "서울 동작구 좌표없음", "", "")
        ), 1));
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
        when(apiClient.searchByRadius(37.5, 127.0, 500, null, 1, 500)).thenReturn(StorePage.empty());

        service.ingestRegion(37.5, 127.0, 500);

        verify(upsertRepository, never()).deactivateStale(anyString(), any());
    }
}
