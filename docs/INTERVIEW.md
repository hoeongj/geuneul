# 면접 STAR — 그늘(Geuneul)

> 27개 ADR · 33개 트러블슈팅(`TROUBLESHOOTING.md`) 중, **면접에서 30초에 꺼낼 수 있는** 9개를 STAR(Situation·Task·Action·Result)로 정리했다.
> 그늘 **고유 축(지리공간·멱등 ETL·실시간·동시성·검증 회복력)** 위주 — 분산/AI/K8s 축은 다른 프로젝트에서 다룬다.
> 각 항목의 원 서사는 괄호 안 `TS-0xx`/`ADR-00xx`에 있다.

---

## 1. "성공 종료했는데 결과가 0" — 지오코딩 5.4만 건 전량 실패 (TS-004)

> **한 줄:** exitCode 0인데 geocoded 0 — 조용한 실패를 로그 패턴으로 근본까지 추적, "모킹이 실계약을 가린다"를 실제 사고로 배움.

- **S(상황):** 프로덕션 지오코딩 배치가 정상 종료(exitCode 0)했는데 결과가 `geocoded=0, failed=54,090` — 화장실 0건 적재. 로컬 curl 단건은 HTTP 200 정상이라 더 혼란.
- **T(과제):** 예외로 죽지도 않고 CI는 전부 green인데 프로덕션만 100% 실패하는 원인을 찾아 재적재.
- **A(행동):** "exitCode 0 + 결과 0"은 "매 호출이 조용히 empty 반환"임을 가리킨다고 보고 CloudWatch에서 `[geocode]` 로그를 뽑으니 전 건 동일 에러 — `Type definition error: JsonNode`. **Spring Boot 4가 Jackson 3(`tools.jackson`)로 이전**됐는데 클라이언트가 응답을 **Jackson 2의 `JsonNode`** 로 역직렬화하려다 매 호출 파싱 예외 → catch에서 empty. 근본 원인은 **테스트 사각지대**: IT가 `@Primary` 페이크 지오코더를 주입해 실제 HTTP 역직렬화 경로가 한 번도 안 돌았다. → 응답을 **타입 있는 record**로 바꾸고, **MockRestServiceServer로 실제 카카오 JSON을 파싱하는 단위 테스트 5건**(도로명/지번폴백/0건/4xx/키없음)을 추가해 사각지대를 메웠다.
- **R(결과):** 로컬에서 파싱을 검증(이전엔 페이크가 가려 불가능)하고 재배포·재적재. 멱등 파이프라인이라 실패분이 캐시되지 않아 재실행으로 수렴.
- **학습:** 메이저 프레임워크 이전의 은닉된 파괴(Jackson 2→3)와 **모킹의 함정** — 외부 의존을 페이크로 대체하면 그 의존과의 실제 계약(역직렬화)이 미검증으로 남는다.

## 2. CI(실 Postgres)에서만 터진 500 — 네이티브 프로젝션 timestamptz (TS-016)

> **한 줄:** JDBC가 `TIMESTAMPTZ`를 `Instant`로 주는데 프로젝션 getter를 `OffsetDateTime`으로 선언 — 목 테스트는 통과, 실 드라이버만 터짐.

- **S(상황):** 후기 목록 `GET /places/{id}/reviews`가 로컬 유닛은 green인데 CI(실 PostGIS)에서만 500. 게다가 **목록이 비면 통과, 데이터가 있으면 실패**.
- **T(과제):** 실 드라이버 경로에서만, 그것도 행이 있을 때만 나는 500을 머지 전에 잡기.
- **A(행동):** 스택트레이스 = `Cannot project java.time.Instant to java.time.OffsetDateTime`. 원인은 `reviews JOIN users` **네이티브 쿼리 + 인터페이스 프로젝션**의 getter를 `OffsetDateTime`으로 선언한 것 — pgJDBC는 `TIMESTAMPTZ`를 **`Instant`** 로 반환하고, Spring Data 프로젝션은 getter 호출 시점에 컨버터가 없으면 예외를 던진다(그래서 행이 없으면 getter 미호출 → 통과). → getter를 **`Instant`로 선언**하고 응답 조립에서 `.atOffset(ZoneOffset.UTC)`로 명시 변환(전역 UTC 가정과 일치).
- **R(결과):** CI가 머지 전에 잡아 수정. **"로컬 IT skip 상태에서 CI가 최종 게이트"라는 원칙이 정확히 이 버그를 위해 존재**함을 실증.
- **학습:** JPA 엔티티(Hibernate 변환)와 달리 **네이티브 프로젝션 getter는 JDBC가 실제로 주는 타입과 일치**해야 한다. TS-004·TS-011과 같은 계열(모킹/스킵이 실 계약을 가림).

