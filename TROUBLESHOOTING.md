# 그늘 (Geuneul) — 트러블슈팅 로그 (TROUBLESHOOTING)

> **목적:** 문제를 어떻게 진단하고 해결했는지 기록하는 문서. 어떤 문제를, 왜 생겼고, 어떻게 구체적으로 해결했는지 + **핵심 학습 포인트**를 남긴다.
> 원인을 좁혀 간 과정이 드러나게 쓰는 것이 목표.

## 작성 규칙
- **문제를 해결할 때마다 맨 아래에 append.** 누적. 사소해 보여도 배운 게 있으면 남긴다.
- 반드시 포함: **상황(증상) → 원인 분석 → 해결 과정(매우 구체적, 재현 가능하게) → 결과 → 핵심 학습 포인트.**
- 추측이 아니라 **어떻게 원인을 좁혔는지(로그/실험/이분탐색)** 를 적는다. 그게 실력의 증거다.
- 코드/설정/명령어는 그대로 인용. 커밋 해시 연결.

## 엔트리 템플릿
```
### TS-NNN · YYYY-MM-DD — <문제 한 줄 요약>
- **상황/증상:** 무엇이 어떻게 안 됐나 (에러 메시지·재현 조건 포함)
- **원인 분석:** 어떻게 원인을 좁혔나 → 진짜 원인
- **해결 과정:** 시도한 것들(실패 포함) → 최종 해결. 명령어/코드 구체적으로.
- **결과:** 무엇이 어떻게 개선됐나 (수치 있으면 before/after)
- **핵심 학습 포인트:** 이 경험에서 보여줄 역량 (예: 공간인덱스 이해, 트랜잭션, 캐시 전략 등)
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
- **핵심 학습 포인트:** ① IaC의 검증 계층 구분 — `validate`(문법)/`plan`(계산)/`apply`(API 런타임 제약)는 각각 잡는 오류가 다름을 실전으로 확인. ② Terraform state 기반 **부분 실패 → 멱등 재적용** 복구 경험. ③ 클라우드 API의 로컬라이제이션 제약(ASCII-only 필드) 인지.
- **관련:** `infra/terraform/network.tf` / 커밋 8488eb6 (PR #2)

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
- **해결 과정:** `@Container` 어노테이션 제거 → **싱글턴 컨테이너 패턴**으로 전환(Testcontainers 공식 권장). static 필드 + static 블록에서 수동 `start()`, JVM 종료 시 Ryuk 컨테이너가 정리. Docker 없는 환경 대비는 `DockerClientFactory.instance().isDockerAvailable()` 가드 + `@Testcontainers(disabledWithoutDocker=true)` 유지(이 어노테이션은 skip 판정에만 쓰고 수명주기 관리는 맡기지 않음).
- **결과:** 모든 IT 클래스가 컨테이너 1세트를 공유(재기동 없음) → 연결 안정 + CI 시간 단축(컨테이너 기동 1회).
- **핵심 학습 포인트:** ① 프레임워크 2개(Testcontainers/Spring TestContext)의 **수명주기 경계 충돌**을 예외 패턴("첫 클래스만 통과")으로 좁혀낸 진단 과정. ② 싱글턴 컨테이너 패턴과 컨텍스트 캐싱의 원리 이해. ③ "로컬에서 재현 안 되는 CI 전용 실패"를 로그만으로 원인 규명.
- **관련:** `AbstractIntegrationTest.java` / PR #3

### TS-003 · 2026-07-02 — 부팅 불가 이미지 배포 → 크래시루프 백오프로 5시간 수렴 지연 (배포 신뢰성 2계층 사고)
- **상황/증상:** 핫픽스(PR #7) 배포의 GitHub Actions가 `{"state":"TIMEOUT","reason":"Waiter has timed out"}`으로 실패 표시. 그런데 라이브는 200이었고, ECS 이벤트를 보니 배포는 **워크플로우 실패 판정 후 ~4.5시간 뒤(23:04) 자체적으로 완료**됨(false-negative). 배포 생성(18:15)→완료(23:04) 사이 이벤트에 "health checks failed → stop → start" 사이클 다수.
- **원인 분석 (2계층):**
  1. **프로세스 실패(선행 원인):** 직전 PR #6을 **CI 빨간 상태로 머지** — 자동화 스크립트가 `gh run watch | tail`의 파이프 종료코드(=tail의 0)를 CI 결과로 오판. 그 이미지(Boot 4 `RestClient.Builder` 빈 부재)는 컨텍스트 생성 실패로 **부팅 자체가 불가**.
  2. **시스템 동작(증폭 요인):** 부팅 불가 이미지의 태스크가 크래시루프 → ECS가 서비스 레벨 **런치 백오프**를 걸어 후속(정상 핫픽스) 배포의 태스크 스케줄링까지 수 시간 지연. 서킷브레이커 미설정이라 자동 롤백도 없었음.
- **해결 과정:** ① 프로세스 — 머지 게이트를 `PIPESTATUS`가 아닌 **변수 캡처한 실제 exit code**로 판정하도록 교정(green 확인 후에만 merge). ② 시스템 — ECS **deployment circuit breaker + 자동 롤백** 활성화(terraform in-place 적용, 무중단). 이제 부팅 불가 이미지는 몇 분 내 실패 판정→직전 버전 롤백되어 백오프가 누적되지 않는다.
- **결과:** 서비스는 전 과정에서 무중단(롤링이 기존 태스크 유지). 이후 배포부터 "깨진 이미지 = 즉시 롤백 + 정직한 실패 신호" 보장.
- **핵심 학습 포인트:** ① 배포 사고를 **프로세스/시스템 2계층으로 분해**해 각각 교정(사람 실수 방지 장치 + 인프라 안전장치). ② ECS 배포 상태 머신(크래시루프→런치 백오프→서킷브레이커) 이해. ③ CI/CD 파이프라인의 **false-negative 신호** 문제 인지(워크플로우 결과 ≠ 실제 수렴 상태).
- **관련:** `infra/terraform/ecs.tf`(circuit_breaker), `.github/workflows/deploy.yml`, ECS 이벤트 로그 / WORKLOG 2026-07-02 핫픽스 항목

### TS-004 · 2026-07-02 — 화장실 60k 지오코딩 전량 실패(0건 적재): Boot 4 Jackson 3 × Jackson 2 JsonNode + 테스트 사각지대
- **상황/증상:** 프로덕션 지오코딩 인제스천이 exitCode=0으로 "성공" 종료했는데 결과가 `geocoded=0 geocodeFailed=54090` — **54,090건 전량 실패, 화장실 0건 적재**. 로컬 curl 단건 지오코딩은 HTTP 200으로 정상이었기에 더 혼란스러웠음.
- **원인 분석:** exitCode 0 + geocoded 0의 조합이 "예외로 죽은 게 아니라 매 호출이 조용히 empty 반환"임을 가리킴 → CloudWatch에서 `[geocode]` warn 로그를 뽑아보니 **전 건이 동일 에러**:
  ```
  Type definition error: [simple type, class com.fasterxml.jackson.databind.JsonNode]
  ```
  → **Spring Boot 4는 Jackson 3(`tools.jackson`)로 이전**됐는데, `KakaoGeocodingClient`가 응답을 **Jackson 2의 `com.fasterxml.jackson.databind.JsonNode`** 로 역직렬화하려 함. RestClient의 Jackson 3 변환기가 이 낯선 타입을 일반 빈으로 introspect하다 실패 → 모든 호출이 파싱 예외 → `catch`에서 empty. curl(순수 HTTP)이 성공한 건 Java 역직렬화 계층을 안 거쳤기 때문.
  - **진짜 근본 원인 = 테스트 사각지대:** IngestionIdempotencyIT가 `@Primary` **페이크 지오코더**를 주입해서 **실제 KakaoGeocodingClient의 HTTP 역직렬화 경로가 단 한 번도 테스트되지 않았다.** CI가 전부 green이었는데 프로덕션에서 100% 실패한 이유.
- **해결 과정:** ① `JsonNode` → **타입 있는 record(`KakaoAddressResponse`)** 로 역직렬화(Jackson 버전 무관하게 이름 매핑). ② 사각지대 제거 — **MockRestServiceServer로 실제 카카오 JSON을 파싱하는 단위 테스트 5건** 추가(도로명 우선/지번 폴백/0건/4xx/키없음). 이 테스트는 **로컬에서 실행**되어 JsonNode 버그를 재현·차단한다(Docker 불필요). ③ 멱등 파이프라인이라 실패분은 캐시 안 됨 → 수정 배포 후 재실행하면 전량 재시도되어 수렴. ④ 후속: 테스트용 2번째 생성자를 추가하자 Spring이 빈 생성 시 생성자를 못 골라 `contextLoads` 실패(NoSuchMethodException) → public 생성자에 `@Autowired` 명시. **이것도 컨텍스트 로드라 Docker 있는 CI에서만 드러남**(로컬 build는 IT skip) — "Docker 전용 테스트가 늦게 잡는 문제"의 반복이라, 이후엔 이런 변경은 CI green을 반드시 확인 후 진행.
- **결과:** 로컬 테스트로 파싱 검증(이전엔 페이크가 가려 불가능) → 재배포 후 재적재.
- **핵심 학습 포인트:** ① "exitCode 0인데 결과 0"이라는 조용한 실패를 로그 패턴으로 근본까지 추적. ② **메이저 프레임워크 이전(Jackson 2→3)의 은닉된 파괴**를 실제 사고로 경험·이해. ③ **모킹의 함정** — 외부 의존을 페이크로 대체하면 "그 의존과의 실제 계약(역직렬화)"이 미검증으로 남는다는 교훈, 그리고 그걸 MockRestServiceServer(실 변환기 경유)로 메운 것.
- **관련:** `KakaoGeocodingClient.java`, `KakaoGeocodingClientTest.java`, ECS task 29da1b2f 로그

### TS-005 · 2026-07-02 — 서킷브레이커가 "정상" 이미지를 오롤백: 느린 부팅(93s) vs 헬스체크 유예(120s)
- **상황/증상:** Jackson 수정(PR #10) 배포가 `Deployment ... rolled back by the deployment circuit breaker`로 실패. CI(실 PostGIS 컨텍스트 로드 포함)는 green이었고 이미지는 정상 부팅 가능한데도 서비스가 **직전(미수정) 이미지로 롤백**됨. #8·#9 배포는 같은 설정으로 성공했었음(플래키).
- **원인 분석:** ECS 이벤트 + `Started GeuneulApplication in 93.704 seconds` 로그가 단서. **0.25 vCPU에서 JVM+Spring 부팅이 ~93초**인데 ALB 대상그룹 헬스체크는 30초 간격·2회 성공 필요라 태스크가 "정상"으로 등록되기까지 ~150초 필요. 그런데 `health_check_grace_period_seconds=120`이라 120초 후부터 ECS가 헬스 실패를 집계 → 부팅 완료 전 태스크를 unhealthy로 죽임 → 실패 태스크 누적 → **서킷브레이커(TS-003에서 켠 안전장치)가 트립해 롤백**. 즉 TS-003의 안전장치가, 유예가 부팅보다 짧아 **역으로 정상 배포를 잡아먹은** 케이스. #8/#9가 통과한 건 부팅 시간이 임계값 근처라 운에 좌우됐기 때문.
- **해결 과정:** `health_check_grace_period_seconds` 120→**240초**(부팅 93초의 ~2.5배 여유)로 terraform in-place 적용(무중단) → 수정 이미지 재배포(workflow_dispatch). 서비스가 새 이미지로 정상 안정화. 근본적으로는 **0.25 vCPU가 JVM 부팅에 빠듯**한 것이므로, 트래픽이 붙으면 task_cpu 512로 올려 부팅을 단축하는 게 정석(지금은 비용 우선 + 저트래픽이라 유예로 해결).
- **결과:** 배포 안정화. "서킷브레이커=안전장치"가 잘못 튜닝되면 **가용성 저하 도구가 될 수 있음**을 실측으로 이해.
- **핵심 학습 포인트:** ① 안전장치(서킷브레이커)의 **오탐(false rollback)** 을 부팅시간 vs 유예 관점으로 규명 — 단순 "실패"가 아니라 임계값 튜닝 문제. ② ALB 헬스체크(interval·threshold)·ECS grace·서킷브레이커의 **상호작용 타이밍** 이해. ③ 근본(부팅 지연=CPU) vs 즉효(유예 확대)를 구분하고 비용 맥락에서 선택.
- **관련:** `infra/terraform/ecs.tf`(grace 240), Deploy run 28638813213(rollback)→재배포, TS-003(서킷브레이커 도입)

### TS-006 · 2026-07-03 — 프론트 프로덕션 빌드 2연속 실패: Next 16 Turbopack↔Serwist(webpack) 충돌 + ESLint 10↔eslint-plugin-react
- **상황/증상:** 프론트 MVP 첫 `pnpm build` 가 두 번 연달아 실패.
  1) `ERROR: This build is using Turbopack, with a webpack config and no turbopack config` → 빌드 자체가 중단.
  2) (빌드 통과 후) `pnpm lint` 가 룰 로딩 단계에서 크래시: `TypeError: Error while loading rule 'react/display-name': contextOrFilename.getFilename is not a function`.
- **원인 분석:**
  - **(1) Turbopack↔Serwist:** Next 16 은 **Turbopack 이 기본**인데, `@serwist/next`(next-pwa 후계 PWA 플러그인)는 서비스워커를 **webpack 플러그인**으로 주입한다. 그래서 nextConfig 에 webpack 설정이 생기고, 기본 Turbopack 빌드가 "webpack config 가 있는데 turbopack config 는 없음"으로 판단해 중단. Serwist 의 Turbopack 지원은 2026 기준 아직 실험(`@serwist/turbopack`)이라 안정 경로가 아님. 부수적으로 홈 디렉터리의 stray `~/package-lock.json` 때문에 Next 가 워크스페이스 루트를 오탐하는 경고도 겹침.
  - **(2) ESLint 10↔react 플러그인:** `eslint@latest` 로 10.6 이 깔렸는데, `eslint-config-next` 가 물고 오는 `eslint-plugin-react@7.37` 이 **ESLint 10 에서 제거된 `context.getFilename()`** 을 호출 → 룰 로딩부터 크래시. ESLint 10 이 갓 릴리스돼 플러그인 생태계가 아직 못 따라온 전형적 신버전 러그.
- **해결 과정:**
  - **(1)** 빌드/개발을 **webpack 으로 고정**: `next build --webpack` / `next dev --webpack`. Serwist 는 webpack 에서 정상 동작(빌드 로그 `✓ (serwist) Bundling the service worker...`). dev 는 SW 캐시가 HMR 을 방해하므로 `disable: NODE_ENV==='development'` 로 SW 자체는 끔. 루트 오탐은 `outputFileTracingRoot: process.cwd()` 로 frontend/ 고정.
  - **(2)** ESLint 를 **9.x(현 안정)로 다운핀**(`eslint@^9`). eslint-config-next 16 은 9 를 지원하고 react 플러그인도 9 호환. 남은 lint 지적 2건도 수정: 디바운스 훅의 "렌더 중 ref 대입"(신규 `react-hooks/refs` 룰) → `useEffect` 로 최신 ref 갱신, flat config 익명 default export → const 로 분리.
- **결과:** `typecheck`·`lint`·`build` 3종 그린 + `next start` 스모크(프록시 4종·페이지 렌더) 통과.
- **핵심 학습 포인트:** ① **빌드 툴체인 전환기의 통합 함정**(Next 16 Turbopack 기본화 vs webpack 기반 플러그인)을 이해하고 "안정 경로=webpack 고정 + Turbopack 은 실험이라 배제"라는 근거 있는 선택. ② **신규 메이저(ESLint 10) 러그**를 진단하고 생태계 호환선(9.x)으로 내려 안정화 — 무작정 최신이 아니라 "동작하는 최신"을 고름. ③ 두 문제 모두 "왜 실패했나"를 프레임워크 릴리스 맥락까지 짚어 근본 파악.
- **관련:** `frontend/next.config.ts`(webpack 고정·tracing root), `frontend/package.json`(scripts·eslint@^9), `frontend/lib/hooks.ts`, `frontend/eslint.config.mjs`

### TS-007 · 2026-07-03 — Vercel git 자동배포만 실패(ENOENT .next): 로컬 경고 억제 설정이 배포 파이프라인을 깨뜨림
- **상황/증상:** CLI 배포(`vercel deploy --prod`)는 성공하는데, **rootDirectory=frontend + GitHub 연결로 전환한 뒤의 git 자동배포만 Error.** 빌드 로그는 `✓ Compiled successfully`·전 라우트 생성까지 정상 — 실패는 빌드 뒤 단계였고 로그에 사유가 안 남음. `vercel redeploy`로 재현하자 실제 에러가 표면화: `ENOENT: no such file or directory, lstat '/vercel/path0/.next/package.json'`.
- **원인 분석:** 로컬에서 홈 디렉터리의 stray `~/package-lock.json` 때문에 Next가 워크스페이스 루트를 오탐하는 **경고를 억제하려고** 넣은 `outputFileTracingRoot: process.cwd()`가 원인. Vercel git 빌드는 **repo 루트를 clone(`/vercel/path0`) 후 rootDirectory(frontend) 기준으로 빌드·출력 수집**을 하는데, tracing root를 코드가 임의로 고정하면 출력(.next) 위치 계산이 어긋나 repo 루트에서 `.next`를 찾다 ENOENT. CLI 배포가 됐던 건 frontend/ 를 프로젝트 루트로 직접 업로드해 두 경로가 우연히 일치했기 때문.
- **해결 과정:** `...(process.env.VERCEL ? {} : { outputFileTracingRoot: process.cwd() })` — Vercel 환경에선 플랫폼 네이티브 처리에 맡기고, 로컬만 stray-lockfile 회피 유지. push → git 자동배포 Ready(50s) 확인, 라이브 alias가 새 빌드로 전환된 것까지 검증.
- **결과:** `git push main → Vercel 프로덕션 자동배포` 파이프라인 정상화. 이후 배포는 무개입.
- **핵심 학습 포인트:** ① "로컬 편의를 위한 설정이 **호스팅 플랫폼의 빌드 가정**(rootDirectory 기반 출력 수집)을 깨뜨릴 수 있다"는 사례 — 환경변수 가드(`process.env.VERCEL`)로 환경별 분기. ② 성공 로그 뒤에 숨은 배포 실패를 `redeploy` 재현으로 표면화시켜 진단한 과정. ③ CLI 배포와 git 배포의 **빌드 컨텍스트 차이**(업로드 루트 vs clone 루트)를 이해.
- **관련:** `frontend/next.config.ts`, Vercel project geuneul(rootDirectory=frontend, git connected), 커밋 a0bc0f1

### TS-008 · 2026-07-03 — 적대적 코드리뷰가 잡은 제보 API 2대 결함: 레이트리밋 우회(XFF 위조) + 맵 무한증가(OOM)
- **상황:** P2 제보 API 머지·배포 후, 다중 에이전트 적대적 코드리뷰(19 에이전트, 5개 차원 → 각 발견 반증 검증)를 돌렸다. 14건 발견 중 **확정 5건**(9건은 검증에서 반증/무해: check-then-increment TOCTOU는 soft-limit 관점 허용, place 하드삭제 레이스는 FK로 안전 등). 확정분은 실질 2대 결함:
  - **(A) XFF 최좌측 신뢰 → 레이트리밋 우회.** `ReportController.clientKey`가 `X-Forwarded-For.split(",")[0]`(최좌측)을 리밋 키로 썼다. **AWS ALB는 실 접속 IP를 XFF 최우측에 append**하므로 최좌측은 클라이언트가 위조 가능 → 공격자가 XFF를 회전시키면 매 요청이 새 키 → 분당3·시간당10 리밋이 무력화. ALB가 퍼블릭 http라 BFF 우회 직접 타격도 가능. 익명 제보는 유일 방어선이 이 리밋인데다 제보가 survival_score freshness를 굴리므로 스팸 오염 위험.
  - **(B) evictIfOversized no-op → 맵 무한증가(OOM).** `removeIf(w -> w.bucket != currentBucket)`가 **같은 버킷 폭주 시 아무것도 못 지운다**(모든 항목이 currentBucket). 위조 XFF로 고유키를 무한 생성하면 minute/hourWindows가 시간 창 동안 무한 증가 → 단일 Fargate 태스크 OOM. "맵 폭주 방지" 가드가 정작 폭주에서 실패.
- **원인:** 프록시 뒤 클라이언트 IP 신원의 **신뢰경계**를 잘못 잡음. "프록시 최좌측=원 클라이언트"는 프록시가 append가 아니라 prepend할 때만 참인데, ALB는 append다. 또 eviction 전략이 "시간 경과(다른 버킷) 항목"만 회수하고 "동일 버킷 카디널리티"는 회수 못 함.
- **해결:**
  - **(A) ProxyClientResolver** 도입(신뢰경계 명시): ① BFF가 공유 시크릿 `X-Proxy-Auth`로 증명하면 BFF가 판정한 실 클라이언트 IP `X-Client-Ip` 신뢰(`c:` 키) → BFF 경로 유저별 리밋. ② 시크릿 설정+미증명(직접 타격)이면 ALB append 최우측 XFF(위조 불가) → 실 IP당 하드 리밋. ③ 시크릿 미설정(현재)이면 기존 호환 최좌측 → **회귀 없음**, 활성화는 배포 후 Vercel·백엔드 env 동일 시크릿 주입 한 번. 순수 오버로드로 단위 테스트 7건(회전 공격 무력화 포함).
  - **(B) evict 하드 상한**: 만료 창 정리 후에도 상한 초과면 맵 전체 `clear()`(카운트 리셋 감수, OOM 우선). 유닛 테스트로 5만 고유키 폭주 시 유계 단정.
- **핵심 학습 포인트:** ① **프록시 체인 신뢰경계**(ALB append vs prepend, 최좌측/최우측 위조 가능성)를 정확히 이해하고 BFF-공유시크릿 패턴으로 해결 — "leftmost XFF 신뢰"라는 교과서적 취약점을 실제로 잡음. ② 적대적 다중 에이전트 리뷰로 자기 코드의 보안 결함을 배포 후 스스로 발견·검증(반증 통과분만 수용 → 거짓양성 9건 배제)하고 회귀 테스트로 고정. ③ 회귀 없는 점진 활성화(시크릿 미설정=기존 동작) 설계로 프로덕션 무중단.
- **관련:** `ProxyClientResolver`(+Test), `ReportRateLimiter`(evict·`trackedWindows`), `ReportController`, `application.yml`(geuneul.proxy-secret), 리뷰 워크플로 wf_bac51fa8. 활성화 절차는 HANDOFF "아침 체크리스트".
- **활성화(2026-07-04):** SSM `/geuneul/proxy_secret` + 태스크데프 rev13 재배포 + Vercel env → **라이브**. 헤더 고정 검증으로 우회 차단 확인(유효시크릿→c:키 유저별 리밋 / 위조→최우측 키잉). 샌드박스 egress 가변성 때문에 "XFF 회전" 직접 테스트는 무결론이었고 헤더 고정 테스트로 확정.

### TS-009 · 2026-07-04 — 로컬 Testcontainers가 colima에서 전멸 skip: docker-java API 1.32 vs 엔진 최소 1.40 — "테스트 하네스가 죽으면 SQL을 직접 검증"
- **상황/증상:** survival_score의 핵심 리스크는 **V4 뷰 SQL**(최근성 버킷·신뢰도 가중·만료 제외 집계)의 정확성인데, 이를 검증할 `SurvivalScoreIT`가 로컬에서 전부 SKIPPED. `AbstractIntegrationTest`는 `@Testcontainers(disabledWithoutDocker=true)`라 Docker 미감지 시 조용히 skip한다. colima는 떠 있는데도 테스트 워커가 Docker를 못 찾음.
- **원인 분석:** 두 겹이었다. ① **Testcontainers가 소켓을 못 찾음** — 기본 `/var/run/docker.sock`·DockerDesktop만 탐색하고 colima 소켓(`~/.colima/default/docker.sock`)·`DOCKER_HOST` env를 forked 테스트 JVM이 상속 못 함(Gradle 데몬 재사용). `--info` 로그로 "Could not find a valid Docker environment" 확인. ② env를 Gradle init 스크립트로 테스트 태스크에 주입하자 소켓은 잡혔으나 **`BadRequestException: client version 1.32 is too old. Minimum supported API version is 1.40`** — TC 1.20.6 번들 docker-java가 API 1.32로 말을 거는데 colima의 엔진이 ≥1.40만 허용. `DOCKER_API_VERSION` env도 zerodep 트랜스포트가 무시.
- **해결 과정:** (1) `~/.testcontainers.properties`·`DOCKER_HOST` env·init-script `environment()`·`DOCKER_API_VERSION` 등 조합을 시도했으나 API 버전 협상 벽은 로컬에서 못 넘김(sudo 없이 표준 소켓 심링크도 불가). (2) **하네스에 매달리는 대신 리스크 자체를 직접 공략**: `postgis/postgis:16-3.4` 컨테이너를 직접 띄우고 **실제 마이그레이션 V1~V4를 순서대로 psql로 적용**(V4 뷰 생성 성공 확인) → 제보 시나리오(제보없음/신선 COOL+SEAT_OK/+FLOOD/전량 만료)를 넣고 **뷰와 리포지토리의 스코어드 반경 쿼리를 그대로 실행**해 신호값을 눈으로 검증. comfort 캡 1.0(0.7+0.7→1.0)·risk 0.7(FLOOD severity 1.0×trust 0.7)·`WHERE expires_at>now()` 만료 제외·`COALESCE` 0신호·거리 0.0m 모두 설계와 일치. (3) 결정 로직은 DB 없는 `SurvivalScoreTest` 8건으로 로컬 green, 엔드투엔드 IT 5건은 표준 Docker인 **CI(머지 게이트)** 에 위임.
- **결과:** 로컬 하네스 불능에도 **간판 SQL을 실 엔진으로 확증**하고 진행. "IT가 skip이니 통과했겠지"라는 위양성(TS-004의 교훈: 모킹/스킵이 실계약을 가린다)을 반복하지 않음. CI에서 IT까지 green 확인 후 머지.
- **핵심 학습 포인트:** ① **검증 회복력** — 테스트 인프라가 죽어도 "무엇을 확신해야 하나(뷰 SQL 시맨틱)"로 되돌아가 실 PostGIS에 마이그레이션+시나리오를 직접 돌려 확증. ② 컨테이너 런타임 내부(docker-java API 버전 협상, XFF와 무관한 소켓/DOCKER_HOST 상속, Gradle forked JVM env)까지 원인을 좁히는 디버깅. ③ skip을 "통과"로 오독하지 않는 규율 — 스킵된 테스트 수를 결과 XML로 명시 확인하고 CI로 실제 실행을 보증.
- **관련:** `V4__place_report_signals_view.sql`, `SurvivalScoreIT`/`SurvivalScoreTest`, `AbstractIntegrationTest`(disabledWithoutDocker), colima. 교훈은 TS-002(컨테이너 수명)·TS-004(모킹이 실계약을 가림)와 한 계열.

### TS-010 · 2026-07-09 — Spring Boot 4에서 Redis 캐시 배선: 사라진 커스터마이저 + Jackson 3 직렬화 클래스 교체
- **상황/증상:** P3 날씨 TTL 캐시를 붙이며 `RedisCacheConfig`를 컴파일하자 두 개가 깨졌다. ① `import org.springframework.data.redis.cache.RedisCacheManagerBuilderCustomizer` → `cannot find symbol`. ② `new GenericJackson2JsonRedisSerializer()`는 `[removal] deprecated` 경고, 그리고 Jackson 3 버전 `GenericJacksonJsonRedisSerializer`(숫자 없음)는 `no-arg constructor 없음`(ObjectMapper 필수) 에러.
- **원인 분석:** Boot 4/Framework 7/Jackson 3로 넘어오며 (a) 캐시 자동설정 클래스 `RedisCacheManagerBuilderCustomizer`가 Boot 4 autoconfigure 재편에서 **어느 jar에도 없음**(레포 전 jar grep 0건 — 제거/이동됨). (b) spring-data-redis 4.x가 Jackson 2 기반 `GenericJackson2JsonRedisSerializer`를 deprecated-for-removal로 표시하고, Jackson 3 기반 `GenericJacksonJsonRedisSerializer`를 도입했는데 이건 **no-arg 생성자를 없애고** `builder()`/`create(Consumer)` 정적 팩토리만 노출(`tools.jackson.databind.ObjectMapper` 필요). 이는 TS-004(Boot 4=Jackson 3라 응답을 JsonNode 아닌 record로)의 같은 계열 — 버전 점프로 시그니처가 조용히 바뀐다.
- **해결 과정:** 추측 대신 **의존성 jar를 직접 열어** 실제 클래스·시그니처를 확인. (1) `unzip -l spring-data-redis-4.0.5.jar | grep Jackson.*RedisSerializer` → `GenericJacksonJsonRedisSerializer`가 Jackson 3판임을 확인. (2) `javap`로 생성자 확인 → no-arg 없음, `static builder()` 존재 → `GenericJacksonJsonRedisSerializer.builder().build()`로 교체. (3) 사라진 커스터마이저는 우회 — `RedisCacheManagerBuilderCustomizer` 대신 **`RedisCacheManager` 빈을 직접 정의**(`RedisCacheManager.builder(connectionFactory).withCacheConfiguration(...).build()`). 자동설정 클래스에 의존하지 않으니 버전 이동에 안정적이고, 캐시별 TTL/직렬화를 명시적으로 소유.
- **결과:** 컴파일 green, 유닛 12건 통과. 부수 효과로 설정이 더 명시적이 됨(어느 빈이 캐시 매니저인지 코드에 드러남).
- **핵심 학습 포인트:** ① 메이저 버전 점프(Boot 3→4, Jackson 2→3)에서 **import·생성자 시그니처가 조용히 바뀐다** — 컴파일러가 잡아주지만, "예제 그대로 붙여넣기"는 실패한다. 문서/블로그(대개 Boot 3·Jackson 2)를 그대로 믿지 말고 **jar를 열어 실물 API를 확인**(`unzip -l`/`javap`). ② 자동설정 편의 클래스(커스터마이저)가 사라지면 **한 겹 내려가 코어 빌더로 직접 구성**하는 게 버전 안정적. ③ TS-004와 동일 계열(Jackson 3 전환)이라, 이 레포에서 외부 JSON을 다루는 신규 코드는 항상 Jackson 3 기준으로 검증할 것.
- **관련:** `RedisCacheConfig`(RedisCacheManager 빈·GenericJacksonJsonRedisSerializer.builder()), `WeatherClient`(record 역직렬화), TS-004(Jackson 3 record 매핑). WORKLOG 2026-07-09.

### TS-011 · 2026-07-09 — 라이브에서만 터진 @Cacheable 500: Optional 언랩 + `unless` SpEL, 그리고 유닛테스트가 캐시 프록시를 우회
- **상황/증상:** 날씨(P3) 배포 후, KMA 키·ElastiCache가 실제로 붙은 rev23에서 `/weather`가 **500**. 정작 키 없던 rev22에선 graceful하게 `available:false`였다. 즉 **날씨가 실제로 조회될 때만(=성공 경로)** 500이 났다.
- **원인 분석:** 로그 스택트레이스 = `SpelEvaluationException: EL1004E: Method isEmpty() cannot be found on type ...weather.Weather`. `WeatherClient.fetchNowcast`의 `@Cacheable(unless = "#result == null || #result.isEmpty()")`가 범인. 메서드 반환형은 `Optional<Weather>`인데 **Spring 캐시 추상화는 Optional을 언랩**해서 `unless`의 `#result`에 **Weather(present) 또는 null(empty)**를 바인딩한다. 그래서 present일 때 `#result.isEmpty()` → Weather엔 없는 메서드 → SpEL 예외 → 500. empty일 때는 `#result == null`이 먼저 참이 되어 단락 → 예외 없음(그래서 키 없던 rev22는 멀쩡했다).
- **더 근본 원인(왜 못 잡았나):** `WeatherClientTest`는 `new WeatherClient(...)`를 **직접** 호출한다 → Spring CGLIB 캐시 프록시를 안 거치므로 `unless` SpEL이 **평가되지 않는다**. 로컬 유닛 12건 green이었지만 캐시 경로는 사각지대였다. (TS-004·TS-009와 같은 계열: "모킹/우회가 실계약을 가린다".)
- **해결:** (1) `unless = "#result == null"`로 교정 — 언랩된 값이 null(=빈 Optional)이면 미캐시, present면 캐시. 원래 의도(빈 결과 미캐시)와 정확히 일치. (2) **회귀 테스트 `WeatherCacheProxyTest` 추가**: `AnnotationConfigApplicationContext`에 `@EnableCaching`+`ConcurrentMapCacheManager`를 올리고 **프록시된 빈**을 통해 호출 → present 결과가 SpEL 오류 없이 캐시되고 2회차가 HTTP 없이 캐시 히트(`ExpectedCount.once()`)함을 검증. 구 `unless`였다면 이 테스트가 500과 동일한 예외로 실패한다. (3) 핫픽스 배포는 describe-task-definition이 rev23(전체 config)을 베이스로 이미지만 교체 → 설정 보존.
- **핵심 학습 포인트:** ① **@Cacheable + Optional 반환**에서 `condition`/`unless`의 `#result`는 **언랩된 값**이다(Optional 메서드를 부르면 안 됨) — null 검사로 판정하라. ② 캐시·트랜잭션·시큐리티처럼 **프록시(AOP)로 동작하는 기능은 프록시를 실제로 태우는 테스트**가 필요하다. 대상 객체를 직접 호출하는 단위 테스트는 애노테이션 의미를 검증하지 못한다. ③ "성공 경로에서만 나는 버그"는 실패 경로(키 없음)로만 검증하면 못 잡는다 — 실데이터/실키로 끝까지 실측(배포 실측)이 최종 게이트.
- **관련:** `WeatherClient`(@Cacheable unless), `WeatherCacheProxyTest`, `RedisCacheConfig`. WORKLOG 2026-07-09. 계열: TS-004(Jackson 우회)·TS-009(스킵=통과 오독).

