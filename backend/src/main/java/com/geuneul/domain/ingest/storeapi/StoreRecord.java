package com.geuneul.domain.ingest.storeapi;

/**
 * 소상공인시장진흥공단 상가(상권)정보 오픈API({@code B553077/api/open/sdsc2}) 응답 레코드(필요 필드만).
 *
 * <p><b>계약 검증 완료(2026-07-10)</b> — 활용신청 승인 후 실 호출로 필드명·타입을 확정했다(TS-026).
 * 좌표는 JSON <b>숫자</b>(lon/lat)라 {@code Double}로 받는다(승인 전 추정은 문자열이었다). 업종은
 * 소분류코드(indsSclsCd)로 확정 분류한다 — 카페=I21201, 독서실/스터디카페=R10202({@link StoreCategoryMapper}).
 * "업소명없음" 같은 플레이스홀더 상호명도 실제로 존재하나 빈 문자열은 아니라 그대로 적재된다.
 */
public record StoreRecord(
        String bizesId,     // 상가업소번호(고유) — 있으면 그대로 external_id로 쓴다
        String bizesNm,     // 상호명
        String indsSclsNm,  // 상권업종소분류명(예: "카페", "독서실/스터디 카페") — 표시·로깅용
        String indsSclsCd,  // 상권업종소분류코드 — 카테고리 확정 분류의 근거(I21201/R10202)
        String rdnmAdr,     // 도로명주소
        String lnoAdr,      // 지번주소(도로명 결측 시 폴백)
        Double lon,         // 경도(WGS84, JSON 숫자)
        Double lat          // 위도(WGS84, JSON 숫자)
) {
}
