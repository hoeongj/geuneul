# ADR-0016. 실시간 제보 급증 알림 — Postgres LISTEN/NOTIFY → SSE(간판 P4 "실시간 이벤트")

- 상태: 승인(구현 반영, 2026-07-10)
- 관련: `domain/alert/*`(신규), `V9__report_surge_notify_trigger.sql`(신규), ADR-0007(place_report_signals 시공간 신호),
  ADR-0013(ECS Service Auto Scaling — min1/**max3**), CLAUDE.md §7("Redis Streams / Postgres LISTEN·NOTIFY로.
  Kafka 등 과설계 금지"), 로드맵 P4("실시간 이벤트(제보 급증 알림)")

## 문제(Context)

로드맵 P4 간판의 마지막 미완 조각: "실시간 이벤트(제보 급증 알림)" — 특정 장소에서 **단시간에 제보가 몰리면**
(예: 갑작스런 침수·러브버그 대발생·화장실 고장) 그 신호를 지도/구독자에게 **즉시** 띄운다. 두 개의 독립된 질문이 있다.

1. **급증을 무엇으로 판정하나(detection)** — "최근 N분 안에 유효 제보 ≥ K건"을 어느 레이어에서 계산하나.
   간판 규율(CLAUDE.md §5: "시공간 랭킹은 DB 레이어에서")과 정합해야 한다.
2. **급증을 어떻게 실시간으로 전파하나(transport)** — 감지된 순간 브라우저까지 밀어 넣는 경로. 폴링/롱폴/SSE/WebSocket과
   그 뒤의 인스턴스 간 팬아웃(Kafka/Redis Streams/LISTEN·NOTIFY) 중 무엇을 쓰나.

## 결정(Decision)

### 1) 감지 = place_report_signals와 같은 정신모델의 시공간 SQL(간판)

급증 판정은 **DB에서** 한다 — `reports`를 시간창(`created_at >= now() - :window`)·유효성(`expires_at > now()`)으로
필터해 장소별 count를 낸다. bounds 뷰포트 급증 목록은 여기에 `places`를 공간조인(`geom && ST_MakeEnvelope`,
V2 GiST 경로)한다. 앱 전체스캔이 아니라 인덱스(`idx_reports_place_created`·GiST) 경로를 타는 것이 CLAUDE.md
§0.4와 정합한다. 임계값(window=10분, minReports=3)은 설정(`geuneul.realtime.*`)으로 뺐다 — 시딩 밀도에 따라
현장에서 조정(P5).

survival_score의 `place_report_signals` 뷰(ADR-0007)를 재사용하지 않고 별도 count 쿼리를 쓴 이유: 그 뷰는
**최근성·신뢰도 가중 집계**(freshness/comfort/risk)라 "얼마나 좋은가"를 재지만, 급증은 **가중 없는 순수 건수 속도**
("얼마나 몰리나")를 본다 — 서로 다른 질문이라 뷰를 오염시키지 않고 분리한다.

### 2) 전파(인스턴스 팬아웃) = Postgres LISTEN/NOTIFY (Kafka·Redis Streams 아님)

`reports`에 `AFTER INSERT` 트리거를 걸어 `pg_notify('geuneul_report_surge', place_id)`를 쏜다. 각 앱 인스턴스는
전용 커넥션으로 `LISTEN geuneul_report_surge` 하다가 알림을 받으면 그 장소의 급증 여부를 (1)의 SQL로 재확인하고,
급증이면 자기 인스턴스에 붙은 SSE 구독자에게 밀어 넣는다.

**왜 LISTEN/NOTIFY인가(핵심 근거)** — 이 서비스는 이미 **ECS Service Auto Scaling(min1/max3, ADR-0013)** 이 붙어
있어 부하 시 인스턴스가 최대 3대까지 뜬다. 제보 insert를 처리한 인스턴스와, SSE로 구독 중인 브라우저가 붙은
인스턴스가 **다를 수 있다**. 인프로세스 이벤트(`ApplicationEventPublisher`)만 쓰면 다른 인스턴스의 구독자에게
전파되지 않는다. LISTEN/NOTIFY는 **어느 인스턴스가 insert하든 전 인스턴스가 알림을 받으므로** 이 팬아웃을
공짜로 해결한다 — 이미 있는 RDS 하나로, 새 인프라 0.

**왜 Kafka/Redis Streams가 아닌가** — CLAUDE.md §7이 명시적으로 "Redis Streams / Postgres LISTEN·NOTIFY로.
Kafka 등 과설계 금지 — 필요 입증 후에만". Kafka는 브로커 운영 부담이 저빈도 알림에 과하다. Redis Streams도
후보였으나, ① Redis는 현재 `CacheErrorHandler`로 **지워도 서비스가 도는 선택적 캐시**(HANDOFF·ADR-0009)라
알림을 Redis에 결합시키면 그 "선택적" 성질이 깨지고, ② 소스오브트루스인 Postgres에 트리거로 거는 편이
"제보가 커밋되는 바로 그 트랜잭션"과 원자적으로 묶여 유실 창이 없다(트리거는 insert와 같은 트랜잭션에서 큐잉,
커밋 시 전달). LISTEN/NOTIFY의 알려진 한계(구독 중이 아닌 인스턴스는 놓침, 페이로드 8000바이트)는 우리 용례에
무해하다 — 놓친 급증은 다음 제보나 폴백 폴링(`GET /alerts/surge`)이 복구하고, 페이로드는 place_id 하나다.

### 3) 브라우저 전파 = SSE(Server-Sent Events), WebSocket·폴링 아님

급증 알림은 **서버→클라이언트 단방향**이다(구독자가 서버로 보낼 게 없다). SSE는 이 단방향에 정확히 맞고, 평범한
HTTP라 BFF 프록시(ADR-0004)·CloudFront(ADR-0015)를 그대로 통과하며 브라우저가 자동 재연결한다. WebSocket은
양방향 핸드셰이크·별도 프로토콜이 필요해 단방향 알림엔 과설계다. 순수 폴링은 실시간성이 떨어지지만, `GET
/alerts/surge?bounds=`를 **폴백 겸 초기 스냅샷**으로 함께 제공한다(SSE 미지원 환경·재연결 공백 보완).

### 4) 안전장치 — 실시간 리스너는 기능 플래그로 격리, 실패해도 앱은 죽지 않는다

`geuneul.realtime.enabled`(기본 true)로 LISTEN 백그라운드 리스너를 켠다. 리스너 커넥션이 끊기면 백오프 후
재연결하고, 리스너가 아예 못 떠도 **핵심 제보/조회 경로는 무영향**이다(SSE·푸시는 부가 기능, 폴백 폴링이 있음).
테스트·로컬에서 플래그로 끌 수 있어 CI가 백그라운드 스레드에 의존하지 않는다(감지 SQL·트리거는 실 PostGIS로 검증).

## 검토한 대안(Alternatives)

| 대안 | 기각 이유 |
|---|---|
| Kafka 토픽으로 제보 이벤트 팬아웃 | 브로커 운영 부담이 저빈도 알림에 과설계 — CLAUDE.md §7이 명시적으로 금지("필요 입증 후에만") |
| Redis Streams | Redis가 "지워도 되는 선택적 캐시"(ADR-0009)라 결합 시 그 성질이 깨짐. 소스오브트루스(PG)에 트리거로 거는 편이 제보 트랜잭션과 원자적 |
| 인프로세스 ApplicationEvent만 | min1/**max3** 오토스케일링(ADR-0013)에서 다른 인스턴스 구독자에게 전파 안 됨 — 실시간의 의미가 깨짐 |
| WebSocket | 급증 알림은 단방향(서버→클라)이라 양방향 프로토콜은 과설계. SSE가 HTTP라 BFF·CloudFront 통과·자동 재연결 |
| 순수 폴링만 | 실시간성 부족. 단, SSE의 폴백 겸 초기 스냅샷으로 `GET /alerts/surge`를 함께 둠 |
| 급증 판정을 앱 메모리 카운터로 | 인스턴스별로 카운터가 갈라져 부정확. DB count가 인스턴스 무관하게 정확(간판=DB 시공간, §5) |

## 결과(Consequences)

- `GET /alerts/surge?bounds=` (폴백/스냅샷) + `GET /alerts/stream` (SSE 실시간)이 추가된다. 둘 다 permitAll
  (공개 커먼스 — 알림은 로그인 불필요).
- 새 인프라 0 — 이미 있는 RDS의 LISTEN/NOTIFY만 쓴다. 트리거는 Flyway V9로 스키마에 포함되므로 재현 가능.
- 표현 규율(CLAUDE.md §6): 급증 알림 문구는 공포 조장 금지 — "위험!"이 아니라 "최근 제보가 몰리고 있어요"
  톤으로 프론트(⑧)에서 렌더한다. 백엔드는 사실(place_id·count·대표 타입)만 싣는다.
- LISTEN/NOTIFY는 "구독 중일 때만" 받는다 — 리스너 재연결 공백에 발생한 급증은 놓칠 수 있으나, 다음 제보나
  폴백 폴링이 복구하므로 알림 유실이 데이터 유실은 아니다(제보 자체는 트랜잭션으로 안전 저장).

## 근거(References)

- PostgreSQL 공식 문서 §NOTIFY/LISTEN — 트리거에서 `pg_notify()`, 페이로드 8000바이트 제한, "구독 중 세션만 수신".
- CLAUDE.md §7(실시간: Redis Streams / Postgres LISTEN·NOTIFY, Kafka 과설계 금지), §5(시공간 랭킹은 DB 레이어).
- ADR-0013(ECS Auto Scaling min1/max3 — 다중 인스턴스 팬아웃이 LISTEN/NOTIFY를 정당화), ADR-0007(시공간 신호 뷰),
  ADR-0009(Redis는 지워도 되는 선택적 캐시), ADR-0004(BFF 프록시 — SSE가 HTTP라 통과).
- 2026-07 웹 확인: SSE가 단방향 서버푸시의 표준(WebSocket 대비 경량), 브라우저 `EventSource` 자동 재연결 내장.