### TS-012 · 2026-07-09 — 캐시 히트에서만 500: GenericJackson 무타이핑 → LinkedHashMap 캐스트 실패
- **상황/증상:** TS-011 수정 후 날씨가 라이브로 떴는데(광화문 22°C 비, 부산 31°C ✓), **같은 격자 2차 호출(=캐시 히트)** 에서만 500. 1차(캐시 미스)·다른 격자는 정상.
- **원인:** 로그 = `ClassCastException: java.util.LinkedHashMap cannot be cast to Weather`. `RedisCacheConfig`가 `GenericJacksonJsonRedisSerializer.builder().build()`(무타이핑)로 값을 저장 → **`@class` 타입 힌트 없이** 순수 JSON으로 직렬화. GET 시 대상 타입을 몰라 Jackson이 `LinkedHashMap`으로 역직렬화 → Spring 캐싱 aspect가 Weather로 캐스트하며 예외. 이 캐스트는 CacheErrorHandler의 get/put 경계 **밖**이라 안 삼켜지고 500으로 전파. (구 `GenericJackson2JsonRedisSerializer` 무인자 생성자는 default typing이 켜져 있었으나, Jackson 3판 `GenericJacksonJsonRedisSerializer.builder().build()`는 기본이 무타이핑 — 버전 이동으로 조용히 바뀐 기본값, TS-010 계열.)
- **해결:** "weather" 캐시는 Weather만 담으므로 **타입 바인드 직렬화기 `JacksonJsonRedisSerializer<>(Weather.class)`** 로 교체 — @class 없이도 정확히 Weather로 복원, 폴리모픽 default typing(`enableUnsafeDefaultTyping`)의 RCE 보안 우려도 회피. `weatherValueSerializer()`로 노출해 **직렬화 왕복 유닛테스트(`RedisCacheConfigTest`)** 로 고정(무타이핑이면 LinkedHashMap≠Weather로 실패).
- **핵심 학습 포인트:** ① 인메모리 캐시(ConcurrentMapCacheManager) 테스트는 **직렬화를 안 타므로** Redis 직렬화 계약을 검증 못 한다 — 직렬화기 자체를 왕복 테스트해야 한다. ② JSON 캐시 역직렬화는 **타입 정보**가 관건: 단일 타입 캐시는 타입 바인드가 가장 안전·간단(무타이핑 default typing은 보안 리스크). ③ 라이브 캐시 버그는 "1차는 되는데 2차(히트)에서 터짐"이 전형 신호. ④ TS-010·TS-011과 함께 "메이저 버전 점프에서 기본값·의미가 바뀐다"의 반복 — 실측(배포 후 2회 호출)이 최종 게이트.
- **관련:** `RedisCacheConfig`(JacksonJsonRedisSerializer), `RedisCacheConfigTest`, TS-010·TS-011. WORKLOG 2026-07-09.

