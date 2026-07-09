package com.geuneul.domain.ingest;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 공공데이터 표준데이터셋 범용 CSV 파서 — 소스별 차이는 전부 {@link SourceSpec}이 흡수한다.
 *
 * 설계 포인트 (WORKLOG 2026-07-02, ADR-0002/0003):
 * - 헤더 유연 매칭(별칭) + BOM 제거 → 표준데이터 컬럼 개정·행안부 영문코드 헤더·UTF-8 BOM에 내성.
 * - 좌표 컬럼은 "옵션": 2025-02 이후 공중화장실처럼 좌표가 아예 없는 소스는
 *   주소 있는 행을 {@link GeocodeCandidate}로 수집(지오코딩 경로), 주소도 없으면 skipped.
 * - 인코딩 주입: 공공데이터 CSV는 UTF-8/CP949(EUC-KR) 혼재. 기본 UTF-8.
 * - 고유번호 없으면 sha256(name|address) 결정적 대체키 → (source, source_external_id) 멱등 upsert 항상 성립.
 */
@Component
public class StandardCsvParser {

    public record Result(
            List<PlaceRow> rows,                 // 좌표 보유 행
            List<GeocodeCandidate> needGeocode,  // 좌표 없음 + 주소 있음 → 지오코딩 대상
            int totalRecords,
            int skipped                          // 좌표도 주소도 못 살리는 행
    ) {
    }

    public Result parse(Path file, Charset charset, SourceSpec spec) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, charset)) {
            return parse(reader, spec);
        }
    }

    public Result parse(Reader reader, SourceSpec spec) throws IOException {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreSurroundingSpaces(true)
                .setTrim(true)
                .build();

        List<PlaceRow> rows = new ArrayList<>();
        List<GeocodeCandidate> needGeocode = new ArrayList<>();
        int total = 0;
        int skipped = 0;

        try (CSVParser parser = format.parse(reader)) {
            // BOM(﻿)이 첫 헤더에 붙으면 별칭 매칭이 깨진다 → 정규화 맵으로 원본 헤더명을 찾는다.
            Map<String, String> normalized = normalizeHeaders(parser.getHeaderMap());
            String idCol = findColumn(normalized, spec.idAliases());
            String nameCol = requireColumn(normalized, spec.nameAliases(), "이름", spec);
            String addrCol = findColumn(normalized, spec.addressAliases());
            String latCol = findColumn(normalized, spec.latAliases());   // 옵션 — 없으면 전행 지오코딩 경로
            String lngCol = findColumn(normalized, spec.lngAliases());

            for (CSVRecord record : parser) {
                total++;
                String name = get(record, nameCol);
                if (name == null || name.isBlank()) {
                    skipped++;
                    continue;
                }
                if (spec.nameAliases().contains(name)) {
                    // 행안부(safetydata) 이중 헤더 파일: 1행 영문코드, 2행 한글 라벨 →
                    // 라벨 행이 데이터로 읽히는 것을 차단(이름 값이 별칭 자체면 헤더 에코 행)
                    skipped++;
                    continue;
                }
                String address = addrCol == null ? null : get(record, addrCol);
                String externalId = idCol == null ? null : get(record, idCol);
                if (externalId == null || externalId.isBlank()) {
                    externalId = fallbackId(name, address);
                }

                Double lat = latCol == null ? null : parseDouble(get(record, latCol));
                Double lng = lngCol == null ? null : parseDouble(get(record, lngCol));
                boolean validCoords = lat != null && lng != null
                        && lat >= 33 && lat <= 39 && lng >= 124 && lng <= 132; // 국내 범위

                if (validCoords) {
                    rows.add(new PlaceRow(externalId, name, address, lat, lng));
                } else if (address != null && !address.isBlank()) {
                    needGeocode.add(new GeocodeCandidate(externalId, name, address));
                } else {
                    skipped++;
                }
            }
        }
        return new Result(rows, needGeocode, total, skipped);
    }

    private static Map<String, String> normalizeHeaders(Map<String, Integer> headerMap) {
        Map<String, String> normalized = new HashMap<>();
        for (String original : headerMap.keySet()) {
            // BOM이 여는 따옴표보다 앞에 오면(﻿"COL") CSV 파서가 비인용 필드로 읽어
            // 키에 따옴표까지 남는다 → BOM 제거 후 감싼 따옴표도 벗긴다.
            String cleaned = original.replace("﻿", "").trim();
            if (cleaned.length() >= 2 && cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
                cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
            }
            normalized.put(cleaned, original);
        }
        return normalized;
    }

    private static String get(CSVRecord record, String column) {
        return column != null && record.isMapped(column) && record.isSet(column) ? record.get(column) : null;
    }

    /** @return 매칭된 "원본" 헤더명 (BOM 포함 원본으로 record에 접근해야 하므로) */
    private static String findColumn(Map<String, String> normalized, List<String> aliases) {
        return aliases.stream()
                .filter(normalized::containsKey)
                .map(normalized::get)
                .findFirst()
                .orElse(null);
    }

    private static String requireColumn(Map<String, String> normalized, List<String> aliases,
                                        String what, SourceSpec spec) {
        String col = findColumn(normalized, aliases);
        if (col == null) {
            throw new IllegalArgumentException(
                    "[%s] CSV에서 %s 컬럼을 찾지 못했습니다. 시도한 별칭: %s / 실제 헤더: %s"
                            .formatted(spec.cliName(), what, aliases, normalized.keySet()));
        }
        return col;
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

    /** 고유번호 없는 행의 대체 자연키 — 같은 (이름|주소)는 재적재 시 같은 키로 수렴한다. {@link IngestIds}에 위임. */
    static String fallbackId(String name, String address) {
        return IngestIds.fallbackId(name, address);
    }
}
