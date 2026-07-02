# 그늘 (Geuneul) — 트러블슈팅 로그 (TROUBLESHOOTING)

> **목적:** 면접·서류에서 "문제 해결 능력"을 보여주기 위한 문서. 어떤 문제를, 왜 생겼고, 어떻게 구체적으로 해결했는지 + **면접에서 어필할 포인트**를 남긴다.
> 심사자가 읽고 "이 사람 트러블슈팅 제대로 한다"고 느끼게 쓰는 것이 목표.

## 작성 규칙
- **문제를 해결할 때마다 맨 아래에 append.** 누적. 사소해 보여도 배운 게 있으면 남긴다.
- 반드시 포함: **상황(증상) → 원인 분석 → 해결 과정(매우 구체적, 재현 가능하게) → 결과 → 면접 어필 포인트.**
- 추측이 아니라 **어떻게 원인을 좁혔는지(로그/실험/이분탐색)** 를 적는다. 그게 실력의 증거다.
- 코드/설정/명령어는 그대로 인용. 커밋 해시 연결.

## 엔트리 템플릿
```
### TS-NNN · YYYY-MM-DD — <문제 한 줄 요약>
- **상황/증상:** 무엇이 어떻게 안 됐나 (에러 메시지·재현 조건 포함)
- **원인 분석:** 어떻게 원인을 좁혔나 → 진짜 원인
- **해결 과정:** 시도한 것들(실패 포함) → 최종 해결. 명령어/코드 구체적으로.
- **결과:** 무엇이 어떻게 개선됐나 (수치 있으면 before/after)
- **면접 어필 포인트:** 이 경험에서 보여줄 역량 (예: 공간인덱스 이해, 트랜잭션, 캐시 전략 등)
- **관련:** 파일 / 커밋 / 참고 링크
```

---

## 트러블슈팅

> _예상 후보(그늘 특성상 나올 법한 것): 공중화장실 표준데이터 WGS84 좌표 누락 → 주소 지오코딩 보완 / PostGIS 반경검색 인덱스 미적용으로 느린 쿼리 → GiST 인덱스 / 기상청 초단기예보 rate limit → Redis TTL 캐시 / 공공데이터 재적재 시 중복 → idempotent upsert._

### TS-001 · 2026-07-02 — Terraform apply 중단: AWS 보안그룹 description은 ASCII만 허용
- **상황/증상:** `terraform apply` 도중 31개 리소스 중 네트워크 일부(VPC/서브넷/IGW/라우팅)만 생성되고 중단.
  ```
  Error: creating Security Group (geuneul-alb-sg): api error InvalidParameterValue:
  Value (ALB: 인터넷에서 HTTP만) for parameter GroupDescription is invalid.
  Character sets beyond ASCII are not supported.
  ```
- **원인 분석:** 에러 메시지가 파라미터명(`GroupDescription`)과 규칙("beyond ASCII not supported")을 명시 → 코드 가독성을 위해 리소스 `description`에 한글을 썼는데, **EC2 SecurityGroup의 GroupDescription 필드는 ASCII 전용**. 사전 실행한 `terraform validate`는 통과했음 — validate는 **문법/스키마 검증**이지 AWS API의 값 제약(런타임 검증)까지는 못 잡는다는 것이 핵심 원인.
- **해결 과정:** ① `network.tf`의 SG 3개 description을 영어로 교체(`ALB: HTTP from internet only` 등) — 주석(한글 가능)과 API 필드(ASCII만)를 구분. ② Terraform은 이미 생성된 리소스를 state로 기억하므로 **부분 실패 상태에서 그대로 재 `apply`** → 나머지 리소스만 이어서 생성(멱등 수렴). 리소스 수동 정리 불필요.
- **결과:** 재적용으로 전체 스택 정상 생성. SG description은 생성 후 변경 시 **리소스 교체(replace)**를 유발하는 필드라, 생성 전에 잡은 게 다행.
- **면접 어필 포인트:** ① IaC의 검증 계층 구분 — `validate`(문법)/`plan`(계산)/`apply`(API 런타임 제약)는 각각 잡는 오류가 다름을 실전으로 확인. ② Terraform state 기반 **부분 실패 → 멱등 재적용** 복구 경험. ③ 클라우드 API의 로컬라이제이션 제약(ASCII-only 필드) 인지.
- **관련:** `infra/terraform/network.tf` / 커밋 (재적용 후 push)

### TS-002 · 2026-07-02 — IT 2번째 클래스부터 전멸: @Container(static) × Spring 컨텍스트 캐시 충돌
- **상황/증상:** PR #3 CI에서 통합테스트가 **첫 클래스는 통과, 이후 클래스(PlaceSpatialQueryIT·IngestionIdempotencyIT)는 전부 실패.** 로컬은 Docker가 없어 IT가 skip이라 재현 불가, CI에서만 발생.
  ```
  Caused by: org.hibernate.exception.JDBCConnectionException
      Caused by: java.sql.SQLTransientConnectionException
          Caused by: org.postgresql.util.PSQLException ... java.net.ConnectException
  ```
- **원인 분석:** 실패 예외가 SQL 문법이 아니라 **전부 ConnectException** → 쿼리 버그가 아니라 "DB가 그 주소에 없음" 신호. 그리고 "첫 클래스만 통과" 패턴이 결정적 단서.
  → 베이스 클래스에 `@Container`(static) + `@ServiceConnection` 조합을 썼는데, 이 둘의 수명주기가 충돌한다:
  1. Testcontainers 확장(`@Container` static)은 **각 테스트 클래스가 끝나면 컨테이너를 중지**한다.
  2. Spring TestContext는 동일 구성의 컨텍스트를 **JVM 전체에서 캐싱**한다 — 첫 클래스에서 만든 컨텍스트(당시 컨테이너의 랜덤 포트로 연결)를 다음 클래스가 재사용.
  3. 두 번째 클래스 시점엔 컨테이너가 중지(재시작돼도 **랜덤 포트가 바뀜**) → 캐시된 커넥션 풀이 죽은 포트로 접속 → ConnectException.
- **해결 과정:** `@Container` 어노테이션 제거 → **싱글턴 컨테이너 패턴**으로 전환(Testcontainers 공식 권장). static 필드 + static 블록에서 수동 `start()`, JVM 종료 시 Ryuk 컨테이너가 정리. Docker 없는 환경 대비는 `DockerClientFactory.isDockerAvailable()` 가드 + `@Testcontainers(disabledWithoutDocker=true)` 유지(이 어노테이션은 skip 판정에만 쓰고 수명주기 관리는 맡기지 않음).
- **결과:** 모든 IT 클래스가 컨테이너 1세트를 공유(재기동 없음) → 연결 안정 + CI 시간 단축(컨테이너 기동 1회).
- **면접 어필 포인트:** ① 프레임워크 2개(Testcontainers/Spring TestContext)의 **수명주기 경계 충돌**을 예외 패턴("첫 클래스만 통과")으로 좁혀낸 진단 과정. ② 싱글턴 컨테이너 패턴과 컨텍스트 캐싱의 원리 이해. ③ "로컬에서 재현 안 되는 CI 전용 실패"를 로그만으로 원인 규명.
- **관련:** `AbstractIntegrationTest.java` / PR #3
