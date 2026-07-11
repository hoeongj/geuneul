# ADR-0022. Web Push 전송 — zerodep(BouncyCastle 없이) + VAPID, 기능 플래그 게이팅

- 상태: 승인(구현 반영, 2026-07-11)
- 관련: ADR-0018(알림 인앱 센터·Web Push를 stretch로 분리), ADR-0020(HEAT_ESCAPE), `domain.notification`,
  `domain.push`(신규), `V16__push_subscriptions.sql`, 프론트 Serwist SW·`NotificationsSection`,
  SPEC.md §3(알림)·§0-2(과설계)·§D(비밀)
- 후속: BACKLOG F2

## 문제(Context)

ADR-0018은 Web Push를 **의도적 stretch**로 미뤘다. 이유 두 가지: (1) iOS는 **설치형 PWA(홈 화면 추가)**에서만
푸시가 되어 실기기 검증이 필요, (2) 대표 Java 라이브러리(`nl.martijndwars:web-push`)가 **BouncyCastle**에
의존해 Spring Boot fat-jar/Docker에서 서명 프로바이더 문제를 일으킬 수 있다(로컬에서 안 드러나고 컨테이너에서만
터지는 TS-009류 함정). 이제 사용자가 설치형 PWA(Safari)를 준비해 (1)이 풀렸다.

## 결정(Decision)

### 1) 라이브러리 = `zerodep-web-push-java`(BouncyCastle 없음)

`com.zerodeplibs:zerodep-web-push-java:2.1.5`를 쓴다. **JDK 내장 crypto(SunEC/SunJCE)만** 사용해
BouncyCastle 의존이 없다 → ADR-0018이 지적한 fat-jar 서명 프로바이더 리스크를 **원천 회피**한다(로컬에서
Docker를 못 돌려 검증할 수 없는 리스크였기에, 리스크 자체를 없애는 선택이 방어적). JDK11+, Boot 4/Java 21 호환.

- **키 계약검증(TS-026)**: 우리가 생성한 VAPID PEM(OpenSSL: PKCS8 개인키 + uncompressed 공개키)을
  `PrivateKeySources.ofPEMText`/`PublicKeySources.ofPEMText`로 로드해 `extractPublicKeyInUncompressedFormAsString()`이
  87자 base64url 공개키를 내는 것까지 실행 검증했다. 실제 암호화 전송은 실기기 구독이 있어야라 코드 경로만 굳혔다.

### 2) 전송은 기능 플래그(`push.enabled`)로 게이팅 — 회귀 0

`WebPushConfig`가 `push.enabled=true`일 때만 `VAPIDKeyPair` 빈을 만든다. `PushService`는
`ObjectProvider<VAPIDKeyPair>`로 주입받아 **빈이 없으면 `sendToUser`가 조용히 no-op**이다.

- **구독 저장은 플래그와 무관하게 동작** — 미리 구독을 받아두고 나중에 켜도 된다.
- 로컬/CI 기본은 off → VAPID 빈 없음 → 컨텍스트 정상 기동, 인앱 알림(B1) 경로 무변경. **검증 안 된 크립토를
  프로덕션에 blind ship하지 않는다**는 규율(§0-2·TS-026)을 지키면서, 켜는 순간 살아나게 배선만 완료.
- 켜기(활성)인데 키가 비면 컨텍스트가 **즉시 실패**(빠른 실패) — 조용히 안 보내는 오배포를 막는다.

### 3) 채널 분리 = 인앱 센터(B1) ∥ OS 배너(F2), 발송 지점 재사용

Web Push는 인앱 알림 센터(notification_deliveries, ADR-0018)와 **병렬 채널**이다. 발송 이력은 그대로 쌓고,
새 delivery가 생기는 지점에서 push도 쏜다. HEAT_ESCAPE(ADR-0020)는 유저 단위 평가라 `insertHeatEscape>0`
직후 `pushService.sendToUser`를 **additive**로 호출(실패 격리 → 인앱 경로 안 죽음). 급증(onSurge)은 벌크
매칭 INSERT라 대상 user_id 회수가 지저분해 이번 스코프에서 제외(follow-up — RETURNING 또는 pushed 플래그 필요).

- 구독 소멸(push 서비스 404/410)이면 해당 endpoint를 정리(stale 구독 누적 방지).
- `POST /push/test`(내 기기로 배너 1회)로 **end-to-end를 한 탭에 검증** — 실기기 확인의 진입점.

### 4) 프론트 = Serwist SW `push`/`notificationclick` + 구독 UI

기존 Serwist 서비스워커(`app/sw.ts`)에 `push`(배너 표시)·`notificationclick`(앱 포커스/열기) 핸들러를 얹는다.
`NotificationsSection`에 "기기 푸시 알림" 행 — 구독(권한요청→pushManager.subscribe(applicationServerKey)→
`/push/subscribe`) + "테스트" 버튼. 공개키는 `/push/public-key`에서 받아 프론트 env 불필요. **서버 비활성이면
행 자체를 숨긴다**(enabled=false).

## 대안(Alternatives)

- **`nl.martijndwars:web-push`(BouncyCastle)**: 기존 base64url 키를 바로 먹지만 fat-jar 서명 리스크가
  Docker에서만 드러나고 로컬 검증 불가 → 회피. zerodep이 방어적.
- **주기 서버 발송(전 유저 스캔)**: HEAT_ESCAPE 온디맨드(ADR-0020)와 대칭으로 유지 — 발송 지점 재사용이 단순.
- **급증 push까지 이번에**: 벌크 INSERT 대상 회수 복잡·중복발송 위험 → follow-up.

## 결과(Consequences)

- (+) BouncyCastle 없이 Web Push 배선 완료. 플래그 off 기본이라 회귀 0, 켜면 즉시 동작.
- (+) 인앱 센터와 독립 채널 — 한쪽 실패가 다른 쪽을 안 죽인다.
- (−) 활성화하려면 VAPID 키(개인키=비밀)를 SSM/env로 주입 + `push.enabled=true` 태스크데프 rev 필요(§D).
- (−) 실제 배너 수신은 **설치형 PWA 실기기**에서만 최종 검증 가능(iOS 조건) — `/push/test`로 사용자가 확인.
- (−) 급증 push는 follow-up(HEAT_ESCAPE만 실배선).
