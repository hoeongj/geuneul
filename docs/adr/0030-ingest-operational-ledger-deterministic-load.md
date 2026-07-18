# ADR-0030 — 인제스천 운영 원장 + 결정적 공간 부하 입력

- 상태: 승인·운영 적용 완료(로컬 실 PostGIS·30만 건 부하 검증, 2026-07-18)
- 관련: ADR-0002(멱등 upsert), ADR-0011(주기 동기화), ADR-0012(k6/EXPLAIN),
  ADR-0014(로컬 관측성), `Flyway V20`, `perf/k6/spatial_load.js`

## 문제(Context)

공공데이터 수집은 ECS one-off 태스크라 종료 뒤 프로세스 메트릭이 사라진다. 기존 로그 요약만으로는 마지막 완전 성공
시각, 부분 수집, 재실행 계보, 지오코딩 실패·백필 건수를 상시 API 프로세스에서 조회할 수 없었다. k6 공간 부하는
`Math.random()` 입력과 사람용 콘솔 출력에 의존해 같은 조건의 회귀 비교와 결과 자동 수집도 어려웠다.

## 결정(Decision)

### 1. 실행 단위 DB 원장과 집계형 dead letter

V20이 `ingest_runs`와 `ingest_dead_letters`를 만든다. `IngestionRunner`는 advisory lock 전 `RUNNING`을 만들고,
종료 시 `SUCCEEDED | PARTIAL | FAILED | SKIPPED`와 기존 수집 요약 카운터를 기록한다. `trigger_type`은
`MANUAL | SCHEDULED | BACKFILL`, `retry_of`는 같은 source·같은 `input_fingerprint`의 이전 종료 실행만 가리킨다.
fingerprint는 contract version과 수집 옵션을 포함한다. 로컬 CSV는 경로가 아니라 파일 바이트 SHA-256을, 원격 CSV는
필수 기대 SHA-256을 사용하고 다운로드 직후 실제 바이트와 대조한다. 원문 경로·URL은 원장에 저장하지 않는다.

dead letter는 `GEOCODE_FAILED`, `RECORD_SKIPPED`, `SOURCE_INCOMPLETE`, `RUN_FAILED`별 **건수만** 저장한다.
원본 행, 주소, 외부 API 응답, 예외 메시지는 저장하지 않는다. 성공한 재실행은 재귀 CTE의 매 단계에서 다음 조상 행의
fingerprint를 다시 비교한 뒤 같은 입력 계보의 미해결 집계만 해결 처리한다. 멱등 upsert가 실제 복구 수단이고 원장은
그 계보와 결과를 설명한다.

도서관·무더위쉼터·상권 API는 `NODATA` 계약만 정상 빈 결과로 구분하고, HTTP·파싱·비정상 result code·
`reportedTotal` 미도달을 전용 값 비노출 예외로 실패시킨다. 전량 snapshot 소스는 모든 페이지를 DB 변경 전에
검증하며, 식별 불가능 행이 하나라도 있으면 `PARTIAL`로 기록하고 stale 비활성화를 건너뛴다. 따라서 부분 snapshot이
완전 성공·freshness로 기록되거나 기존 장소를 삭제하는 경로를 닫는다.

부분/실패 실행은 같은 source·동일 입력 조건으로 다시 실행한다. 예를 들어 로컬 도서관 backfill은 다음처럼 계보를
명시한다(실제 UUID와 로컬 설정을 사용).

```bash
cd backend
./gradlew bootRun --args='--ingest.source=library --ingest.trigger=backfill --ingest.retry-of=<UUID> --ingest.deactivate-stale=true --ingest.exit-after=true'
```

### 2. 종료 프로세스 대신 상시 API가 원장을 읽어 메트릭화

`IngestMetrics`는 고정된 네 source를 10초 snapshot으로 읽어 Prometheus gauge를 노출한다. 한 scrape에서 SQL은
한 번만 실행하고, DB 조회 실패 시 이전 snapshot으로 강등하면서 `geuneul.ingest.ledger.readable=0`을 낸다.
freshness, 완전 성공 이력 유무, 최신 one-hot 상태, 미해결 레코드, 최신 upsert/backfill/시간, retry 누계를 전용
Grafana 대시보드에 표시한다.

로컬 Prometheus 규칙은 원장 조회 실패, 최신 `FAILED/PARTIAL`, 미해결 dead letter, 월간 도서관 완전 성공 35일 초과를
평가한다. Alertmanager 전송은 구성하지 않고 Prometheus Alerts와 Grafana 패널에서만 보인다. 기존 ADR-0014대로
프로덕션 `/actuator/prometheus`는 기본 미노출이며 이번 변경으로 운영 관측 백엔드를 새로 만들지 않는다.

