package com.geuneul.domain.ingest.openapi;

import com.geuneul.domain.ingest.FeatureSpec;
import com.geuneul.domain.ingest.IngestIds;
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
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 도서관 오픈API 인제스천 오케스트레이션 — 매핑(좌표유효/결측)·seatCo 조건부 백필·
 * deactivateStale opt-in을 실 DB 없이 협력 객체(mock)로 검증한다.
 */
class PublicLibraryIngestionServiceTest {

    private PublicLibraryApiClient apiClient;
    private PlaceBulkUpsertRepository upsertRepository;
    private GeocodingClient geocodingClient;
    private PublicLibraryIngestionService service;

    @BeforeEach
    void setUp() {
        apiClient = mock(PublicLibraryApiClient.class);
        upsertRepository = mock(PlaceBulkUpsertRepository.class);
        geocodingClient = mock(GeocodingClient.class);
        service = new PublicLibraryIngestionService(apiClient, upsertRepository, geocodingClient);
        when(upsertRepository.upsertPlaces(any(), anyString(), any(), eq(false))).thenReturn(0);
        when(upsertRepository.upsertPlaces(any(), anyString(), any(), eq(true))).thenReturn(0);
        when(upsertRepository.findGeocodedAddresses(anyString())).thenReturn(Map.of());
        when(upsertRepository.backfillFeatures(anyString(), any(), any())).thenReturn(0);
    }

    private static PublicLibraryRecord record(String name, String addr, String lat, String lng, String seatCo) {
        return new PublicLibraryRecord(name, addr, lat, lng, seatCo);
    }

    @Test
    @DisplayName("좌표 보유 행은 그대로 upsert, 좌표 결측+주소보유 행은 지오코딩 경로로 분리된다")
    void separatesValidCoordsFromNeedsGeocoding() {
        when(apiClient.fetchPage(1, 500)).thenReturn(new LibraryPage(List.of(
                record("숭실대 중앙도서관", "서울 동작구 상도로 369", "37.4962", "126.9573", "800"),
                record("좌표없는 도서관", "서울 동작구 어딘가", "", "", "100")
        ), 2));
        when(apiClient.fetchPage(2, 500)).thenReturn(LibraryPage.empty());
        when(geocodingClient.geocode("서울 동작구 어딘가")).thenReturn(Optional.of(new GeocodingClient.LatLng(37.5, 126.9)));

        service.ingestAll(false);

        var rowsCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(upsertRepository).upsertPlaces(rowsCaptor.capture(), eq(PublicLibraryIngestionService.SOURCE_KEY),
                eq(PlaceCategory.LIBRARY), eq(false));
        List<PlaceRow> coordRows = rowsCaptor.getValue();
        assertThat(coordRows).hasSize(1);
        assertThat(coordRows.get(0).name()).isEqualTo("숭실대 중앙도서관");

        verify(geocodingClient).geocode("서울 동작구 어딘가");
        var geocodedCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(upsertRepository).upsertPlaces(geocodedCaptor.capture(), eq(PublicLibraryIngestionService.SOURCE_KEY),
                eq(PlaceCategory.LIBRARY), eq(true));
        List<PlaceRow> geocodedRows = geocodedCaptor.getValue();
        assertThat(geocodedRows).hasSize(1);
        assertThat(geocodedRows.get(0).lat()).isEqualTo(37.5);
    }

    @Test
    @DisplayName("seatCo>0인 도서관만 study_ok/quiet 백필 대상 external_id로 넘어간다")
    void onlyPositiveSeatCountBackfillsStudyFeatures() {
        when(apiClient.fetchPage(1, 500)).thenReturn(new LibraryPage(List.of(
                record("좌석있는 도서관", "서울 동작구 A", "37.4962", "126.9573", "800"),
                record("좌석0인 도서관", "서울 동작구 B", "37.5", "126.95", "0"),
                record("좌석결측 도서관", "서울 동작구 C", "37.5", "126.95", "")
        ), 3));
        when(apiClient.fetchPage(2, 500)).thenReturn(LibraryPage.empty());

        service.ingestAll(false);

        String expectedId = IngestIds.fallbackId("좌석있는 도서관", "서울 동작구 A");
        var idsCaptor = org.mockito.ArgumentCaptor.forClass(Set.class);
        verify(upsertRepository).backfillFeatures(eq(PublicLibraryIngestionService.SOURCE_KEY), idsCaptor.capture(),
                eq(List.of(new FeatureSpec("study_ok", "true", 0.6), new FeatureSpec("quiet", "true", 0.5))));
        assertThat(idsCaptor.getValue()).containsExactly(expectedId);
    }