## 3. 관심 장소 알림 "정확히 1회" 푸시 — 동시성 (ADR-0026, C3)

> **한 줄:** 사전 SELECT + `inserted>0` 가드는 per-user dedup에서 누락·중복 — `INSERT … RETURNING`으로 실제 삽입한 유저만 푸시.

- **S(상황):** 북마크한 장소에 침수 제보 1건이 오면 저장 유저에게 인앱+푸시 알림. 파이프라인(제보 INSERT → `LISTEN/NOTIFY` → 리스너)은 있는데, 초기 설계에 세 가지 동시성 결함이 적대적 리뷰로 드러났다.
- **T(과제):** 멀티 인스턴스·동시 제보에서 **재알림 없이, 안전 제보를 잃지 않고, 정확히 1회** 푸시.
- **A(행동):** (1) **재알림** — dedup 버킷을 "sliding since(now-10분)"가 아니라 **제보 `created_at` 기준**으로 산정 → 같은 제보는 항상 같은 dedup_key. (2) **안전 알림 유실** — NOTIFY가 place_id만 실어 재조회하는데 `LIMIT 1`이면 FLOOD·SLIPPERY가 ms차로 들어올 때 하나를 잃음 → `DISTINCT ON (report_type)`로 타입별 최신 각각. (3) **푸시 누락/중복** — 사전 수신자 SELECT와 INSERT가 별도 스냅샷이고 dedup_key가 per-user라 all-or-nothing이 아님 → **`INSERT … RETURNING user_id`**(EntityManager 커스텀 조각)로 **실제 삽입된 유저에만** 푸시 → 삽입=푸시 집합 일치.
- **R(결과):** 마이그레이션 0(기존 스키마 재사용)으로 실시간 안전 알림 완성. 단위 13건 + IT 3건, CI 게이트 통과.
- **학습:** "존재 확인 후 삽입"은 동시성에서 깨진다 — **삽입의 원자적 결과(RETURNING)** 를 진실의 원천으로 삼아야 정확히 1회가 보장된다.

## 4. "병목은 GiST가 아니라 CPU였다" — k6 부하테스트 (ADR-0012)

> **한 줄:** 측정이 튜닝의 범위를 정한다 — 인덱스부터 만지려다, k6+EXPLAIN으로 진짜 병목이 CPU임을 확인하고 반경 p95 2.68s→~1.4s.

- **S(상황):** 간판인 공간검색(반경/kNN/bounds)의 성능을 증명·개선해야 하는데, "느리면 인덱스 문제"라는 직관으로 바로 인덱스를 만지려던 참.
- **T(과제):** 무엇을 튜닝해야 실제로 빨라지는지를 **추측이 아니라 측정**으로 정하기.
- **A(행동):** `EXPLAIN`으로 반경 `ST_DWithin`·kNN `<->`·bounds가 **GiST 인덱스를 실제로 타는지 실증**(이미 탐). k6로 부하를 걸어보니 p95 병목이 인덱스 스캔이 아니라 **0.5 vCPU CPU 포화**였다. → 인덱스는 이미 최적이라 손대지 않고, 만료 제보 조건에 부분 인덱스(V8)를 추가하고 태스크 CPU를 조정. 재부하로 확인.
- **R(결과):** **반경 검색 p95 2.68s → ~1.4s**, 실패율 0%(kNN p95 213ms·bounds p95 493ms). "GiST를 더 만졌으면 아무 효과 없었을" 것을 측정이 막았다.
- **학습:** 성능 작업의 첫 단계는 코드 수정이 아니라 **병목을 측정으로 국소화**하는 것. EXPLAIN(무엇을 타나) + 부하테스트(어디서 포화되나)가 튜닝 범위를 정한다.

## 5. 레이트리밋 우회 + OOM — 프록시 신뢰경계 (TS-008)

> **한 줄:** ALB는 실 IP를 XFF **최우측**에 append하는데 최좌측을 신뢰 → 위조로 리밋 무력화. 적대적 리뷰가 잡고 BFF 공유시크릿으로 해결.

