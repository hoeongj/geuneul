package com.geuneul.domain.ingest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 고유번호 없는 소스(공공데이터 CSV·오픈API 공통)의 결정적 대체 자연키.
 * 같은 (이름|주소)는 재적재 시 같은 키로 수렴한다 — (source, source_external_id) 멱등 upsert가
 * 항상 성립하게 한다(ADR-0002). CSV 경로({@link StandardCsvParser})와 JSON 오픈API 경로
 * ({@code domain.ingest.openapi}, ADR-0006) 양쪽이 공유한다.
 */
public final class IngestIds {

    private IngestIds() {
    }

    public static String fallbackId(String name, String address) {
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
