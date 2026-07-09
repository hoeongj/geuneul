package com.geuneul.domain.ingest.storeapi;

/**
 * 소상공인시장진흥공단 상가(상권)정보 오픈API({@code B553077/api/open/sdsc2}) 응답 레코드(필요 필드만).
 *
 * ⚠️ <b>계약 미검증</b>(ADR-0006) — 이 API는 별도 "활용신청" 승인이 필요해 2026-07-09 기준 서비스키가
 * 아직 403(미승인)이라 실 응답으로 필드명을 확증하지 못했다(도서관 API는 실측 완료, 이쪽은 공식
 * 문서·서드파티 가이드 리서치 기반 — bizesId/indsSclsNm/rdnmAdr/lon/lat, data.go.kr 15083033/15012005,
 * data.sbiz.or.kr 매뉴얼). 승인 후 반드시 실 호출로 필드명·null 처리를 재검증할 것.
 */
public record StoreRecord(
        String bizesId,     // 상가업소번호(고유) — 있으면 그대로 external_id로 쓴다
        String bizesNm,     // 상호명
        String indsSclsNm,  // 상권업종소분류명 — 카페/스터디카페 판별(StoreCategoryMapper)
        String indsSclsCd,  // 상권업종소분류코드 — 15067631 매핑표 실측 확정 전까지는 이름 매칭을 우선한다
        String rdnmAdr,     // 도로명주소
        String lnoAdr,      // 지번주소(도로명 결측 시 폴백)
        String lon,         // 경도(WGS84 문자열)
        String lat          // 위도(WGS84 문자열)
) {
}