- **S(상황):** 익명 제보 API 배포 후 다중 에이전트 적대적 리뷰(19 에이전트·5차원)를 돌려 14건 발견 → 반증 검증으로 **확정 2대 결함**.
- **T(과제):** 익명 제보의 유일한 방어선인 레이트리밋이 실제로 방어가 되는지 검증·강화(제보가 survival_score freshness를 굴리므로 스팸 오염 위험).
- **A(행동):** (A) **XFF 최좌측 신뢰 → 우회**: `X-Forwarded-For.split(",")[0]`을 리밋 키로 썼는데 **ALB는 실 IP를 최우측에 append**하므로 최좌측은 클라이언트가 위조 가능 → XFF 회전으로 매 요청 새 키. → `ProxyClientResolver` 도입(신뢰경계 명시): BFF가 공유 시크릿으로 증명하면 BFF 판정 IP 신뢰, 직접 타격이면 append된 **최우측**(위조 불가), 시크릿 미설정이면 기존 호환 → **회귀 0**. (B) **eviction no-op → OOM**: `removeIf(bucket != current)`가 같은 버킷 폭주 시 아무것도 못 지움 → 위조 XFF로 고유키 무한 생성 시 단일 태스크 OOM. → 만료 정리 후에도 상한 초과면 맵 전체 `clear()`(OOM 우선).
- **R(결과):** 회귀 없는 점진 활성화(시크릿 미설정=기존 동작)로 무중단 배포. 회전 공격 무력화·5만 고유키 유계를 단위 테스트로 고정.
- **학습:** **프록시 체인의 신뢰경계**(append vs prepend, 최좌측/최우측 위조 가능성)를 정확히 알아야 한다. "leftmost XFF 신뢰"는 교과서적 취약점.

## 6. 테스트 하네스가 죽어도 간판은 확증한다 (TS-009)

> **한 줄:** colima에서 Testcontainers가 전멸 skip — 하네스에 매달리는 대신 실 PostGIS에 마이그레이션+시나리오를 직접 돌려 뷰 SQL을 확증.

- **S(상황):** `survival_score`의 핵심 리스크는 **V4 뷰 SQL**(최근성 버킷·신뢰도 가중·만료 제외)의 정확성인데, 이를 검증할 IT가 로컬(colima)에서 전부 SKIPPED.
- **T(과제):** 테스트 인프라가 죽은 상태에서도 간판 SQL의 정확성을 확신하고 진행하기(skip을 "통과"로 오독하지 않기).
- **A(행동):** 원인을 docker-java API 1.32 vs 엔진 최소 1.40 버전 협상 벽으로 좁혔으나 로컬에선 못 넘김. → **하네스 대신 리스크 자체를 공략**: `postgis/postgis:16` 컨테이너를 직접 띄우고 **V1~V4 마이그레이션을 psql로 순서대로 적용**한 뒤, 제보 시나리오(없음/신선/침수/전량 만료)를 넣고 뷰·리포지토리 쿼리를 그대로 실행해 신호값(comfort 캡 1.0·risk 0.7·만료 제외·거리 0.0)을 눈으로 검증. 결정 로직은 DB 없는 단위 8건으로, 엔드투엔드 IT 5건은 **표준 Docker인 CI(머지 게이트)** 에 위임.
- **R(결과):** 로컬 하네스 불능에도 간판 SQL을 실 엔진으로 확증하고, CI에서 IT green 확인 후 머지.
- **학습:** **검증 회복력** — 인프라가 죽으면 "무엇을 확신해야 하나"로 되돌아가 실 엔진에 직접 돌린다. skip은 통과가 아니다(스킵 수를 결과 XML로 명시 확인).

## 7. "내가 안 건드린 IT가 커넥션 에러로 실패" (TS-030)

> **한 줄:** 컨텍스트 수 × 풀 크기 = 커넥션 총량 소진 — 무관한 IT가 Flyway "too many clients"로 실패, 풀 캡 4로 안정.

- **S(상황):** 새 IT를 추가하자 **내 diff와 무관한** actuator IT 2건이 CI에서 `FlywaySqlUnableToConnectToDbException`으로 실패(플래키 아님).
- **T(과제):** 내 코드가 안 건드린 영역의 커넥션 실패 원인을 규명.
- **A(행동):** 에러 종류(`PSQLException`/커넥션 계열, 로직/스키마 아님)로 "접속 총량 소진"을 의심. 각 IT가 고유 프로퍼티(`jwt.secret` 7종 등)로 **서로 다른 Spring 컨텍스트**를 만들고 TestContext 캐시가 전부 살려둠 → **~10 컨텍스트 × HikariCP 풀 10 = ~100 커넥션**이 Testcontainers Postgres `max_connections`(100)에 육박. 컨텍스트를 하나 더 추가하자 임계 초과. → `AbstractIntegrationTest`에 `hikari.maximum-pool-size=4`를 추가(서브클래스 상속) → ~40 커넥션으로 안정. IT는 순차 MockMvc라 작은 풀로 충분, 프로덕션은 컨텍스트 1개라 무영향.
- **R(결과):** 한 줄 프로퍼티로 컨텍스트 수 증가에도 견디는 수술적 해결.
- **학습:** 커넥션 계열 실패는 **컨텍스트 수 × 풀 크기**를 본다. 컨텍스트별 고유 프로퍼티가 캐시를 늘려 커넥션을 잠식한다.

## 8. 동시 이중요청 500 — 제약위반 catch 후 tx 오염 (TS-031)

