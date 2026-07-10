// 실시간 제보 급증 알림(백엔드 ADR-0016, GET /alerts/surge · /alerts/stream)의 응답 계약.
// 표현 규율(§6): 백엔드가 이미 공포 조장 없는 중립 message를 만들어 내려준다("위험!" 금지).
export interface SurgeInfo {
  placeId: number;
  name: string;
  lat: number;
  lng: number;
  reportCount: number;
  /** 가장 많이 올라온 제보 타입(FLOOD·BUG·COOL 등). 배지 아이콘/색 힌트로만 쓴다. */
  topType: string;
  /** 중립 안내 문구(백엔드 §6 순화 완료). 그대로 표시한다. */
  message: string;
}
