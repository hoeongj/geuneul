package com.geuneul.domain.ingest.safetydata;

import com.geuneul.domain.ingest.FeatureSpec;
import com.geuneul.domain.ingest.PlaceBulkUpsertRepository;
import com.geuneul.domain.ingest.PlaceRow;
import com.geuneul.domain.ingest.geocode.GeocodingClient;
import com.geuneul.domain.place.PlaceCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 무더위쉼터 인제스천 — 쉼터 소스(cooling_shelter_std)로 upsert, 냉방기>0만 air_conditioned 백필,
 * deactivate-stale은 전량 수집 완료 시에만 적용, 좌표 결측 시 지오코딩 폴백을 실 DB 없이 검증한다.
 */
class ShelterIngestionServiceTest {

    private static final String SOURCE = "cooling_shelter_std";

    private SafetyDataApiClient apiClient;
    private PlaceBulkUpsertRepository upsertRepository;
    private GeocodingClient geocodingClient;
    private ShelterIngestionService service;

    @BeforeEach
    void setUp() {
        apiClient = mock(SafetyDataApiClient.class);
        upsertRepository = mock(PlaceBulkUpsertRepository.class);
        geocodingClient = mock(GeocodingClient.class);
        service = new ShelterIngestionService(apiClient, upsertRepository, geocodingClient);
        when(apiClient.fetchPage(anyInt(), anyInt())).thenReturn(ShelterPage.empty());
        when(upsertRepository.upsertPlaces(any(), anyString(), any(), eq(false))).thenReturn(0);
        when(upsertRepository.upsertPlaces(any(), anyString(), any(), eq(true))).thenReturn(0);
        when(upsertRepository.findGeocodedAddresses(anyString())).thenReturn(Map.of());
        when(upsertRepository.backfillFeatures(anyString(), any(), any())).thenReturn(0);
    }

    private static ShelterRecord shelter(long no, String name, String addr, Double la, Double lo, Integer arc) {
        return new ShelterRecord(no, name, addr, null, la, lo, arc);
    }

    @Test
    @DisplayName("쉼터 소스로 upsert하고, 냉방기>0인 쉼터만 air_conditioned를 백필한다")
    void upsertsWithShelterSourceAndAirConditionedBackfill() {
        when(apiClient.fetchPage(1, 1000)).thenReturn(new ShelterPage(List.of(
                shelter(1, "냉방쉼터", "서울 A", 37.5, 127.0, 4),
                shelter(2, "무냉방쉼터", "서울 B", 37.6, 127.1, 0)), 2));

        service.ingestAll(false);

        verify(upsertRepository).upsertPlaces(any(), eq(SOURCE), eq(PlaceCategory.COOLING_SHELTER), eq(false));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<String>> ids = ArgumentCaptor.forClass(Set.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FeatureSpec>> feats = ArgumentCaptor.forClass(List.class);
        verify(upsertRepository).backfillFeatures(eq(SOURCE), ids.capture(), feats.capture());
        assertThat(ids.getValue()).containsExactly("shelter:1"); // 냉방기 4>0만
        assertThat(feats.getValue()).extracting(FeatureSpec::featureType).containsExactly("air_conditioned");
    }

    @Test
    @DisplayName("전량 수집 완료 시 deactivate-stale이 적용된다(샘플 대체)")
    void deactivateStaleWhenComplete() {
        when(apiClient.fetchPage(1, 1000)).thenReturn(new ShelterPage(List.of(
                shelter(1, "쉼터A", "서울 A", 37.5, 127.0, 1),
                shelter(2, "쉼터B", "서울 B", 37.6, 127.1, 1)), 2)); // totalCount=2, 2건 수집 → complete

        service.ingestAll(true);

        verify(upsertRepository).deactivateStale(eq(SOURCE), any());
    }

    @Test
    @DisplayName("부분 수집(totalCount 미달)이면 deactivate-stale을 건너뛴다(사고 방지)")
    void skipsDeactivateStaleWhenIncomplete() {
        when(apiClient.fetchPage(1, 1000)).thenReturn(new ShelterPage(List.of(
                shelter(1, "쉼터A", "서울 A", 37.5, 127.0, 1)), 100)); // totalCount=100인데 1건만 → incomplete

        service.ingestAll(true);

        verify(upsertRepository, never()).deactivateStale(anyString(), any());
    }

    @Test
    @DisplayName("좌표 결측 + 주소 보유 쉼터는 지오코딩 경로로 넘어간다")
    void geocodesMissingCoordinates() {
        when(apiClient.fetchPage(1, 1000)).thenReturn(new ShelterPage(List.of(
                shelter(1, "좌표없는쉼터", "서울 좌표없음", null, null, 0)), 1));
        when(geocodingClient.geocode("서울 좌표없음"))
                .thenReturn(Optional.of(new GeocodingClient.LatLng(37.5, 126.9)));

        service.ingestAll(false);

        verify(geocodingClient).geocode("서울 좌표없음");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PlaceRow>> captor = ArgumentCaptor.forClass(List.class);
        verify(upsertRepository).upsertPlaces(captor.capture(), eq(SOURCE), eq(PlaceCategory.COOLING_SHELTER), eq(true));
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).lat()).isEqualTo(37.5);
    }
}
