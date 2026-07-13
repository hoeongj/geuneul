# ADR-0018. 알림(Notifications) — 인앱 센터 MVP + 급증 이벤트 재사용, Web Push는 stretch

> **후속 상태(2026-07):** 당시 stretch로 분리했던 Web Push는 [ADR-0022](./0022-web-push-zerodep-vapid-feature-gated.md)에서 기능 플래그 방식으로 구현됐다. 아래 본문은 인앱 센터 우선 결정을 내린 당시의 근거를 보존한다.

- 상태: 승인(구현 반영, 2026-07-10)
- 관련: `V15__notifications.sql`(신규), `domain.notification`(신규), `domain.alert`(급증 LISTEN/NOTIFY 재사용,
  ADR-0016), `bookmarks`(A7·V14), `domain.weather`(폭염 트리거 — stretch), SPEC.md §3(알림)·§6(공포 조장 금지)·§9

## 문제(Context)

로드맵 심화 B1: ① 내 주변 침수/벌레 **급증 알림**, ② **관심 장소 상태 변화**(A7 bookmarks), ③ **폭염 피난 추천**.
두 질문: (1) **전달 방식**(어떻게 유저에게 닿나), (2) **감지·평가**(규칙을 무엇으로 트리거하나).

## 결정(Decision)

### 1) 전달 = 인앱 알림 센터(폴링) MVP, Web Push(VAPID)는 stretch

**인앱 센터 + 폴링**을 MVP로 한다. 규칙이 충족되면 `notification_deliveries`에 발송 이력을 쌓고, 프론트가
`GET /notifications`로 조회한다. Web Push(VAPID)는 **의도적 후순위**다.

- **2026 트렌드 확인(웹 검색)**: iOS Web Push는 iOS 16.4+에서 되지만 **"홈 화면에 추가한 PWA"에서만** 동작하고
  Safari 탭에서는 안 된다. EU는 DMA로 iOS 17.4부터 PWA가 Safari 탭으로 열려 푸시 불가. 즉 **도달률이 설치 PWA로
  제한**된다(Android/네이티브 대비 좁음). → 설치를 강제하지 않는 우리 공개 커먼스에는 인앱 센터가 먼저다.
- Web Push는 VAPID 키(비밀·`web-push`로 생성 가능, `.local`/SSM, §D)와 service worker `push` 핸들러가 필요해
  **사용자 액션·비밀 관리**가 붙는다 → stretch로 분리(BACKLOG 권장과 일치). 인앱 센터가 있으면 푸시는 additive.
- 기존 SSE(`/alerts/stream`, ADR-0016)는 지도 급증 배지(A4)에 이미 쓴다. 알림 센터는 "내가 없을 때 쌓인 것"을
  보는 용도라 **영속 이력(폴링)**이 맞다(SSE는 실시간 순간, 발송 이력은 DB).

### 2) 감지·평가 = 급증 LISTEN/NOTIFY 이벤트 재사용(간판 인프라 그대로)

규칙 평가는 **새 스케줄러를 얹지 않고**(§0-2 과설계 금지) 기존 급증 이벤트에 훅한다. `ReportNotificationListener`
(ADR-0016, 전 인스턴스가 받는 Postgres LISTEN/NOTIFY)가 급증을 확인한 그 지점에서 `NotificationService.onSurge`를
호출해 활성 규칙을 매칭·발송한다.

- **SURGE_NEARBY**: 규칙의 중심(center)에서 `radius_m` 안에 급증 장소가 들면 발송(ST_DWithin, 간판 공간쿼리 재사용).
- **BOOKMARK_SURGE**("관심 장소 상태 변화"): 급증 장소를 **북마크한 유저**에게 발송(A7 bookmarks 조인).
- **HEAT_ESCAPE**(폭염 피난): 날씨 트리거라 급증 이벤트와 무관 → **이번 MVP 범위 밖(follow-up)**. 규칙 타입은
  정의해 두되 평가는 후속(EventBridge 주기 트리거나 조회 시 온디맨드). 새 스케줄 인프라를 알림 하나로 당기지 않는다.

### 3) 중복 없이 1회 발송 = `dedup_key` UNIQUE(멀티 인스턴스 + cooldown 동시 해결)

수용 기준 "조건 충족 시 **1회**(중복 없이)"를 위해 `notification_deliveries.dedup_key`에 UNIQUE를 걸고
`ON CONFLICT DO NOTHING`으로 삽입한다. key = `surge:{ruleId}:{placeId}:{epoch/COOLDOWN}`(시간 버킷).

- **멀티 인스턴스**: ECS min1/max3(ADR-0013)에서 **모든 인스턴스의 리스너가 같은 NOTIFY를 받아** 각자 평가·삽입을
  시도하지만, 같은 dedup_key라 하나만 이기고 나머지는 DO NOTHING → 정확히 1건. (A4 SSE는 인스턴스별 브로드캐스트라
  중복이 문제 안 됐지만, 발송 이력은 영속이라 dedup이 필수.)
- **cooldown**: 같은 규칙×장소가 COOLDOWN(10분) 창 안에 반복 급증해도 같은 버킷 key → 재발송 안 됨(반복 알림 폭주 방지).

### 4) 표현(§6) — 규칙·발송 문구는 공포 조장 금지

발송 title/body는 급증 `SurgeInfo.message`(백엔드가 이미 순화, ADR-0016)를 재사용한다("위험!" 금지 →
"최근 침수 제보가 몰리고 있어요 · 우회 권장"). 위치 프라이버시: 규칙의 중심좌표는 유저가 명시 설정한 값만 저장(최소).

## 대안(Alternatives)

- **Web Push를 MVP로**: iOS 도달률 제한 + VAPID 비밀·설치 강제 → 공개 커먼스에 부적합. stretch로. 기각.
- **condition_json(ERD 원안) 그대로**: 규칙 매칭이 **공간쿼리**(ST_DWithin)라 JSONB에서 lat/lng/radius를 뽑아
  쓰는 건 취약·비효율. **구조화 컬럼**(center_lat/lng, radius_m)이 타입안전·인덱스·테스트에 유리 → ERD의
  condition_json 대신 구조화 컬럼 채택(ERD는 초안, 공간 매칭 요구가 우선). 문서화된 의도적 이탈.
- **전용 스케줄러로 주기 평가**: 급증은 이벤트-드리븐이 자연스럽고(이미 LISTEN/NOTIFY 있음) 새 스케줄은 과설계. 기각.
- **인메모리 이벤트로 평가**: 인스턴스 로컬이라 다른 인스턴스의 제보를 놓친다 → DB 브로드캐스트(LISTEN/NOTIFY) 재사용.

## 결과(Consequences)

- **좋음**: 새 인프라 0(급증 LISTEN/NOTIFY·bookmarks·공간쿼리 재사용). 멀티 인스턴스에서 정확히 1회. Web Push는
  나중에 이 발송 이력 위에 additive(service worker + VAPID만). §6 순화 유지.
- **비용**: 리스너 handle()에 규칙 평가 쿼리 1~2개 추가(급증은 저빈도라 무해). 알림 센터는 폴링(가벼움).
- **한계(follow-up)**: HEAT_ESCAPE 평가 미구현(타입만 정의). Web Push 미구현(stretch). 폴링 간격은 프론트가 조정.
