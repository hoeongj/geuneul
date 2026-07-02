package com.geuneul.domain.ingest;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * 전국무더위쉼터표준데이터 CSV 파서.
 *
 * 설계 포인트 (WORKLOG 2026-07-02):
 * - 헤더 유연 매칭: 표준데이터 컬럼명이 개정될 수 있고(2025-02 좌표 정책 변경 사례),
 *   문서상 위도/경도 필드 존재가 확정이 아니어서 별칭 리스트로 컬럼을 찾는다.
 * - 좌표 결측/파싱불가 행은 버리지 않고 skipped 로 계수 → 지오코딩 보완 백로그의 근거 데이터.
 * - 인코딩 주입: 공공데이터 CSV는 UTF-8/CP949(EUC-KR)가 혼재한다. 기본 UTF-8, 옵션으로 MS949.
 * - 고유 식별자(쉼터시설번호)가 없으면 sha256(name|address)을 대체 자연키로 사용해
 *   멱등 upsert 대상 (source, source_external_id)를 항상 채운다 (ADR-0002).
 */
@Component
public class CoolingShelterCsvParser {

    private static final List<String> ID_HEADERS = List.of("쉼터시설번호", "관리번호", "시설번호");
    private static final List<String> NAME_HEADERS = List.of("쉼터명칭", "쉼터명", "시설명칭", "시설명");
    private static final List<String> ADDR_HEADERS = List.of("소재지도로명주소", "도로명주소", "상세주소", "소재지지번주소", "지번주소", "주소");
    private static final List<String> LAT_HEADERS = List.of("위도", "위도(도)", "lat", "latitude");
    private static final List<String> LNG_HEADERS = List.of("경도", "경도(도)", "lng", "lon", "longitude");

    public record Result(List<ShelterRow> rows, int totalRecords, int skippedNoCoords) {
    }

    public Result parse(Path file, Charset charset) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, charset)) {
            return parse(reader);
        }
    }

    public Result parse(Reader reader) throws IOException {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreSurroundingSpaces(true)
                .setTrim(true)
                .build();

        List<ShelterRow> rows = new ArrayList<>();
        int total = 0;
        int skipped = 0;

        try (CSVParser parser = format.parse(reader)) {
            Map<String, Integer> headers = parser.getHeaderMap();
            String idCol = findColumn(headers, ID_HEADERS);
            String nameCol = requireColumn(headers, NAME_HEADERS, "이름");
            String addrCol = findColumn(headers, ADDR_HEADERS);
            String latCol = requireColumn(headers, LAT_HEADERS, "위도");
            String lngCol = requireColumn(headers, LNG_HEADERS, "경도");

            for (CSVRecord record : parser) {
                total++;
                Double lat = parseDouble(get(record, latCol));
                Double lng = parseDouble(get(record, lngCol));
                if (lat == null || lng == null || lat < 33 || lat > 39 || lng < 124 || lng > 132) {
                    skipped++; // 좌표 결측/국내 범위 밖 → 지오코딩 보완 대상
                    continue;
                }
                String name = get(record, nameCol);
                if (name == null || name.isBlank()) {
                    skipped++;
                    continue;
                }
                String address = addrCol == null ? null : get(record, addrCol);
                String externalId = idCol == null ? null : get(record, idCol);
                if (externalId == null || externalId.isBlank()) {
                    externalId = fallbackId(name, address);
                }
                rows.add(new ShelterRow(externalId, name, address, lat, lng));
            }
        }
        return new Result(rows, total, skipped);
    }

    private static String get(CSVRecord record, String column) {
        return record.isMapped(column) && record.isSet(column) ? record.get(column) : null;
    }

    private static String findColumn(Map<String, Integer> headers, List<String> aliases) {
        return aliases.stream().filter(headers::containsKey).findFirst().orElse(null);
    }

    private static String requireColumn(Map<String, Integer> headers, List<String> aliases, String what) {
        String col = findColumn(headers, aliases);
        if (col == null) {
            throw new IllegalArgumentException(
                    "CSV에서 %s 컬럼을 찾지 못했습니다. 시도한 별칭: %s / 실제 헤더: %s"
                            .formatted(what, aliases, headers.keySet()));
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

    /** 고유번호 없는 행의 대체 자연키 — 같은 (이름|주소)는 재적재 시 같은 키로 수렴한다. */
    static String fallbackId(String name, String address) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((name + "|" + (address == null ? "" : address))
                    .getBytes(StandardCharsets.UTF_8));
            return "h:" + HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
