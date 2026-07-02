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
 * 공공데이터 표준데이터셋 범용 CSV 파서 — 소스별 차이는 전부 {@link SourceSpec}이 흡수한다.
 *
 * 설계 포인트 (WORKLOG 2026-07-02, ADR-0002):
 * - 헤더 유연 매칭(별칭 리스트) → 표준데이터 컬럼 개정에 내성.
 * - 좌표 결측/파싱불가/국내범위 밖 행은 버리지 않고 skipped 계수 → 지오코딩 보완(P2) 백로그 근거.
 * - 인코딩 주입: 공공데이터 CSV는 UTF-8/CP949(EUC-KR) 혼재. 기본 UTF-8.
 * - 고유번호 없으면 sha256(name|address) 결정적 대체키 → (source, source_external_id) 멱등 upsert 항상 성립.
 */
@Component
public class StandardCsvParser {

    public record Result(List<PlaceRow> rows, int totalRecords, int skippedNoCoords) {
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
        int total = 0;
        int skipped = 0;

        try (CSVParser parser = format.parse(reader)) {
            Map<String, Integer> headers = parser.getHeaderMap();
            String idCol = findColumn(headers, spec.idAliases());
            String nameCol = requireColumn(headers, spec.nameAliases(), "이름");
            String addrCol = findColumn(headers, spec.addressAliases());
            String latCol = requireColumn(headers, spec.latAliases(), "위도");
            String lngCol = requireColumn(headers, spec.lngAliases(), "경도");

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
                rows.add(new PlaceRow(externalId, name, address, lat, lng));
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