### TS-013 · 2026-07-09 — 카카오 로그인이 안 되던 3중 콘솔 함정(KOE006→KOE010) — "구글이 되면 코드는 무죄"
- **상황/증상:** 프론트→백엔드 OAuth를 배포하니 **구글 로그인은 즉시 성공**(프로필까지), **카카오만 실패**. 처음엔 KOE006(Admin Settings Issue), 콘솔 수정 후엔 동의창까지 갔다가 KOE010(Bad client credentials)로 로그인 실패.
- **원인 분석(순차):** 동일 백엔드/프론트 코드로 구글이 되므로 코드는 정상 → 전부 **카카오 콘솔 설정** 문제로 좁힘. 세 겹이었다.
  - **(A) KOE006 = Redirect URI 미등록.** 사용자가 콜백 URL을 **[카카오 로그인]>[고급]의 "로그아웃 리다이렉트 URI"** 칸에 넣었음(로그인용이 아님). 게다가 개편된 콘솔은 로그인 Redirect URI가 **[앱]>[플랫폼 키]>[REST API 키 수정]>"카카오 로그인 리다이렉트 URI"** 로 이동([카카오 로그인]>[일반]엔 없음). 에러 화면의 "Why/How" 아코디언이 정확한 위치([App]>[Platform Keys])와 사용된 URI를 그대로 알려줘 확정.
  - **(B) 호출 허용 IP = 127.0.0.1.** REST API 키에 허용 IP가 로컬로 박혀 있어 AWS(ECS)에서의 토큰 교환이 차단될 소지 → 제거(전체 허용).
  - **(C) KOE010 = Client Secret 불일치.** 콘솔에서 Client Secret "활성화 ON"이면 토큰 요청에 client_secret **필수**. 백엔드에 SSM으로 배선했으나 **스크린샷의 시크릿을 눈으로 옮길 때 `I`(대문자 i)를 `l`(소문자 L)로 오독**(`…PP5luecQj` vs `…PP5IuecQj`) → Bad credentials. 카카오가 KOE010 안내 메일도 발송(활성화됐는데 시크릿 누락/오류).
