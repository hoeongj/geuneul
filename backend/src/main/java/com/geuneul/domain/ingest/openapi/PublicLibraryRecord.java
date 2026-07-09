package com.geuneul.domain.ingest.openapi;

/**
 * 전국도서관표준데이터 오픈API(문화체육관광부, {@code tn_pubr_public_lbrry_api}) 응답 레코드(필요 필드만).
 * 2026-07-09 실측 확인: {@code https://api.data.go.kr/openapi/tn_pubr_public_lbrry_api} 가 전국
 * 3,555건을 페이지네이션으로 제공(경기도 한정이 아님 — HANDOFF의 이전 추정을 실측으로 정정, WORKLOG 기록).
 * 고유 코드 필드가 없어 (이름|주소) 해시를 자연키로 쓴다({@link com.geuneul.domain.ingest.IngestIds}).
 * seatCo(열람좌석수)는 실측상 문자열("879" 등, 결측 시 빈 문자열)로 온다.
 * 응답에는 이 레코드에 없는 필드가 다수 있지만(운영시간·홈페이지 등), Jackson 기본 설정이 미매핑
 * 필드를 관대하게 무시한다(KakaoAddressResponse와 동일 패턴, TS-004 검증 완료).
 */
public record PublicLibraryRecord(
        String lbrryNm,     // 도서관명
        String rdnmadr,     // 도로명주소
        String latitude,    // WGS84 위도(문자열)
        String longitude,   // WGS84 경도(문자열)
        String seatCo       // 열람좌석수(문자열, 결측 시 빈 값) — study_ok/quiet 조건부 백필 기준(ADR-0006)
) {
}
