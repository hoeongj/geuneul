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
- **면접 어필 포인트:** ① 프레임워크 2개(Testcontainers/Spring TestContext)의 **수명주기 경계 충돌**을 예외 패턴("첫 클래스만 통과")으로 좁혀낸 진단 과정. ② 싱글턴 컨테이너 패턴과 컨텍스트 캐싱의 원리 이해. ③ "로컬에서 재현 안 되는 CI 전용 실패"를 로그만으로 원인 규명.
- **관련:** `AbstractIntegrationTest.java` / PR #3

### TS-003 · 2026-07-02 — 부팅 불가 이미지 배포 → 크래시루프 백오프로 5시간 수렴 지연 (배포 신뢰성 2계층 사고)
- **상황/증상:** 핫픽스(PR #7) 배포의 GitHub Actions가 `{"state":"TIMEOUT","reason":"Waiter has timed out"}`으로 실패 표시. 그런데 라이브는 200이었고, ECS 이벤트를 보니 배포는 **워크플로우 실패 판정 후 ~4.5시간 뒤(23:04) 자체적으로 완료**됨(false-negative). 배포 생성(18:15)→완료(23:04) 사이 이벤트에 "health checks failed → stop → start" 사이클 다수.
- **원인 분석 (2계층):**
  1. **프로세스 실패(선행 원인):** 직전 PR #6을 **CI 빨간 상태로 머지** — 자동화 스크립트가 `gh run watch | tail`의 파이프 종료코드(=tail의 0)를 CI 결과로 오판. 그 이미지(Boot 4 `RestClient.Builder` 빈 부재)는 컨텍스트 생성 실패로 **부팅 자체가 불가**.
  2. **시스템 동작(증폭 요인):** 부팅 불가 이미지의 태스크가 크래시루프 → ECS가 서비스 레벨 **런치 백오프**를 걸어 후속(정상 핫픽스) 배포의 태스크 스케줄링까지 수 시간 지연. 서킷브레이커 미설정이라 자동 롤백도 없었음.
- **해결 과정:** ① 프로세스 — 머지 게이트를 `PIPESTATUS`가 아닌 **변수 캡처한 실제 exit code**로 판정하도록 교정(green 확인 후에만 merge). ② 시스템 — ECS **deployment circuit breaker + 자동 롤백** 활성화(terraform in-place 적용, 무중단). 이제 부팅 불가 이미지는 몇 분 내 실패 판정→직전 버전 롤백되어 백오프가 누적되지 않는다.
- **결과:** 서비스는 전 과정에서 무중단(롤링이 기존 태스크 유지). 이후 배포부터 "깨진 이미지 = 즉시 롤백 + 정직한 실패 신호" 보장.
- **면접 어필 포인트:** ① 배포 사고를 **프로세스/시스템 2계층으로 분해**해 각각 교정(사람 실수 방지 장치 + 인프라 안전장치). ② ECS 배포 상태 머신(크래시루프→런치 백오프→서킷브레이커) 이해. ③ CI/CD 파이프라인의 **false-negative 신호** 문제 인지(워크플로우 결과 ≠ 실제 수렴 상태).
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
- **면접 어필 포인트:** ① "exitCode 0인데 결과 0"이라는 조용한 실패를 로그 패턴으로 근본까지 추적. ② **메이저 프레임워크 이전(Jackson 2→3)의 은닉된 파괴**를 실제 사고로 경험·이해. ③ **모킹의 함정** — 외부 의존을 페이크로 대체하면 "그 의존과의 실제 계약(역직렬화)"이 미검증으로 남는다는 교훈, 그리고 그걸 MockRestServiceServer(실 변환기 경유)로 메운 것.
- **관련:** `KakaoGeocodingClient.java`, `KakaoGeocodingClientTest.java`, ECS task 29da1b2f 로그

### TS-005 · 2026-07-02 — 서킷브레이커가 "정상" 이미지를 오롤백: 느린 부팅(93s) vs 헬스체크 유예(120s)
- **상황/증상:** Jackson 수정(PR #10) 배포가 `Deployment ... rolled back by the deployment circuit breaker`로 실패. CI(실 PostGIS 컨텍스트 로드 포함)는 green이었고 이미지는 정상 부팅 가능한데도 서비스가 **직전(미수정) 이미지로 롤백**됨. #8·#9 배포는 같은 설정으로 성공했었음(플래키).
- **원인 분석:** ECS 이벤트 + `Started GeuneulApplication in 93.704 seconds` 로그가 단서. **0.25 vCPU에서 JVM+Spring 부팅이 ~93초**인데 ALB 대상그룹 헬스체크는 30초 간격·2회 성공 필요라 태스크가 "정상"으로 등록되기까지 ~150초 필요. 그런데 `health_check_grace_period_seconds=120`이라 120초 후부터 ECS가 헬스 실패를 집계 → 부팅 완료 전 태스크를 unhealthy로 죽임 → 실패 태스크 누적 → **서킷브레이커(TS-003에서 켠 안전장치)가 트립해 롤백**. 즉 TS-003의 안전장치가, 유예가 부팅보다 짧아 **역으로 정상 배포를 잡아먹은** 케이스. #8/#9가 통과한 건 부팅 시간이 임계값 근처라 운에 좌우됐기 때문.
- **해결 과정:** `health_check_grace_period_seconds` 120→**240초**(부팅 93초의 ~2.5배 여유)로 terraform in-place 적용(무중단) → 수정 이미지 재배포(workflow_dispatch). 서비스가 새 이미지로 정상 안정화. 근본적으로는 **0.25 vCPU가 JVM 부팅에 빠듯**한 것이므로, 트래픽이 붙으면 task_cpu 512로 올려 부팅을 단축하는 게 정석(지금은 비용 우선 + 저트래픽이라 유예로 해결).
- **결과:** 배포 안정화. "서킷브레이커=안전장치"가 잘못 튜닝되면 **가용성 저하 도구가 될 수 있음**을 실측으로 이해.
- **면접 어필 포인트:** ① 안전장치(서킷브레이커)의 **오탐(false rollback)** 을 부팅시간 vs 유예 관점으로 규명 — 단순 "실패"가 아니라 임계값 튜닝 문제. ② ALB 헬스체크(interval·threshold)·ECS grace·서킷브레이커의 **상호작용 타이밍** 이해. ③ 근본(부팅 지연=CPU) vs 즉효(유예 확대)를 구분하고 비용 맥락에서 선택.
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
- **면접 어필 포인트:** ① **빌드 툴체인 전환기의 통합 함정**(Next 16 Turbopack 기본화 vs webpack 기반 플러그인)을 이해하고 "안정 경로=webpack 고정 + Turbopack 은 실험이라 배제"라는 근거 있는 선택. ② **신규 메이저(ESLint 10) 러그**를 진단하고 생태계 호환선(9.x)으로 내려 안정화 — 무작정 최신이 아니라 "동작하는 최신"을 고름. ③ 두 문제 모두 "왜 실패했나"를 프레임워크 릴리스 맥락까지 짚어 근본 파악.
- **관련:** `frontend/next.config.ts`(webpack 고정·tracing root), `frontend/package.json`(scripts·eslint@^9), `frontend/lib/hooks.ts`, `frontend/eslint.config.mjs`

### TS-007 · 2026-07-03 — Vercel git 자동배포만 실패(ENOENT .next): 로컬 경고 억제 설정이 배포 파이프라인을 깨뜨림
- **상황/증상:** CLI 배포(`vercel deploy --prod`)는 성공하는데, **rootDirectory=frontend + GitHub 연결로 전환한 뒤의 git 자동배포만 Error.** 빌드 로그는 `✓ Compiled successfully`·전 라우트 생성까지 정상 — 실패는 빌드 뒤 단계였고 로그에 사유가 안 남음. `vercel redeploy`로 재현하자 실제 에러가 표면화: `ENOENT: no such file or directory, lstat '/vercel/path0/.next/package.json'`.
- **원인 분석:** 로컬에서 홈 디렉터리의 stray `~/package-lock.json` 때문에 Next가 워크스페이스 루트를 오탐하는 **경고를 억제하려고** 넣은 `outputFileTracingRoot: process.cwd()`가 원인. Vercel git 빌드는 **repo 루트를 clone(`/vercel/path0`) 후 rootDirectory(frontend) 기준으로 빌드·출력 수집**을 하는데, tracing root를 코드가 임의로 고정하면 출력(.next) 위치 계산이 어긋나 repo 루트에서 `.next`를 찾다 ENOENT. CLI 배포가 됐던 건 frontend/ 를 프로젝트 루트로 직접 업로드해 두 경로가 우연히 일치했기 때문.
- **해결 과정:** `...(process.env.VERCEL ? {} : { outputFileTracingRoot: process.cwd() })` — Vercel 환경에선 플랫폼 네이티브 처리에 맡기고, 로컬만 stray-lockfile 회피 유지. push → git 자동배포 Ready(50s) 확인, 라이브 alias가 새 빌드로 전환된 것까지 검증.
- **결과:** `git push main → Vercel 프로덕션 자동배포` 파이프라인 정상화. 이후 배포는 무개입.
- **면접 어필 포인트:** ① "로컬 편의를 위한 설정이 **호스팅 플랫폼의 빌드 가정**(rootDirectory 기반 출력 수집)을 깨뜨릴 수 있다"는 사례 — 환경변수 가드(`process.env.VERCEL`)로 환경별 분기. ② 성공 로그 뒤에 숨은 배포 실패를 `redeploy` 재현으로 표면화시켜 진단한 과정. ③ CLI 배포와 git 배포의 **빌드 컨텍스트 차이**(업로드 루트 vs clone 루트)를 이해.
- **관련:** `frontend/next.config.ts`, Vercel project geuneul(rootDirectory=frontend, git connected), 커밋 a0bc0f1
