package com.geuneul.domain.ingest.safetydata;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 행정안전부_무더위쉼터 오픈API(safetydata.go.kr, {@code DSSP-IF-10942}) 응답 레코드(필요 필드만).
 *
 * <p><b>계약 검증 완료(2026-07-10, TS-027)</b> — safetydata 전용 서비스키로 실호출해 확정했다.
 * JSON 키가 대문자 스네이크(RSTR_NM 등)라 {@link JsonProperty}로 매핑한다. 좌표는 LA/LO(WGS84 숫자,
 * 실측 결측 0/1000)로 오고 TM 좌표 XCORD/YCORD는 null이라 무시한다. 냉방기 보유수(COLR_HOLD_ARCNDTN)로
 * air_conditioned feature를 조건부 백필한다(도서관 seatCo>0 → study_ok 패턴과 동형).
 */
public record ShelterRecord(
        @JsonProperty("RSTR_FCLTY_NO") Long rstrFcltyNo,        // 쉼터시설번호(고유) — external_id
        @JsonProperty("RSTR_NM") String rstrNm,                 // 쉼터명칭
        @JsonProperty("RN_DTL_ADRES") String rnDtlAdres,        // 도로명 상세주소
        @JsonProperty("DTL_ADRES") String dtlAdres,             // 지번 상세주소(도로명 결측 시 폴백)
        @JsonProperty("LA") Double la,                          // 위도(WGS84, JSON 숫자)
        @JsonProperty("LO") Double lo,                          // 경도(WGS84, JSON 숫자)
        @JsonProperty("COLR_HOLD_ARCNDTN") Integer colrHoldArcndtn // 냉방기 보유수 — >0이면 air_conditioned 백필
) {
}