- **해결:** (A) 로그인 Redirect URI를 올바른 칸에 정확한 문자열로 등록(`https://geuneul.vercel.app/api/auth/kakao/callback` + localhost). (B) 허용 IP 제거. (C) 정확한 Client Secret을 SSM 값 갱신 후 `update-service --force-new-deployment`(ECS secret은 태스크 시작 시 주입이라 값만 바꾸면 재기동 필요). → **구글·카카오 둘 다 실사용 성공.**
- **핵심 학습 포인트:** ① **"한쪽 제공자가 되면 공용 코드는 무죄"** — 실패한 제공자의 외부 설정으로 범위를 좁힌다. ② OAuth 제공자 **에러 화면/이메일의 상세(아코디언)** 가 최고의 진단원 — 추측 말고 그걸 펼쳐 읽어라(KOE006이 사용된 redirect_uri·등록 위치를 직접 명시). ③ 개편된 콘솔은 **필드 위치가 이동**한다(로그인 Redirect URI ≠ 로그아웃 Redirect URI, [플랫폼 키]로 이동) — 옛 문서/기억 금지. ④ **시크릿은 눈으로 옮기지 말 것**(I/l·O/0) — 복사 버튼 값을 쓰고, 안 되면 오독부터 의심. ⑤ ECS Secrets는 **태스크 시작 시점 주입** — SSM 값 변경 후 force-new-deployment로 재주입.
- **관련:** `KakaoOAuthClient`(client_secret 조건부 전송), `ssm.tf`(kakao_client_secret), 콘솔 앱 ID 1502770. 계열: 배포 실측이 최종 게이트(TS-011·012).

### TS-014 · 2026-07-09 — CI(실 Docker)에서만 터진 IT 컨텍스트 로딩 실패: `@SpringBootTest`에 ObjectMapper 빈이 없다
- **상황/증상:** 날씨 2부(ADR-0009) PR의 신규 `WeatherComfortIT`(실 PostGIS + `@MockitoBean WeatherClient`)가 로컬(colima Docker 미가용이라 skip)에서는 당연히 안 잡히고, **CI(실 Docker 러너)에서 처음** 4건 전부 `UnsatisfiedDependencyException`으로 실패. 원인은 테스트 로직이 아니라 컨텍스트 로딩 자체.
- **원인 분석:** 스택트레이스: `No qualifying bean of type 'com.fasterxml.jackson.databind.ObjectMapper' available`. 테스트가 `@Autowired ObjectMapper objectMapper`로 응답 JSON을 파싱하려 했는데, `AbstractIntegrationTest`(`@SpringBootTest` + `@AutoConfigureMockMvc`, 웹 MVC 슬라이스가 아닌 순수 IT)의 이 프로젝트 자동구성 조합에서는 `JacksonAutoConfiguration`이 `ObjectMapper` 빈을 등록해 주지 않았다. (`@WebMvcTest` 슬라이스에서는 되는 걸 SurvivalScoreIT류가 이미 jsonPath()로만 검증해 왔어서 이 갭이 지금껏 안 드러났다 — ObjectMapper를 직접 `@Autowired`한 IT가 이번이 처음.)
- **해결:** 앱 컨텍스트의 빈에 기대지 말고 **테스트 로컬 `private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();`** 로 교체. 이 IT의 관심사는 응답 JSON의 단순 필드(`survival.score`·`survival.comfortScore`) 파싱뿐이라 앱의 커스텀 Jackson 모듈이 전혀 필요 없었다 — 애초에 빈을 끌어올 이유가 없는 의존성이었다.
- **핵심 학습 포인트:** ① **"로컬 green"은 이 레포에서 사실상 무의미**하다(colima 이슈로 Testcontainers IT는 로컬에서 전부 skip) — 새 IT를 작성한 뒤엔 반드시 **CI를 실제로 태워서** 컨텍스트 로딩 자체를 검증해야 한다(TS-009 "SKIP≠통과"의 직접적 재확인 사례: 이번엔 로직이 아니라 **컨텍스트 부트스트랩** 단계에서 로컬이 못 잡는 실패였다). ② 테스트가 필요로 하는 최소 의존성만 끌어써라 — 앱 빈을 습관적으로 `@Autowired`하면 그 빈의 자동구성 여부라는, 테스트 목적과 무관한 결합이 생긴다. `new ObjectMapper()`처럼 자체 생성 가능한 유틸은 굳이 스프링 컨텍스트에서 가져오지 않는 게 더 견고하다.
- **관련:** `WeatherComfortIT`, ADR-0009. 계열: TS-009(로컬 SKIP≠통과), CI(`.github/workflows/ci.yml`)가 실 Docker로 도는 유일한 최종 게이트.
### TS-015 · 2026-07-09 — Boot 4 `@WebMvcTest`가 시큐리티 오토컨피그를 안 끌어옴: `SecurityConfig` Import만으론 `HttpSecurity` 빈이 없다
- **상황/증상:** 후기(review) API는 이 레포에서 **최초로 "로그인 필요" 컨트롤러 슬라이스 테스트**를 요구했다(ReportController/AuthController엔 이 패턴의 선례가 없었음). `@WebMvcTest(ReviewController.class) @Import(SecurityConfig.class)`로 작성했더니 9개 테스트 전부 `IllegalStateException: Failed to load ApplicationContext`로 실패(로그인 성공 케이스까지 전멸 — 컨텍스트 자체가 안 뜸).
- **원인 분석:** 스택트레이스 근본 원인은 `UnsatisfiedDependencyException: ... 'filterChain' ... No qualifying bean of type 'HttpSecurity' available`. `SecurityConfig.filterChain(HttpSecurity http, ...)`가 파라미터로 받는 `HttpSecurity` 빈은 Spring Security 코어의 `HttpSecurityConfiguration`이 만드는데, 이 설정 클래스는 `@EnableWebSecurity`가 있어야 `@Import`된다. 프로덕션 풀부팅에선 Spring Boot의 시큐리티 오토컨피그(`spring-boot-security` 모듈의 `ServletWebSecurityAutoConfiguration`, 패키지 `org.springframework.boot.security.autoconfigure.web.servlet.*` — Boot 4에서 재편된 위치)가 이걸 조건부로 켜준다. 그런데 **`@WebMvcTest` 슬라이스가 끌어오는 오토컨피그 목록**(`ImportsContextCustomizer`로 확인)엔 `WebMvcAutoConfiguration`·`JacksonAutoConfiguration`·`ValidationAutoConfiguration` 등만 있고 **시큐리티 오토컨피그가 전혀 없었다** — Boot 3까지 통용되던 "`@WebMvcTest`는 시큐리티를 자동 포함한다" 기대가 Boot 4에서 깨졌다(패키지 재편 + 슬라이스 큐레이션 변경, TS-010과 같은 "메이저 버전 점프에서 조용히 바뀐다" 계열).
- **해결 과정:** 추측 대신 실패 메시지의 `ImportsContextCustomizer` 목록을 직접 읽어 "무엇이 빠졌는지" 확인 → `spring-boot-security-4.0.6.jar`를 `unzip -l`로 열어 실제 오토컨피그 클래스명을 확보(`ServletWebSecurityAutoConfiguration`이 `@EnableWebSecurity`를 조건부 적용, `SecurityFilterAutoConfiguration`이 필터를 서블릿 컨테이너에 등록) → `@Import({SecurityConfig.class, ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})`로 명시 추가. 컨텍스트가 정상 로딩되고 9개 테스트(201/401×2/400×4/공개목록/클램프) 전부 green.
- **결과:** `ReviewControllerTest`가 실 `SecurityConfig` 필터체인(비로그인 401, 무효토큰 401, 로그인 시 principal 주입)을 실제로 태우는 첫 슬라이스 테스트로 확립. 다음에 "로그인 필요 컨트롤러" 슬라이스 테스트를 추가할 때 이 패턴을 재사용하면 된다.
- **핵심 학습 포인트:** ① **테스트 슬라이스(`@WebMvcTest` 등)의 "자동 포함" 기대는 메이저 버전마다 재검증**해야 한다 — Boot 3의 통념을 Boot 4에 그대로 가져오면 조용히 깨진다(TS-010·TS-004와 동일 계열: "버전 점프는 문서보다 실물 클래스패스를 확인"). ② 실패 스택트레이스의 `ContextCustomizer`/`ImportsContextCustomizer` 목록은 "이 슬라이스가 실제로 무엇을 로드했는지"를 정확히 보여주는 1차 진단 자료 — 추측보다 먼저 읽을 것. ③ Spring Security를 코드(필터체인 DSL)로 구성한 앱에서 `HttpSecurity` 빈은 `@EnableWebSecurity`(직접 또는 Boot 오토컨피그를 통해) 없이는 존재하지 않는다는 기초를 재확인.
- **관련:** `ReviewControllerTest`(`@Import` 3종), `SecurityConfig`, `spring-boot-security-4.0.6.jar`(`org.springframework.boot.security.autoconfigure.web.servlet.*`). 계열: TS-010(Boot4 재편)·TS-004(버전 점프 시 실물 확인).

