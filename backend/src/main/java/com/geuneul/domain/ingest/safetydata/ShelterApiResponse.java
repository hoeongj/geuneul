package com.geuneul.domain.ingest.safetydata;

import java.util.List;

/**
 * safetydata.go.kr(재난안전데이터공유플랫폼) V2 API 응답 포맷.
 *
 * <p><b>계약 검증 완료(2026-07-10, TS-027)</b> — data.go.kr 표준 오픈API와 달리 {@code response} 래퍼가
 * 없고 {@code header}·{@code body}·페이지네이션(totalCount/pageNo/numOfRows)이 <b>최상위</b>에 오며,
 * {@code body}는 {@code items} 없이 <b>레코드 배열 그 자체</b>다(실측). header 키는 camelCase
 * (resultCode/resultMsg)라 별도 매핑이 필요 없다. 미매핑 필드(errorMsg 등)는 Jackson 기본이 무시한다.
 */
public record ShelterApiResponse(Header header, List<ShelterRecord> body, Integer totalCount,
                                 Integer pageNo, Integer numOfRows) {

    public record Header(String resultCode, String resultMsg) {
    }
}
