package com.geuneul.domain.ingest.storeapi;

import java.util.List;

/**
 * 소상공인시장진흥공단 상가(상권)정보 오픈API({@code B553077/api/open/sdsc2/storeListInRadius}) 응답 포맷.
 *
 * <p><b>계약 검증 완료(2026-07-10)</b> — 상가업소정보 활용신청 승인 후 실 호출로 확정했다.
 * 이 API는 도서관 표준 오픈API와 달리 {@code response} 래퍼가 <b>없고</b> {@code header}·{@code body}가
 * 최상위에 온다(실측). 좌표(lon/lat)·페이지네이션(totalCount 등)은 JSON 문자열이 아니라 <b>숫자</b>다.
 * 승인 전(2026-07-09)에는 도서관 응답 형태를 추정해 {@code response} 래퍼·문자열 좌표를 가정했으나
 * 실측으로 정정함(TS-026). 응답에는 columns·stdrYm 등 우리가 안 쓰는 필드도 오지만
 * Jackson 기본(FAIL_ON_UNKNOWN_PROPERTIES=false)이 무시한다.
 */
public record StoreApiResponse(Header header, Body body) {

    public record Header(String resultCode, String resultMsg) {
    }

    public record Body(List<StoreRecord> items, Integer totalCount, Integer numOfRows, Integer pageNo) {
    }
}
