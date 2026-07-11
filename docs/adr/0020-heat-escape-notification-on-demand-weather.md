# ADR-0020. HEAT_ESCAPE 알림 — 온디맨드(pull) 날씨 평가, 새 스케줄러 없이

- 상태: 승인(구현 반영, 2026-07-10)
- 관련: `domain.notification`(B1, ADR-0018 확장), `domain.weather`(`HeatComfort`·`WeatherService`, ADR-0009),
  `domain.place`(kNN `findNearest`, 간판 공간쿼리), `notification_deliveries`(V15, dedup 재사용),
  SPEC.md §3(알림)·§6(공포 조장 금지)·§0-2(범위/과설계 금지)
- 후속: BACKLOG F5

## 문제(Context)

B1 알림(ADR-0018)에서 `NotificationRuleType.HEAT_ESCAPE`는 **타입만 정의**하고 평가는 follow-up으로 남겼다.
목표: **폭염일 때 규칙 중심 근처 무더위쉼터로 피난을 권유**한다. 급증(SURGE_NEARBY/BOOKMARK_SURGE)은 제보
LISTEN/NOTIFY 이벤트로 트리거되지만, 폭염은 **이벤트가 아니라 상태(날씨)**라 트리거 소스가 다르다.

두 질문: (1) **언제 평가하나**(주기 스케줄 vs 온디맨드), (2) **폭염을 무엇으로 판정하나**.

## 결정(Decision)

### 1) 평가 시점 = 온디맨드(pull), 알림 센터 열 때. 새 스케줄러 없음

HEAT_ESCAPE 규칙은 **요청 유저가 알림 센터(`GET /notifications`)를 열 때** 그 유저의 활성 규칙만 평가한다.
새 스케줄러/EventBridge 트리거를 추가하지 않는다.

- **근거(결정적)**: Web Push(F2)는 아직 미배선이라 **발송 이력은 앱을 열어야만 보인다**. 서버가 주기적으로
  미리 생성해도 유저가 앱을 열기 전까지 아무도 못 본다 → 온디맨드 생성과 **유저가 보는 결과가 동일**한데
  주기 스케줄은 인프라(EventBridge→RunTask 경로·전 유저 스캔)만 늘린다. §0-2(과설계 금지)에 정면으로 어긋난다.
- **2026 관점**: 푸시가 붙는 시점(F2)엔 트리거를 서버 주기평가로 승격하면 된다 — 온디맨드는 그때까지의 올바른
  right-sized 선택이고, 평가 로직(`evaluateHeatEscape`)은 그대로 재사용된다(트리거만 교체).
- 급증(onSurge, push 아님)과 대칭: 급증도 "내가 없을 때 쌓인 것"을 폴링으로 본다(ADR-0018). HEAT_ESCAPE도
  같은 폴링 모델에 얹어 **읽기 진입점에서 upsert**한다. dedup_key UNIQUE + cooldown 버킷이 중복을 막으므로
  읽기마다 평가해도 안전하다(멱등).

### 2) 폭염 판정 = 체감온도 ≥ 33℃(기상청 폭염주의보), `HeatComfort` 재사용

`HeatComfort`(ADR-0009)가 이미 기온·습도로 **체감온도**를 계산하고 폭염특보 임계값(33/35/38℃)에 앵커돼 있다.
`isHeatAdvisory(Weather)` = 체감온도 ≥ **33℃**(폭염주의보 발효선)를 추가해 그대로 재사용한다 — 새 임계값을
추측하지 않고 기존 공식 기준선을 쓴다(§0-B 방어 가능성). 날씨 결측(키 미설정·네트워크·관측 없음)이면 규칙을
조용히 skip한다(graceful degradation — 알림 안 함이 오탐보다 안전).

### 3) 쉼터 추천 = 간판 kNN 재사용, §6 권유형 문구

규칙 중심(center_lat/lng) 기준 `PlaceRepository.findNearest(lat, lng, 'COOLING_SHELTER', 1)`(index-assisted
KNN `<->`)로 가장 가까운 무더위쉼터 1곳을 찾는다. 쉼터가 없으면 skip(대상 없는 알림 안 만듦).

문구는 **§6 공포 조장 금지**: "위험!"이 아니라 권유형 —
`"지금 체감 34℃. 가까운 무더위쉼터 '○○'(120m)에서 잠깐 쉬어가세요."`

### 4) cooldown = 규칙당 3시간 1회

dedup_key = `heat:<ruleId>:<3h 버킷>`. 날씨 실황은 시간당 갱신이라 매시 알림은 과하다 → **3시간 버킷**으로
규칙당 최대 3시간에 1회. `notification_deliveries.dedup_key` UNIQUE + `ON CONFLICT DO NOTHING`(급증과 동일
메커니즘)이 멀티 인스턴스 중복·반복 열람을 함께 막는다.

## 대안(Alternatives)

- **EventBridge 주기 스케줄로 전 유저 HEAT_ESCAPE 스캔**: 정석이지만 푸시 없는 현시점엔 유저가 보는 결과가
  동일한데 인프라만 늘어난다(§0-2). F2(푸시) 배선 시 승격 대상으로 남긴다.
- **survival_score/추천 응답에 폭염 배너 인라인**: 알림 규칙(개인화·이력)과 성격이 달라 알림 도메인 밖.
  추천 탭 "비 피할 곳/폭염 피난"은 이미 시나리오 추천으로 존재 — 중복.
- **더 촘촘한(시간당) cooldown**: 나깅 유발. 3시간이 폭염 지속 상황에서 적절.

## 결과(Consequences)

- (+) 새 인프라 0. 기존 날씨·kNN·notification_deliveries·dedup을 조합만 함(간판 재사용).
- (+) 읽기 진입점 멱등 upsert라 멀티 인스턴스·반복 열람에 안전.
- (−) 앱을 안 열면 알림이 안 생김 — 단 푸시 미배선(F2) 하에선 어차피 못 보므로 실질 손실 없음. F2 배선 시
  트리거를 주기평가로 승격(로직 재사용).
- (−) `GET /notifications`가 쓰기(upsert)를 겸함 — dedup 멱등이라 안전하나, 평가는 별도 트랜잭션으로 분리해
  읽기 목록 트랜잭션을 오염시키지 않는다.