    @Test
    @DisplayName("deactivateStale=false(기본)면 soft-delete를 호출하지 않는다")
    void deactivateStaleDefaultsToFalse() {
        when(apiClient.fetchPage(1, 500)).thenReturn(new LibraryPage(List.of(
                record("도서관A", "주소A", "37.5", "126.9", "10")
        ), 1));

        service.ingestAll(false);

        verify(upsertRepository, never()).deactivateStale(anyString(), any());
    }

    @Test
    @DisplayName("deactivateStale=true면 이번 수집의 전체 external_id 집합으로 soft-delete를 호출한다")
    void deactivateStaleTrueCallsWithCurrentSnapshot() {
        when(apiClient.fetchPage(1, 500)).thenReturn(new LibraryPage(List.of(
                record("도서관A", "주소A", "37.5", "126.9", "10")
        ), 1));
        when(apiClient.fetchPage(2, 500)).thenReturn(LibraryPage.empty());

        service.ingestAll(true);

        verify(upsertRepository, times(1)).deactivateStale(eq(PublicLibraryIngestionService.SOURCE_KEY), any());
    }

    @Test
    @DisplayName("페이지네이션: 마지막 페이지(요청보다 적은 건수)에서 멈춘다")
    void stopsAtLastPage() {
        List<PublicLibraryRecord> full = List.of(
                record("A", "addrA", "37.1", "126.1", "1"),
                record("B", "addrB", "37.2", "126.2", "1")
        );
        when(apiClient.fetchPage(1, 500)).thenReturn(new LibraryPage(full, 2)); // items.size()(2) < numOfRows(500) → 마지막

        var summary = service.ingestAll(false);

        assertThat(summary.totalFetched()).isEqualTo(2);
        verify(apiClient, times(1)).fetchPage(1, 500);
        verify(apiClient, never()).fetchPage(2, 500);
    }

    @Test
    @DisplayName("reportedTotal 전 짧은 페이지는 mutation 전에 실패해 stale 비활성화를 막는다")
    void rejectsIncompleteSnapshotBeforeMutation() {
        when(apiClient.fetchPage(1, 500)).thenReturn(new LibraryPage(List.of(
                record("도서관A", "주소A", "37.5", "126.9", "10")
        ), 2));

        assertThatThrownBy(() -> service.ingestAll(true))
                .isInstanceOf(LibraryApiException.class)
                .hasMessageContaining("reportedTotal");

        verify(upsertRepository, never()).upsertPlaces(any(), anyString(), any(), anyBoolean());
        verify(upsertRepository, never()).deactivateStale(anyString(), any());
        verify(upsertRepository, never()).backfillFeatures(anyString(), any(), any());
    }

    @Test
    @DisplayName("이름 또는 좌표·주소가 없어 식별 불가능한 행이 있으면 partial로 남기고 stale 비활성화를 막는다")
    void unidentifiableRowsMakeSnapshotPartial() {
        when(apiClient.fetchPage(1, 500)).thenReturn(new LibraryPage(List.of(
                record("정상 도서관", "주소A", "37.5", "126.9", "10"),
                record("", "주소B", "37.5", "126.9", "10"),
                record("주소도 좌표도 없는 도서관", "", "", "", "10")
        ), 3));

        var summary = service.ingestAll(true);

        assertThat(summary.complete()).isFalse();
        assertThat(summary.skipped()).isEqualTo(2);
        verify(upsertRepository, never()).deactivateStale(anyString(), any());
    }

    @Test
    @DisplayName("중간 페이지 API 장애는 어떤 DB mutation도 하기 전에 전체 실행을 실패시킨다")
    void middlePageFailureStopsBeforeMutation() {
        List<PublicLibraryRecord> firstPage = IntStream.range(0, 500)
                .mapToObj(i -> record("도서관" + i, "서울 " + i, "37.5", "126.9", "10"))
                .toList();
        when(apiClient.fetchPage(1, 500)).thenReturn(new LibraryPage(firstPage, 501));
        when(apiClient.fetchPage(2, 500)).thenThrow(new LibraryApiException(2, "HTTP 또는 응답 파싱 오류"));

        assertThatThrownBy(() -> service.ingestAll(true))
                .isInstanceOf(LibraryApiException.class)
                .hasMessageContaining("page=2");

        verify(upsertRepository, never()).upsertPlaces(any(), anyString(), any(), anyBoolean());
        verify(upsertRepository, never()).deactivateStale(anyString(), any());
        verify(upsertRepository, never()).backfillFeatures(anyString(), any(), any());
    }
}