### TS-016 · 2026-07-09 — CI(실 Postgres)에서만 터진 500: 네이티브 쿼리 프로젝션에 `OffsetDateTime` 선언 → `Instant` 언프로젝션 실패
- **상황/증상:** `ReviewFlowIT`(후기 작성→목록조회)이 **로컬은 Docker 없어 skip**이라 못 잡혔고, PR #31 CI(표준 Docker, 실 PostGIS)에서 `createThenList`만 실패. `GET /places/{id}/reviews`가 200이 아니라 500 → `jakarta.servlet.ServletException: ... UnsupportedOperationException: Cannot project java.time.Instant to java.time.OffsetDateTime; Target type is not an interface and no matching Converter found`.
- **원인 분석:** `ReviewRepository.findByPlaceIdWithAuthor`는 `reviews JOIN users` **네이티브 쿼리**를 `ReviewWithAuthorView` **인터페이스 프로젝션**으로 매핑한다. PostgreSQL `TIMESTAMPTZ` 컬럼(created_at/updated_at)을 JDBC 드라이버가 **`java.time.Instant`** 로 반환하는데, 프로젝션 인터페이스의 getter를 `OffsetDateTime`으로 선언해뒀다. Spring Data의 프로젝션 팩토리(`ProjectingMethodInterceptor`)는 위임 대상 타입이 프로젝션 인터페이스가 아니고 매칭되는 `Converter`도 없으면 **레이지하게(getter 호출 시점에)** `UnsupportedOperationException`을 던진다 — 그래서 `MockitoBean` 유닛테스트(값을 그대로 리턴)에선 절대 안 드러나고, **실 JDBC 드라이버가 Instant를 돌려주는 IT/CI에서만** 터진다. reviews 목록이 비어 있으면(getter를 호출할 행이 없으면) 역시 안 드러난다 — 그래서 `listIsPublicEvenWhenEmpty`는 통과하고 데이터가 있는 `createThenList`만 실패했다.
- **해결:** `ReviewWithAuthorView.getCreatedAt()/getUpdatedAt()`을 **`Instant`로 선언**(JDBC가 실제로 주는 타입과 일치)하고, `ReviewResponse.of(ReviewWithAuthorView, ...)`에서 `.atOffset(ZoneOffset.UTC)`로 명시 변환(애플리케이션 전역이 UTC — `application.yml`의 `hibernate.jdbc.time_zone: UTC`, Report 엔티티 주석의 기존 가정과 동일 근거).
- **핵심 학습 포인트:** ① **네이티브 쿼리 인터페이스 프로젝션의 getter 타입은 JDBC 드라이버가 실제로 반환하는 타입과 일치해야 한다** — JPA 엔티티(Hibernate가 컬럼 메타데이터로 변환)와 달리, 네이티브 쿼리 프로젝션은 드라이버가 준 원시 타입을 프록시가 그대로 통과시키려다 실패하면 컨버터 탐색 후 예외를 던진다. PostgreSQL TIMESTAMPTZ → pgJDBC는 `Instant`를 준다(OffsetDateTime 아님). ② 이번에도 **"모킹은 우리 로직만 검증하고 실 계약(드라이버 반환 타입)을 가린다"**(TS-004·TS-011과 동일 계열) — `ReviewServiceTest`는 리포지토리를 목으로 대체해 이 버그를 놓쳤고, `ReviewFlowIT`(실 Postgres)만 잡을 수 있었다. **로컬 IT skip(colima, TS-009) 상태에서 PR을 열었다면 CI가 최종 게이트라는 이 레포의 원칙이 정확히 이 버그를 위해 존재한다** — 실제로 CI가 잡았고, 머지 전에 고쳤다.
- **관련:** `ReviewWithAuthorView`, `ReviewRepository.findByPlaceIdWithAuthor`, `ReviewResponse.of`, `ReviewFlowIT`(PR #31 CI 런 29017025458에서 재현). 계열: TS-004·TS-009·TS-011(모킹/스킵이 실 계약을 가림, CI가 최종 게이트).

### TS-017 · 2026-07-09 — ADR 전제("도서관 오픈API=경기도만")가 실측으로 뒤집힘 — 구현 착수 전 추정을 검증하지 않은 대가
- **상황:** ADR-0006/HANDOFF는 "전국도서관표준데이터는 CSV 다운로드로 전국, 오픈API는 경기도 한정"이라 적어뒀고(과거 세션의 조사 근거), 이번 세션도 처음엔 그 전제를 그대로 받아 `SourceSpec.LIBRARY`(CSV 파서 경로)와 픽스처·IT를 먼저 만들었다.
- **원인 분석:** CLAUDE.md §B(의사결정 프로토콜)는 "웹 검색으로 트렌드·베스트프랙티스를 확인"까지는 요구하지만, 이번처럼 **실제 API를 직접 호출해 응답을 실측하기 전까지는 문서화된 과거 추정이 최신 진실이라는 보장이 없다.** `.local/datago.env`에 이미 확보돼 있던 `DATA_GO_KR_SERVICE_KEY`로 `https://api.data.go.kr/openapi/tn_pubr_public_lbrry_api`(전국도서관표준데이터 오픈API)를 직접 curl 해보니 **지역 파라미터 없이 페이지네이션만으로 전국 3,555건**(광주·서울 등 여러 시도 확인, `totalCount=3555`)을 반환했다 — "경기도만" 전제가 틀렸음을 그 자리에서 확인.
- **해결 과정:** CSV 기반으로 이미 작성한 `SourceSpec.LIBRARY`·`library_sample.csv`·`StandardCsvParserTest` LIBRARY 케이스·초안 IT를 **전량 되돌리고**, JSON 오픈API 기반의 새 패키지(`domain.ingest.openapi`)로 다시 설계했다. 손해(되돌린 코드)는 있었지만, 결과적으로 더 나은 설계로 이어졌다 — API가 `seatCo`(열람좌석수)를 레코드마다 직접 주기 때문에, CSV 경로에서는 "균일 규칙으로 근사"하려 했던 study_ok 백필(ADR 원안 "열람좌석수>0"을 컬럼 파싱 없이 카테고리 전체에 균일 적용)을 **레코드 조건부로 정밀 구현**할 수 있었다.
- **결과:** 정정된 설계로 완료. 상권정보(STUDY_CAFE/CAFE) API도 같은 방식으로 먼저 실호출해봤고, 이쪽은 **403(활용신청 미승인)** 임을 확인해 "계약 미검증"으로 명시 플래그 처리(추측 코드를 실측인 것처럼 포장하지 않음, ADR-0006 "구현 정정" 섹션).
- **핵심 학습 포인트:** ① **문서화된 과거 조사도 유효기간이 있다** — 데이터 소스가 살아있는 외부 API라면, 코드를 쓰기 전에 실제로 한 번 호출해보는 것이 웹 검색보다 싸고 확실하다(이번엔 curl 몇 번으로 몇 시간의 잘못된 방향을 피할 수 있었다). ② 실측이 불가능하면(승인 대기 등) **"검증됨"과 "리서치 기반 추정"을 코드 주석·ADR에 명확히 구분 표시**해 다음 사람이 신뢰 수준을 오인하지 않게 한다. ③ 되돌린 작업이 아깝다고 원래 설계를 억지로 맞추지 않고, 새로 확인된 사실(seatCo 필드 존재)에 맞춰 설계를 더 정밀하게 다시 짜는 게 결과적으로 이득이었다.
- **관련:** `domain.ingest.openapi.PublicLibraryIngestionService`, ADR-0006 "구현 정정" 섹션, `domain.ingest.storeapi`(계약 미검증 플래그). CLAUDE.md §B.
### TS-018 · 2026-07-09 — `terraform validate` 경고: `aws_s3_bucket_lifecycle_configuration`의 `rule`에 filter/prefix 필수
- **상황/증상:** S3 사진 버킷(`s3.tf`, 미완료 멀티파트 업로드 정리용)을 추가하고 `terraform validate`를 돌리니 에러는 아니지만 경고: `No attribute specified when one (and only one) of [rule[0].filter,rule[0].prefix] is required` + `This will be an error in a future version of the provider`. 리소스 자체는 `id`·`status`·`abort_incomplete_multipart_upload`만 있고 필터링 조건이 필요 없는 규칙(버킷 전체 대상)이었다.
- **원인 분석:** S3 라이프사이클 API는 규칙이 "무엇에 적용되는지"(filter 또는 구식 prefix)를 명시하도록 강제하는 방향으로 바뀌었는데(AWS 문서/`aws_s3_bucket_lifecycle_configuration` 스키마), 대상 provider(hashicorp/aws v5.100.0)는 아직 완전 강제(하드 에러)는 아니고 경고만 낸다 — "버킷 전체"라는 의도를 표현하는 관용구가 빈 `filter {}` 블록이라는 게 문서만 봐서는 바로 안 와닿았다(직관적으로는 filter를 아예 생략하면 "무조건"일 거라 생각하기 쉬움).
- **해결:** `filter {}`(빈 블록, 프리픽스/태그 조건 없음 = 버킷 전체)를 명시 추가. `terraform validate` 경고 0.
- **핵심 학습 포인트:** ① Terraform 경고("This will be an error in a future version")는 무시하지 말고 그 자리에서 고친다 — 다음 프로바이더 메이저 업그레이드에서 조용히 `apply` 실패로 바뀔 수 있다. ② "조건 없음"을 표현하는 관용구가 "생략"이 아니라 "빈 블록"인 리소스가 있다 — 스키마 경고 메시지가 정확한 힌트(`filter` 또는 `prefix` 둘 중 하나)를 주므로 그대로 따르면 된다.
- **관련:** `infra/terraform/s3.tf`(`aws_s3_bucket_lifecycle_configuration.photos`), P2 사진 presign 인프라.

### TS-019 · 2026-07-10 — advisory lock 설계 중 발견한 함정: HikariCP는 `Connection.close()`로도 세션(=advisory lock)을 안 끊는다
- **상황:** P3 무인 스케줄(EventBridge Scheduler→ECS RunTask)의 동시 실행 방지 가드를 `IngestBatchLock`(Postgres `pg_try_advisory_lock`)으로 설계하던 중 — 실제 장애를 겪은 게 아니라, **구현 전 사고 실험으로 미리 잡은 함정**이라 TS로 남기되 "예방"으로 명시한다.
- **원인 분석:** 처음 떠올린 구현은 `JdbcTemplate.queryForObject("SELECT pg_try_advisory_lock(?)", ...)`로 락을 걸고, 배치가 끝나면 별도로 `JdbcTemplate.queryForObject("SELECT pg_advisory_unlock(?)", ...)`를 부르는 방식이었다. 그런데 `JdbcTemplate`의 각 호출은 내부적으로 `DataSource.getConnection()` → 실행 → `Connection.close()`를 매번 반복한다. **HikariCP(다른 커넥션 풀도 동일)는 `close()`를 "풀에 반환"으로 처리할 뿐, 물리 TCP 연결(=Postgres 백엔드 세션)은 재사용을 위해 살려둔다.** 반면 `pg_try_advisory_lock`류(세션 수준 advisory lock)는 **락을 건 세션이 명시적으로 `pg_advisory_unlock`을 호출하거나 그 세션이 완전히 종료돼야** 풀린다. 즉 lock 호출과 unlock 호출이 **풀에서 서로 다른 물리 커넥션을 뽑아 쓰면** — 흔히 그렇게 된다, 풀에 여러 물리 커넥션이 있으니 — unlock이 아무 세션도 안 쥐고 있는 락을 풀려는 헛손질이 되어 **원래 락을 건 세션은 계속 잠긴 채로 남는다**(풀에 반환된 채, 다음 요청이 그 커넥션을 다시 뽑을 때까지 락 누수).
- **예방(구현에 반영):** `IngestBatchLock.runExclusive()`는 `DataSource.getConnection()`으로 얻은 **단 하나의 `Connection` 객체를 lock부터 unlock까지 끝까지 물고 있다가**(`try`-with-resources 하나로 감싸) 마지막에만 `close()`한다 — 중간에 `JdbcTemplate` 등 별도 커넥션을 뽑는 헬퍼를 쓰지 않는다. 이 계약을 `IngestBatchLockTest`(Mockito, "같은 Connection mock으로 lock/unlock 두 호출이 모두 간다"를 검증)와 `IngestBatchLockIT`(실 PostGIS, 두 스레드가 동시에 락을 다투면 하나만 이기고 락 해제 후 재시도가 다시 성공함을 검증)로 고정했다.
- **핵심 학습 포인트:** ① **세션 수준 리소스(advisory lock, `SET`으로 바꾼 세션 변수, prepared statement 이름 등)를 커넥션 풀 위에서 다룰 땐 "풀에 반환 = 세션 종료"가 아니라는 것**을 항상 의식해야 한다 — `close()`는 논리적 반납이지 물리적 종료가 아니다. ② 이런 함정은 보통 **동시성이 낮은 환경에서는 우연히 같은 커넥션을 재사용해 안 드러나다가**, 풀에 여러 커넥션이 살아있고 부하가 생기면 간헐적으로 나타나는 재현하기 어려운 버그가 된다 — 그래서 이번처럼 **구현 전에 "이 API가 세션 스코프인가?"를 먼저 따져보는 것**이 사후 디버깅보다 훨씬 싸다(TS-011·TS-012·TS-016이 전부 "라이브/CI에서만 터진 뒤 사후 발견"이었던 것과 대비되는, 이번엔 사전 예방 사례). ③ 크래시 안전성은 별개로 확보된다 — 프로세스가 죽으면 물리 커넥션 자체가 끊겨 Postgres가 세션 종료 시 advisory lock을 자동 해제하므로, "명시적 unlock을 못 타는 경로"가 있어도 영구 데드락은 아니다.
- **관련:** `IngestBatchLock`, `IngestBatchLockTest`, `IngestBatchLockIT`, ADR-0010. PostgreSQL 공식 문서 §9.27(Advisory Lock Functions).

### TS-020 · 2026-07-10 — EventBridge Scheduler Universal Target(ecs:runTask) input JSON은 PascalCase 필수
- **상황/증상:** 공공데이터 주기 동기화(ADR-0011) `terraform apply` 시 `aws_scheduler_schedule` 생성만 400 `ValidationException: Invalid RequestJson provided. Reason Request payload is missing the following field(s): TaskDefinition`. SSM·IAM 등 나머지는 정상 생성됨.
- **원인 분석:** Universal Target(`arn:aws:scheduler:::aws-sdk:ecs:runTask`)의 `input` JSON은 AWS SDK camelCase(`taskDefinition`·`cluster`·`overrides.containerOverrides`)가 아니라 **AWS API 모델의 PascalCase**(`TaskDefinition`·`Cluster`·`Overrides.ContainerOverrides`)를 요구한다. camelCase면 필수 필드를 못 찾아 400. Terraform은 이 input을 타입체크하지 않아(임의 JSON 문자열) apply 시점에야 드러난다(C3가 PR에서 "input 미검증"으로 이미 리스크 플래그한 지점 — 실측이 그 리스크를 실현).
- **해결:** input 블록 전체를 PascalCase로 교체(`Cluster`/`TaskDefinition`/`LaunchType`/`Count`/`NetworkConfiguration.AwsvpcConfiguration.{Subnets,SecurityGroups,AssignPublicIp}`/`Overrides.ContainerOverrides[].{Name,Command}`) → 재적용 시 스케줄이 `DISABLED` 상태로 정상 생성.
- **핵심 학습 포인트:** ① IaC가 "임의 문자열/JSON"으로 넘기는 필드(Universal Target input, IAM 정책 문서 일부 등)는 `terraform validate`가 못 잡는다 — **apply(또는 실트리거)까지 가야 검증**된다. ② AWS Universal Target은 SDK가 아니라 **API 모델 네이밍(PascalCase)**을 따른다. 서브에이전트가 "미검증"으로 플래그한 지점은 통합자가 반드시 실검증할 것.
- **관련:** `infra/terraform/scheduler.tf`, ADR-0011, TS-019(같은 브랜치의 advisory-lock 함정). (2026-07-10 후속: 실트리거 1회 검증(수동 RunTask exit 0) 후 스케줄 **ENABLED**, default=true 승격 — PR #42.)
### TS-021 · 2026-07-10 — 부하테스트 준비 중 두 함정: ① LATERAL 서브쿼리의 random()이 전 행 동일값으로 캐시됨 ② bounds 대박스 Seq Scan을 "인덱스 미사용 버그"로 오독할 뻔
- **상황:** P4 k6 부하테스트를 위해 합성 30만 places를 시드하려고 카테고리를 `CROSS JOIN LATERAL (SELECT (ARRAY[...])[1+floor(random()*10)] AS category)`로 뽑았다. 적재 후 분포를 확인하니 **30만 행 전부 단일 카테고리(WATER)**. 또 EXPLAIN에서 bounds 대박스(서울 도심)가 `Seq Scan on places`로 나와 "GiST 인덱스를 안 탄다"고 성급히 판단할 뻔했다.
- **원인 분석:**
  1. **LATERAL 서브쿼리에 outer(gs) 참조가 없으면 플래너가 한 번만 평가하고 캐시한다.** `random()`은 VOLATILE이지만, 상관(correlation) 없는 LATERAL 서브쿼리 전체가 "행마다 재평가할 이유가 없는 상수식"으로 취급돼 첫 평가값이 전 행에 뿌려졌다. 즉 VOLATILE 함수라도 **어디에 놓느냐**(파생 서브쿼리의 SELECT 목록 vs 상관 없는 LATERAL)가 행별 재평가 여부를 가른다.
  2. **bounds 대박스의 Seq Scan은 옵티마이저의 정상 판단이었다.** `p.geom && ST_MakeEnvelope(...)` + `LIMIT 100` + `ORDER BY` 없음이고, 시드의 70%가 수도권에 몰려 있어 수도권 큰 박스는 첫 ~9천 행 안에서 100건이 다 찬다. 플래너는 "박스가 테이블의 큰 비율과 겹치고 LIMIT이 작다 → Bitmap 구성 오버헤드보다 조기종료 Seq Scan이 싸다"를 정확히 계산한 것.
- **해결 과정:**
  1. 카테고리/좌표 계산을 **파생 서브쿼리 `FROM (SELECT gs, (ARRAY[...])[...] AS category, ... FROM generate_series(1,:n) gs) t`의 SELECT 목록**으로 옮겨 행마다 random()이 평가되게 했다. 재적재 후 분포 정상화(TOILET 90k=30%, 나머지 ~30k씩). is_commercial은 같은 파생 컬럼 `t.category`를 참조해 재계산 캐시 문제를 원천 회피.
  2. bounds가 진짜로 GiST를 타는지 **희소(강원 산간 작은 박스)와 밀집(서울 대박스)을 각각 EXPLAIN으로 대조**. 희소 박스는 `Bitmap Index Scan on idx_places_geom`(1.7ms)로 인덱스를 실제로 탔다. 즉 "인덱스는 이득이 될 때만 쓴다"는 정상 동작 — 대박스 Seq Scan은 버그가 아니라 정답. 이 판별을 ADR-0010·`perf/explain/RESULTS.md`에 근거와 함께 기록.
- **핵심 학습 포인트:** ① **VOLATILE 함수의 "행별 평가"는 함수 종류가 아니라 배치 위치가 결정한다** — 상관 없는 LATERAL/서브쿼리는 상수 폴딩·단일 평가 대상이 될 수 있으니, 행마다 달라야 하는 랜덤은 반드시 행을 생성하는 스캔(generate_series)의 직접 SELECT 목록에서 계산하고 EXPLAIN/실측으로 분포를 검증한다(합성 데이터 자체가 편향되면 부하테스트 결론이 통째로 틀어진다). ② **EXPLAIN의 Seq Scan을 반사적으로 "인덱스 미사용 결함"으로 읽지 않는다** — LIMIT + 낮은 selectivity(넓은 술어)에서는 조기종료 Seq Scan이 정답일 수 있다. "인덱스가 있는데 왜 안 쓰나"가 아니라 "이 술어·LIMIT에서 인덱스가 이득인가"를 selectivity를 바꿔가며(희소 vs 밀집 박스) 대조해야 옵티마이저의 의도를 읽는다.
- **관련:** `perf/seed/seed_synthetic_places.sql`, `perf/explain/explain_spatial_queries.sql`, `perf/explain/RESULTS.md`, ADR-0010, CLAUDE.md §0-4(GiST·전체스캔 금지). 계열: 합성 데이터/실측 검증의 중요성(TS-004·TS-017 "추정을 실측으로 검증").

### TS-022 · 2026-07-10 — `/actuator/prometheus`가 이미 프로덕션에 인증 없이 공개돼 있었다(관측성 작업 착수 전 실측으로 발견)
- **상황/증상:** P4 관측성(ADR-0014) 작업을 시작하기 전, `application.yml`을 읽다가 `management.endpoints.web.exposure.include: health,info,prometheus`가 하드코딩돼 있는 걸 발견했다. "설마 이미 라이브에 나가 있나?" 싶어 실제 ALB URL로 확인: `curl http://<ALB>/actuator/prometheus` → **200과 함께 전체 Micrometer 메트릭 덤프**(JVM 힙, GC, HTTP 요청 경로별 카운트 등)가 인증 없이 그대로 응답했다.
- **원인 분석:** 이 프로퍼티와 `micrometer-registry-prometheus` 의존성은 이전 세션에서 P4를 대비해 미리 스캐폴드해 둔 것으로 보이는데, "노출을 언제 켤지"에 대한 안전장치(옵트인·인증·네트워크 격리) 없이 기본값으로 들어가 있었다. `SecurityConfig`는 명시적으로 나열한 경로(`/me`·`POST /reviews`·`POST /flags`·`/admin/**`)만 보호하고 나머지는 `permitAll()`이라 `/actuator/prometheus`도 자동으로 공개 대상에 포함됐다. ALB target group이 컨테이너 포트 8080 하나만 바라보므로 앱이 그 포트에 무엇을 노출하든 인터넷에서 바로 도달 가능하다 — "배포된 적 없는 기능이라 안전하다"는 가정이 틀렸다(실제로 이미 여러 차례 배포된 `backend/**` 변경에 실려 나가 있었다).
- **해결:** `management.endpoints.web.exposure.include`를 `${MANAGEMENT_EXPOSURE:health,info}`로 바꿔 **prometheus를 옵트인**으로 전환했다(ADR-0014 §1). ECS 태스크데프가 이 env를 주입하지 않는 한 다음 배포부터 자동으로 닫힌다 — 인프라(SG·target group) 변경이 전혀 필요 없다. `ActuatorExposureIT`(신규)가 "기본 설정에서 prometheus는 항상 404"를 회귀 테스트로 못 박는다.
- **핵심 학습 포인트:** ① **"아직 안 썼으니 안전하다"는 스캐폴드 코드에 대한 착각이다** — `application.yml`에 값이 존재하고 CI/CD가 `backend/**`를 자동 배포하는 한, "쓴 적 없음"과 "배포 안 됨"은 다르다. 새 관측성 작업을 시작하기 전 **먼저 라이브 상태를 실측**(curl)한 게 이 발견의 유일한 경로였다 — 코드만 읽었다면 "prometheus가 include에 있네" 정도로 넘어갔을 것이다. ② 민감할 수 있는 엔드포인트를 다루는 작업에서는 "설계하기 전에 지금 무엇이 실제로 노출돼 있는지부터 확인"이 첫 단계여야 한다(TS-017 "추정을 검증하지 않은 대가"와 같은 계열 — 이번엔 요구사항이 아니라 현재 프로덕션 상태에 대한 추정).
- **관련:** `backend/src/main/resources/application.yml`, ADR-0014, `ActuatorExposureIT`. 계열: TS-017(추정 대신 실측 검증).

### TS-023 · 2026-07-10 — 로컬 검증 중 발견: `postgis/postgis` 이미지는 `pg_isready`가 성공해도 PostGIS 확장 초기화 스크립트가 아직 안 끝났을 수 있다
- **상황/증상:** 관측성 스택(Prometheus/Grafana/Tempo) 검증을 위해 임시 `postgis/postgis:16-3.4` 컨테이너를 띄우고 `pg_isready`로 준비를 확인한 뒤 곧바로 백엔드(`java -jar`)를 기동했더니, Flyway가 DB 연결 자체에서 실패했다(`EOFException` at `ConnectionFactoryImpl.enableSSL` — TLS 협상 중 서버가 연결을 끊음).
- **원인 분석:** `docker logs`로 확인하니 `pg_isready`가 성공을 보고한 시점 이후에도 컨테이너 엔트리포인트가 `/docker-entrypoint-initdb.d/10_postgis.sh`(PostGIS 확장 `CREATE EXTENSION` 2종 × 2개 DB)를 계속 실행 중이었다 — 이 이미지는 **"기본 postgres가 accept 상태"와 "커스텀 init 스크립트까지 전부 끝난 상태"가 다르고, `pg_isready`는 전자만 본다.** init 스크립트가 도중에 재시작/재바인드를 하는 구간에 접속을 시도하면 TCP는 열려 있어도 SSL 핸드셰이크 중 끊긴다.
- **해결:** `docker exec ... psql -U geuneul -d geuneul -c "select 1;"`로 **실제 쿼리가 도는지**까지 확인한 뒤 재시도했더니 정상 연결됐다. 재현 가능한 안전한 대기 방법은 `pg_isready` 폴링이 성공한 뒤에도 짧게(수 초) 추가로 기다리거나, `psql -c "select postgis_version();"`처럼 확장이 실제로 로드됐는지 쿼리로 확인하는 것.
- **핵심 학습 포인트:** **헬스체크 도구가 "무엇을 확인하는지"를 그 이미지의 실제 엔트리포인트 스크립트 기준으로 다시 확인해야 한다** — `pg_isready`는 순정 Postgres 기준의 헬스체크이고, 확장 이미지(PostGIS)의 커스텀 초기화까지는 알지 못한다. 이 레포의 `docker-compose.yml`은 이미 동일 이미지에 healthcheck를 걸어 두었지만(`pg_isready`), 첫 기동(볼륨이 비어 있어 init 스크립트가 실제로 도는 경우)에는 여전히 이 레이스가 이론상 가능하다 — 지금까지 문제가 없었던 건 로컬 개발 흐름상 `docker compose up -d` 후 `./gradlew bootRun`까지 수 초의 자연스러운 지연이 있었기 때문일 가능성이 높다(자동화 스크립트에서 두 명령을 곧바로 이어붙이면 재현될 수 있음 — 향후 CI/스크립트화 시 유의).
- **관련:** 로컬 검증(임시 컨테이너, 커밋 대상 아님), `docker-compose.yml`(postgres healthcheck), ADR-0014.

### TS-024 · 2026-07-10 — `.tfvars`에 셸 env로 읽은 시크릿을 스크립트로 써넣을 때 trailing newline이 HCL 멀티라인 문자열을 만들어 `terraform plan`이 깨졌다
- **상황/증상:** AI 프로바이더를 Mistral로 전환하며 `terraform.tfvars`의 `openrouter_api_key`를 `ai_summary_api_key`로 리네임하고 `.local/ai.env`의 키 값을 주입했다. 그런데 `terraform plan`이 `Invalid multi-line string / Quoted strings may not be split over multiple lines`(line 11)로 실패했고, 값을 고쳐 쓴 뒤에도 이번엔 line 12에 홀로 남은 `"` 때문에 `Invalid argument name`으로 또 깨졌다.
- **원인 분석:** ① 첫 주입에서 `python re.sub`로 `key = "..."`를 만들 때 소스 값에 개행이 섞여 들어가 `ai_summary_api_key = "<키>\n"`처럼 **따옴표 안에 개행**이 생겼다 — HCL은 큰따옴표 문자열을 여러 줄에 걸칠 수 없어 파싱 실패. ② 이를 "라인 11만 재작성"으로 고쳤더니, 원래 두 줄로 쪼개져 있던 값의 **둘째 줄(닫는 `"` 하나)이 라인 12에 그대로 남아** 다시 파싱이 깨졌다(첫 수정이 라인 하나만 교체하고 잔여 라인을 지우지 않은 탓).
- **해결:** 키 값을 쓰기 전에 `key.strip().replace("\r","").replace("\n","")`로 **개행·CR·공백을 전부 제거**한 뒤 라인을 한 줄로 재작성하고, 남아 있던 잔여 `"` 라인(정확히 `"`인 줄)을 삭제했다. 이후 `terraform validate` → `plan`이 정상(`1 to add, 1 to destroy`)으로 통과했다. 시크릿 값은 gitignore된 `terraform.tfvars`에만 있고 git에는 들어가지 않는다(규칙 D) — 다만 깨진 `terraform` 에러 메시지가 값을 콘솔에 노출했으므로, 이런 스크립팅 실수 자체가 **작업 로그·터미널로의 우발적 시크릿 노출 경로**임을 유의(대화는 허용, 커밋·푸시는 금지 경계는 지켜짐).
- **핵심 학습 포인트:** ① **셸 `. env` 소싱 → 스크립트로 파일에 써넣는 경로에서는 값의 trailing newline을 항상 제거**하라(`.strip()`/`tr -d '\n\r'`). 특히 HCL·JSON·YAML처럼 개행이 문법적으로 유의미한 포맷에 값을 끼워 넣을 때 치명적이다. ② **"한 줄만 교체"하는 편집은 그 줄이 원래 여러 줄로 오염돼 있으면 잔재를 남긴다** — 재작성 전에 대상이 정말 한 줄인지 확인하거나, 값 주입은 "라인 지우고 새로 삽입"이 아니라 "구조를 파싱해서 키만 세팅"하는 방식이 안전하다. ③ 시크릿을 파일에 주입할 때는 성공/실패와 무관하게 **값을 stdout에 찍지 않는 경로**(길이·마스킹만 출력)로 설계하면, 파싱 에러가 값을 토해내는 사고도 줄일 수 있다.
- **관련:** `infra/terraform/terraform.tfvars`(gitignore), PR #41 후속 배포, WORKLOG 2026-07-10 AI 라이브 배포, CLAUDE.md 규칙 D(비밀 경계).

## TS-025 — 네이티브 프로젝션 timestamptz는 Instant로 받아야 한다(TS-016 재발) + `gh run watch` EXIT를 CI 판정으로 오신뢰

**증상**: ⑥ 후기 커뮤니티의 `GET /reviews/{id}/comments`가 실 Postgres(CI)에서만 500. 스택:
`java.lang.UnsupportedOperationException: Cannot project java.time.Instant to java.time.OffsetDateTime;
Target type is not an interface and no matching Converter found`. 로컬 컴파일·단위테스트는 green이라 안 잡힘(IT는 colima로 skip, TS-009).

**원인 1 (재발한 함정 = TS-016)**: `ReviewCommentWithAuthorView.getCreatedAt()`를 `OffsetDateTime`으로 선언했다.
네이티브 쿼리 결과의 PostgreSQL `TIMESTAMPTZ`는 JDBC가 `Instant`로 반환하는데, Spring Data 인터페이스
프로젝션 팩토리는 Instant→OffsetDateTime을 자동 변환하지 못한다. `ReviewWithAuthorView`(TS-016)가 이미
같은 이유로 `Instant` + `ReviewResponse.of`에서 `.atOffset(ZoneOffset.UTC)` 변환을 쓰고 있었는데, 새 프로젝션을
만들며 그 교훈을 놓쳤다. **해결**: getter를 `Instant`로 바꾸고 `ReviewCommentResponse.of`에서 UTC 부착.

**원인 2 (프로세스 실패, 더 중요)**: 이 결함이 ⑥ PR CI에서 실제로 **실패**했는데도 머지됐다. `gh run watch
<run_id> --exit-status`가 EXIT=0을 반환해 통과로 오판한 것 — `gh run list --limit 1`이 집은 run_id가 실패한
백엔드 "CI" run이 아니라 통과한 다른 run(gitleaks/Next.js)이었을 수 있다. 그 EXIT를 유일 근거로 삼아 `gh pr
merge`를 실행했고, 같은 방식으로 ⑦·⑧까지 red 위에 쌓였다(다행히 실패 테스트는 CommunityFlowIT 1개뿐이라
325개 중 나머지는 전부 실측 green이었다).

**핵심 학습 포인트**:
1. **머지 전 반드시 `gh pr checks <N>`로 모든 체크가 pass인지 눈으로 확인**한다. `gh run watch`의 EXIT
   코드 하나만 믿지 않는다 — `--limit 1`이 어느 워크플로우 run을 집었는지 불확실하다(한 push가 CI·Next.js·
   gitleaks 여러 run을 만든다). 백엔드 게이트는 이름이 "Backend (Gradle, JDK 21)"인 체크의 conclusion을 본다.
2. **새 네이티브 쿼리 인터페이스 프로젝션에 시각 컬럼이 있으면 무조건 `Instant`**(+ DTO에서 UTC 부착).
   기존 `ReviewWithAuthorView` 패턴을 복사한다. 로컬은 못 잡으니(IT skip) CI가 유일 게이트 — 그래서 원인 1이
   원인 2와 겹치면 "로컬 green → 머지 → main red"가 조용히 성립한다.

## TS-026 — 미검증 스캐폴드의 "응답 계약 추정"은 실호출로만 확증된다(상권정보 sdsc2)

**상황**: 상권정보 오픈API(B553077 상가업소정보)는 2026-07-09까지 활용신청 미승인(403)이라, 코드
(`domain.ingest.storeapi`)는 **공식 매뉴얼·서드파티 가이드 리서치 기반으로 계약을 "추정"**해 스캐폴드만
해뒀다(ADR-0006에 "승인 후 재검증 필요"로 명시). 2026-07-10 사용자가 "data.go.kr 승인 완료"를 알려
실호출하니 `resultCode=00`. 이때 추정 계약과 실제가 **세 군데 어긋나 있었다** — 검증 없이 배포했으면
인제스천이 조용히 0건(파싱 실패 → 빈 페이지)으로 돌았을 것이다.

**원인(추정 vs 실제)**:
1. **응답 봉투 형태** — 스캐폴드는 같은 포털의 도서관 표준 API(`tn_pubr_public_lbrry_api`, 실측 완료)를
   본떠 `{response:{header,body}}` 래퍼를 가정했다. 그러나 sdsc2 실응답은 **`{header, body}`가 최상위**
   (response 래퍼 없음). → `StoreApiResponse(Response response)`로 역직렬화하면 `response`가 null →
   클라이언트가 `body.response()==null`에서 빈 페이지 반환 → **전량 0건**(예외도 안 남, 조용한 실패).
2. **좌표·페이지네이션 타입** — 추정은 문자열(`"lon":"126.9"`)이었으나 실제는 **JSON 숫자**
   (`"lon":126.979...`, `"totalCount":1347`). record 필드가 String이면 Jackson 기본 강제변환에 기대야 해
   불안정 → `Double lon/lat`, `Integer totalCount`로 실측 타입에 맞춤.
3. **업종 소분류코드** — 추정은 개편 전 코드(`I56191` 등)였고 분류명 텍스트 매칭으로 임시 우회했다.
   실측 확정: **카페=`I21201`, 독서실/스터디 카페=`R10202`**. 코드가 확정되니 (a) 코드 기반 확정 분류로
   승격하고 (b) `indsSclsCd` **서버측 필터**로 대상 업종만 페이지네이션 — 광화문 1.5km 상가 6,831건 중
   카페 수백뿐이라, 전체를 받아 클라에서 거르던 낭비(호출량 수십 배)를 없앴다.

**해결**: 실 API를 여러 지점(광화문·노량진 학원가)에서 프로브해 봉투·타입·코드·서버필터 동작을 4종 실측
확정 → `StoreApiResponse`/`StoreRecord`/`StoreCategoryMapper`/`SmallBusinessStoreApiClient`/
`StoreIngestionService` 정정, 테스트 픽스처를 **추정 응답 → 실측 응답**으로 재작성(14 green).

**핵심 학습 포인트**:
1. **"같은 포털이니 응답 형태도 같겠지"는 검증 없이는 가정일 뿐.** 도서관 표준 API와 상권 API(sdsc2)는
   같은 data.go.kr이지만 봉투가 다르다(래퍼 유무). 표준데이터 계열과 기관 개별 오픈API는 스캐폴드가 다르다.
2. **파싱 실패는 예외가 아니라 "조용한 0건"으로 샌다.** 봉투 불일치는 역직렬화 null → 빈 결과라 배포까지
   무증상. 인제스천/외부계약은 **실호출 스모크(최소 1건 파싱 확인)** 없이는 green이 무의미(TS-004의 "모킹은
   우리 로직만 검증하고 외부 계약은 가린다"와 동형).
3. **미검증 스캐폴드는 "미검증"을 코드·ADR에 명시하고, 승인/해금 즉시 실호출로 확증**한다. 추상화
   (`StoreApiClient`/`StorePage`)를 잘 끼워둔 덕에 계약 정정이 클라이언트/서비스 국소 수정으로 끝났다(호출부 무영향).

## TS-027 — 별개 포털(safetydata) API는 별개 키 + 별개 응답 봉투다(무더위쉼터)

**상황**: 무더위쉼터 전국 데이터는 오랫동안 "외부 승인 대기" 블로커였다. 원인 정리(2026-07-10 실측):
- data.go.kr에는 무더위쉼터 표준 오픈API가 **없다**(후보 엔드포인트 전부 `resultCode 12`/`-3`/미등록) — 표준데이터가
  재난안전데이터공유플랫폼 **safetydata.go.kr**의 `DSSP-IF-10942`로 이관·폐기됐다.
- safetydata는 data.go.kr과 **완전히 별개 포털**이라 **회원가입·활용신청·서비스키가 전부 별개**다. data.go.kr
  공용키(datago)로 safetydata를 호출하면 `resultCode 30 (등록되지 않은 서비스키)`.

**해결**: 사용자가 safetydata.go.kr에서 발급받은 **전용 서비스키**로 실호출 → `resultCode 00`, 전국 `totalCount=60,297`.
키는 `.local/safetydata.env`(gitignore)에만 저장(규칙 D), 프로덕션엔 ECS RunTask env 오버라이드로만 주입(상시 배선 X).

**계약 함정(코드 前 실호출로 확인 — TS-026 교훈 적용)**: safetydata V2 응답 봉투가 data.go.kr 표준과 **다르다**.
- data.go.kr 표준: `{response:{header, body:{items:[...], totalCount}}}` (도서관 API).
- **safetydata V2**: `{header:{resultCode,resultMsg}, totalCount, pageNo, numOfRows, body:[...]}` — `response` 래퍼 **없음**,
  `header`·페이지네이션이 **최상위**, `body`는 `items` 없이 **레코드 배열 그 자체**.
- 필드 키가 **대문자 스네이크**(`RSTR_NM`·`LA`·`LO`·`RSTR_FCLTY_NO`·`COLR_HOLD_ARCNDTN`) → record에 `@JsonProperty` 매핑 필수.
  좌표는 TM(XCORD/YCORD=null)이 아니라 **WGS84 LA/LO(숫자)** 를 쓴다.

**핵심 학습 포인트**:
1. **"공공데이터 = data.go.kr"이 아니다.** 재난·안전 도메인은 safetydata.go.kr(행안부), 지자체는 각 열린데이터광장 등
   **포털마다 키·인증·응답 규격이 다르다.** "승인 대기"의 진짜 원인이 "잘못된 포털에 신청"일 수 있으니 **데이터 출처 포털을
   먼저 확정**한다(무더위쉼터는 data.go.kr이 아니라 safetydata).
2. **새 외부 API는 계약을 코드보다 먼저 실호출로 고정한다(TS-026 재확인).** 같은 "표준 오픈API"처럼 보여도 포털이 다르면
   봉투가 다르다 — 도서관 클라이언트를 그대로 복붙했으면 `response` 래퍼 null로 전량 0건이 됐을 것이다.
3. **전량 스냅샷 soft-delete는 "수집 완료"를 게이트로 둔다.** 6만건 페이지네이션 중 오류로 끊긴 부분 스냅샷으로
   deactivateStale을 켜면 안 덮인 멀쩡한 행을 지운다 → 첫 페이지 totalCount 대비 실제 수집량이 채워졌을 때만 적용.

**§후속(2026-07-10) — IP 제한(resultCode 32)로 직접 API 경로가 ECS에서 막힘 → CSV 스냅샷 우회**:
배포 후 ECS one-off로 직접 API 적재(`--ingest.source=shelter`)를 돌리자 **`resultCode=32 UNREGISTERED IP ERROR`**.
safetydata 키가 **발급 시점의 등록 IP(사용자 로컬)에 잠겨** 있어, 로컬(등록 IP)에서는 되지만 **AWS Fargate egress
IP에서는 거부**된다. 구조상: RDS가 프라이빗이라 적재는 반드시 VPC 내(ECS)에서 하고, ECS의 나가는 IP는 유동(assignPublicIp
ephemeral)이며 NAT 게이트웨이는 비용상 배제 → **이 IP 잠금 키로는 직접 API 경로를 ECS에서 쓸 수 없다.**
- **방어 설계 적중**: `complete` 게이트가 fetched=0을 감지해 deactivateStale을 건너뛰어 기존 데이터를 지키지 않았다면
  전체 쉼터가 날아갔을 것이다(교훈 3의 게이트가 정확히 이 사고를 막았다).
- **우회(원래 의도된 CSV 스냅샷 경로)**: 등록 IP(로컬)에서 전량 다운로드 → `SourceSpec.COOLING_SHELTER` 별칭과 일치하는
  헤더의 CSV 생성 → GitHub Release 자산 업로드 → ECS가 **기존 CSV 파이프라인**(`--ingest.source=cooling_shelter --ingest.url=`)으로
  적재. `SourceSpec.COOLING_SHELTER`에 safetydata 영문 헤더 별칭(RSTR_*·LA/LO)과 이중헤더 처리가 이미 있던 게 이 경로용이었다.
- **학습**: **IP 잠금 외부 API + 프라이빗 DB + 이동 IP 실행환경**은 직접 호출이 성립하지 않는다. "데이터를 가져오는 곳(등록
  IP)"과 "DB에 쓰는 곳(VPC)"이 다르면, 등록 IP에서 스냅샷을 떠서 VPC로 실어 나른다(파일/릴리즈). 키 IP 잠금 여부를 도입
  전에 확인할 것. IP 해제(allow-all)나 고정 egress(EIP/NAT)를 얻으면 직접 API + 자동 동기화가 가능해진다.