> **한 줄:** UNIQUE 충돌을 catch해도 같은 `@Transactional`은 이미 aborted — 이어지는 SELECT가 PG 25P02로 500.

- **S(상황):** 멱등 삽입을 `if(!exists){try{save}catch(중복){}} return count()`로 구현. 순차 경로는 정상인데, **진짜 동시 이중요청**(둘 다 exists 가드 통과)에서 뒤늦은 save가 UNIQUE 충돌.
- **T(과제):** catch로 예외를 삼켰는데도 나는 500의 원인을 잡고, 동시성에서 멱등을 보장.
- **A(행동):** 원인은 하나의 `@Transactional` 안에서 실패한 INSERT가 **트랜잭션 전체를 abort**시키고(PG는 문장 하나 실패 시 tx를 aborted로), 이어지는 `count()`가 `current transaction is aborted`(SQLState **25P02**) → 500. **catch가 예외는 삼켰지만 tx 오염은 못 되돌린다.** → 쓰기 메서드를 `@Transactional(propagation = NOT_SUPPORTED)`로 바꿔 exists/save/count를 **각자 tx**에서 실행 → 실패 save의 롤백이 자기 tx에 국한. 멱등성은 UNIQUE 제약이 보장하므로 세 호출의 원자성은 애초에 불필요. 같은 패턴의 기존 `ReactionService`에도 적용.
- **R(결과):** 동시 요청에서도 500 없이 멱등. 순차 테스트만으론 안 드러나는 경로라 적대적 리뷰가 잡음.
- **학습:** **제약위반을 catch해도 같은 tx는 오염됐다.** 대안은 tx 분리(NOT_SUPPORTED/REQUIRES_NEW) 또는 `INSERT … ON CONFLICT DO NOTHING`.

## 9. 사후 디버깅 대신 사전 예방 — advisory lock × 커넥션 풀 (TS-019)

> **한 줄:** HikariCP `close()`는 "풀 반환"이지 세션 종료가 아니다 — lock/unlock이 다른 물리 커넥션을 뽑으면 락 누수. 구현 전 사고 실험으로 예방.

- **S(상황):** 무인 스케줄(EventBridge→ECS RunTask)의 동시 실행 방지를 Postgres `pg_try_advisory_lock`으로 설계 중. **장애를 겪기 전에** 사고 실험으로 함정을 발견.
- **T(과제):** "락 걸고 배치 후 언락" 구현이 커넥션 풀 위에서 안전한지 착수 전에 따지기.
- **A(행동):** `JdbcTemplate` 각 호출은 `getConnection→실행→close`를 반복하는데, **HikariCP는 `close()`를 풀 반환으로만 처리하고 물리 세션은 살려둔다.** 반면 세션 수준 advisory lock은 **락을 건 그 세션**이 unlock하거나 종료돼야 풀린다 → lock/unlock이 풀에서 다른 물리 커넥션을 뽑으면 **원래 세션은 잠긴 채 락 누수**. → `runExclusive()`가 **단 하나의 `Connection`을 lock부터 unlock까지 물고 있다가** 마지막에만 close(try-with-resources 하나). 계약을 단위(같은 Connection으로 두 호출)와 IT(두 스레드 경합, 하나만 승리·해제 후 재시도 성공)로 고정.
- **R(결과):** 저동시성에서 우연히 숨었다가 부하에서 간헐 발생할 재현 난해 버그를 **착수 전에** 차단(TS-011/012/016이 "라이브/CI에서만 터진 뒤 사후 발견"이었던 것과 대비).
- **학습:** 세션 수준 리소스(advisory lock·`SET` 세션 변수)를 커넥션 풀 위에서 다룰 땐 **"풀 반환 ≠ 세션 종료"**. `close()`는 논리 반납이지 물리 종료가 아니다.

---

## 방법론 — 적대적 다중 에이전트 코드리뷰

위 3·5·8번은 **적대적 리뷰 워크플로**로 잡았다: 여러 렌즈(correctness·security·concurrency·repro)로 결함 후보를 **생성**하고, 각 후보를 독립 에이전트가 **반증(refute) 시도** → 반증을 못 넘긴 것만 확정으로 수용한다(TS-008은 14건 발견 중 확정 5건, 9건은 거짓양성으로 배제). 이 방식이 **순차 테스트로는 안 드러나는 동시성·신뢰경계 결함**을 커밋 전에 잡아준다. 확정분은 회귀 테스트로 고정하고, 회귀 없는 점진 활성화(예: 시크릿 미설정=기존 동작)로 무중단 배포한다.

> 원 서사·코드 위치는 [`TROUBLESHOOTING.md`](../TROUBLESHOOTING.md)(TS-001~033)와 [`docs/adr/`](./adr)(ADR 0001~0027)에.
