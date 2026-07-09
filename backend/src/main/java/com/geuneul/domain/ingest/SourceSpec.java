package com.geuneul.domain.ingest;

import com.geuneul.domain.place.PlaceCategory;

import java.util.Arrays;
import java.util.List;

/**
 * 공공데이터 소스 정의 — 소스가 늘 때 여기 한 줄과 헤더 별칭만 추가하면 된다.
 * 별칭 리스트를 두는 이유: 표준데이터 컬럼명이 개정될 수 있고(2025-02 공중화장실 좌표 정책 변경 전례),
 * 소스별로 표기가 다르다(위도 vs WGS84위도).
 */
public enum SourceSpec {

    COOLING_SHELTER(
            "cooling_shelter",                 // CLI 이름 (--ingest.source=)
            "cooling_shelter_std",             // places.source 값
            PlaceCategory.COOLING_SHELTER,
            // 한글 별칭 = data.go.kr 표준 포맷 / 영문 코드 = 행정안전부 재난안전데이터(safetydata) 포맷
            List.of("쉼터시설번호", "관리번호", "시설번호", "RSTR_FCLTY_NO"),
            List.of("쉼터명칭", "쉼터명", "시설명칭", "시설명", "RSTR_NM"),
            List.of("소재지도로명주소", "도로명주소", "도로명상세주소", "상세주소", "소재지지번주소", "지번주소", "주소", "RN_DTL_ADRES", "DTL_ADRES"),
            List.of("위도", "위도(도)", "lat", "latitude", "LA"),
            List.of("경도", "경도(도)", "lng", "lon", "longitude", "LO")),

    PUBLIC_TOILET(
            "public_toilet",
            "public_toilet_std",
            PlaceCategory.TOILET,
            List.of("번호", "관리번호"),
            List.of("화장실명", "화장실명칭", "시설명"),
            List.of("소재지도로명주소", "소재지지번주소", "도로명주소", "지번주소", "주소"),
            List.of("WGS84위도", "위도", "lat", "latitude"),
            List.of("WGS84경도", "경도", "lng", "lon", "longitude"));

    // LIBRARY(전국도서관표준데이터)는 CSV가 아니라 실측 확인된 JSON 오픈API로 적재한다
    // (domain.ingest.openapi.PublicLibraryIngestionService, ADR-0006) — SourceSpec(CSV 전용) 대상이 아니다.

    private final String cliName;
    private final String sourceKey;
    private final PlaceCategory category;
    private final List<String> idAliases;
    private final List<String> nameAliases;
    private final List<String> addressAliases;
    private final List<String> latAliases;
    private final List<String> lngAliases;

    SourceSpec(String cliName, String sourceKey, PlaceCategory category,
               List<String> idAliases, List<String> nameAliases, List<String> addressAliases,
               List<String> latAliases, List<String> lngAliases) {
        this.cliName = cliName;
        this.sourceKey = sourceKey;
        this.category = category;
        this.idAliases = idAliases;
        this.nameAliases = nameAliases;
        this.addressAliases = addressAliases;
        this.latAliases = latAliases;
        this.lngAliases = lngAliases;
    }

    public static SourceSpec fromCliName(String cliName) {
        return Arrays.stream(values())
                .filter(s -> s.cliName.equals(cliName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "지원하지 않는 ingest.source: " + cliName + " (지원: "
                                + Arrays.stream(values()).map(s -> s.cliName).toList() + ")"));
    }

    public String cliName() {
        return cliName;
    }

    public String sourceKey() {
        return sourceKey;
    }

    public PlaceCategory category() {
        return category;
    }

    public List<String> idAliases() {
        return idAliases;
    }

    public List<String> nameAliases() {
        return nameAliases;
    }

    public List<String> addressAliases() {
        return addressAliases;
    }

    public List<String> latAliases() {
        return latAliases;
    }

    public List<String> lngAliases() {
        return lngAliases;
    }
}