### 3. DB snapshot과 k6 요청 입력을 seed로 고정하고 JSON summary를 남김

합성 데이터는 `data_seed`로 좌표를 고정하고 source key 해시로 제보 대상·타입·freshness offset을 정한다. 수도권 여부는
행당 한 번만 평가해 경도와 위도가 서로 다른 분포를 선택하지 않게 한다. 생성 뒤 places와 reports fingerprint를 출력한다.
`RUN_SEED` 기본값과 `(seed, __VU, __ITER)` 혼합값은 k6 `randomSeed()`에 매 iteration 적용한다. 같은 VU/iteration은
항상 같은 좌표·카테고리·반경·시나리오를 생성한다. `handleSummary()`는 schema version, 요청/DB seed, DB fingerprint,
VU/stages, 대상 종류, p90/p95/p99, 요청/실패/threshold 결과를 `perf/results/spatial-summary.json`으로 쓴다. 기본 대상은
loopback이며 원격은 `ALLOW_REMOTE_LOAD=true` 없이는 init 단계에서 차단한다.

이 결정은 k6의 공식 [`randomSeed`](https://grafana.com/docs/k6/latest/javascript-api/k6/random-seed/)와
[`handleSummary`](https://grafana.com/docs/k6/latest/results-output/end-of-test/custom-summary/) 계약을 따른다.

## 비교 경계(Same-condition p95 boundary)

요청 seed와 DB seed/fingerprint가 같아도 p95 비교가 유효하려면 `PEAK_VUS`와 stages, 애플리케이션 빌드, 인덱스,
PostgreSQL/PostGIS 버전, 머신/컨테이너 자원, k6 버전도 같아야 한다. JSON은 두 seed·DB fingerprint·VU·stages·target
origin을 기록하지만 빌드 SHA와 하드웨어 fingerprint까지 자동 증명하지 않는다. 따라서 이 조건 중 하나라도 다르면 p95를
회귀 수치로 직접 비교하지 않고 별도 환경 결과로 취급한다. 스케줄링과 시스템 잡음 때문에 같은 조건에서도 latency와
총 iteration 수 자체는 결정적이지 않다.

## 로컬 검증 근거(2026-07-18)

- Testcontainers를 1.20.6에서 2.0.5로 올려 Docker Engine 29의 API 최소 버전과 맞췄다. 전체 백엔드 게이트는
  **506 tests, skip 0, failures/errors 0**, JaCoCo line **2,785/3,208(86.8%)**로 70% 하한을 통과했다.
- 기존 `prom/prometheus:v3.2.1` 이미지의 promtool로 `ingest-alerts.yml` 4개 규칙과 전체 Prometheus 설정을 검사해
  모두 통과했다. 네트워크는 끄고 저장소 디렉터리를 읽기 전용으로 마운트했다.
- 격리된 로컬 환경은 Colima 2 vCPU/4GiB, Docker 29.5.2(aarch64), amd64 `postgis/postgis:16-3.4` QEMU 실행,
  k6 2.1.0이다. Flyway V1~V20, places 300,010(합성 300,000), 활성 합성 reports 10,000을 사용했다.
  `data_seed=0.20260718`의 fingerprint는 places `f45d466de919f4ed573997ce5e7c7d03`, reports
  `37f362ee091648caea99535eefe68aa0`이고 수도권 bbox 비율은 70.17%였다.
- `RUN_SEED=20260718`, peak 4 VU, stages 20s/40s/10s, loopback에서 672/672 요청 성공, 실패율 0%, 9.55 req/s.
  p95는 반경 1,349.02ms, kNN 73.31ms, bounds 357.41ms, 추천 918.64ms였고 모든 p95/p99 임계를 통과했다.
  추천의 날씨 키는 비워 외부 호출 없이 graceful fallback 경로를 사용했다.
- EXPLAIN은 반경과 추천 공간 선필터의 `idx_places_geom_geography` Bitmap, kNN의 GiST KNN index scan을 확인했다.
  밀집 bounds는 `LIMIT 100` 조기종료가 유리해 `idx_places_active`를 선택했다. 희소 bounds의 geometry GiST 근거는
  ADR-0012의 기존 실측을 유지한다.

## 운영 적용 근거(2026-07-18)

- [PR #118](https://github.com/ghdtjdwn/geuneul/pull/118)을 merge commit
  `9639c3581f617af4d2a0e37f4849301a0f35c08c`로 병합했다. Backend 실 PostGIS 게이트와 gitleaks, Vercel
  Production 배포가 성공했고 공개 웹 응답은 HTTP 200이었다.
- 첫 AWS 배포는 저장소 이전 뒤 운영 IAM trust의 OIDC subject가 이전 저장소에 남아 있어 이미지 빌드 전에
  `AssumeRoleWithWebIdentity`가 거부됐다. 기존 ECS task는 계속 정상 응답해 중단이나 불완전 배포는 없었다.
  저장된 targeted plan이 `aws_iam_role.github_actions`의 정확 일치 subject 1건만 바꾸며 add/destroy가 0임을
  확인한 뒤 적용했다.
- 실패 job 재실행은 테스트, OIDC, ECR 로그인, 이미지 빌드·푸시와 ECS rolling deploy를 모두 통과했다.
  task definition revision 76의 실행 이미지는 merge SHA와 일치했고, CloudWatch의 값 비노출 로그에서 Flyway V20
  1건 적용과 애플리케이션 시작을 확인했다. ECS rollout은 desired/running 1, pending 0으로 완료됐고 공개
  `/actuator/health`는 `UP`이었다. [배포 run](https://github.com/ghdtjdwn/geuneul/actions/runs/29647181251)은
  attempt 2에서 최종 성공했다.
- 애플리케이션 안정화 뒤 scheduler만 대상으로 한 저장 plan이 description과 container command의
  `--ingest.trigger=scheduled` 추가 1건 외에는 동일하고 add/change/destroy가 `0/1/0`임을 검사해 적용했다.
  운영 schedule은 `ENABLED`이며 선언한 command와 일치한다. 마지막 전체 `terraform plan -detailed-exitcode`는
  exit 0, `No changes`였다.
- 기존 IAM role과 scheduler의 in-place update만 수행해 새 유료 리소스를 만들지 않았다. 운영 서비스에 대한 부하
  테스트·성능 측정, 외부 공공 API 호출, one-off 데이터 적재는 수행하지 않았다.

## 검토한 대안(Alternatives)

- one-off 태스크에서 Prometheus push: Pushgateway와 수명주기 운영이 새로 필요해 기존 DB 원장보다 복잡하다.
- 원본 레코드 DLQ: 개별 재처리는 쉬우나 주소·외부 응답을 중복 보존해 개인정보·보안·retention 부담이 커진다.
- 로그 검색만 유지: freshness와 재실행 계보를 안정적으로 질의하거나 대시보드화하기 어렵다.
- HTTP·파싱 오류를 빈 결과로 계속 표현: 호출부는 정상 NODATA와 장애를 구분할 수 없어 거짓 성공과 stale 삭제를
  막을 수 없다. 전용 값 비노출 예외와 전량 선검증을 선택했다.
- URL·파일 경로만 retry fingerprint로 사용: 같은 위치의 내용 교체를 감지하지 못한다. content SHA-256을 쓰고 원격
  다운로드는 기대 digest를 필수 검증한다.
- 전역 PRNG를 init에서 한 번만 seed: VU 스케줄링과 iteration 배분 변화가 요청 조합을 흔든다.

## 결과와 한계(Consequences)

- 부분 수집과 백필을 source 단위 멱등 재실행으로 복구하고, retry chain 전체 해결 여부를 남길 수 있다.
- 원장은 월간/수동 실행의 작은 집계 행만 보존하고 원본 payload를 저장하지 않는다.
- CSV URL 자체의 불변성을 신뢰하지 않는다. 배포자는 스냅샷을 바꿀 때 새 SHA-256을 명시해야 하며, digest 불일치는
  원장 `FAILED` 후 DB mutation 없이 종료된다.
- **자동 retention은 아직 없다.** 현재 저빈도에서는 비용이 작지만 `ingest_runs`와 해결된 dead letter도 무기한
  증가한다. 보존기간 정책이나 실행 빈도가 확정되면 해결된 행부터 삭제/아카이브하는 후속 마이그레이션이 필요하다.
- 프로세스가 강제 종료되면 `RUNNING` 행이 남는다. 최신 상태와 freshness로 보이지만 자동 terminal 전환은 없다.
- 로컬 경보는 알림 전송이 아니라 가시성 자산이다. 프로덕션 alert routing/retention은 별도 승인·설계 대상이다.
- 운영 배포와 위 두 건의 제한된 Terraform in-place update는 완료했다. 프로덕션 부하·성능 측정, 외부 공공 API
  호출과 one-off 데이터 적재는 수행하지 않았다.
- 위 30만 건 결과는 향후 같은 fingerprint 회귀의 기준점이다. ADR-0012의 과거 결과와는 앱 변경·DB snapshot·k6 버전이
  달라 성능 개선률을 계산하지 않는다.
