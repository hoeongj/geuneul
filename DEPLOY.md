# 배포 (AWS — IaC + push 자동배포)

> **ECS Fargate**(관리형 컨테이너) + **RDS PostgreSQL(PostGIS)** + **Terraform**(IaC) + **GitHub Actions OIDC**(키 없는 배포) + **ECR** + **ALB** + **CloudFront**(HTTPS).
> 비용 원칙: $200 신규 크레딧 + RDS 프리티어 + ECS 무료 control plane + NAT 게이트웨이 없음(Fargate는 퍼블릭 서브넷, SG로 잠금)으로 최소화.
>
> ⚠️ 모든 비밀(DB 비번·키)은 SSM/환경변수로만. 레포 커밋 금지. `terraform.tfvars`·`*.tfstate`는 gitignore됨.

## 사전 준비
- AWS 계정(신규면 $200 크레딧), AWS CLI 로그인(`aws configure` 또는 SSO).
- Terraform 설치(`brew install terraform`).

## 1. 인프라 프로비저닝 (Terraform)
```bash
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars   # 필수 변수 전부 채우기 (gitignore됨 — db_password 외에도 OAuth/JWT/API 키 등, example 참고)
terraform init
terraform plan       # 생성될 리소스 리뷰 (VPC/RDS/ECS/ALB/ECR/IAM/ElastiCache/S3/EventBridge)
terraform apply
```
apply 후 output 확인:
- `alb_url` — 오리진 URL (공개 진입점은 CloudFront)
- `ecr_repository_url` — 이미지 push 대상
- `github_actions_role_arn` — 다음 단계에 사용

> RDS는 PostGIS를 Flyway `V1__enable_postgis.sql`(CREATE EXTENSION)로 활성화하므로 별도 작업 불필요.

## 2. GitHub → AWS 배포 연결 (OIDC, 키 없음)
1. GitHub 레포 **Settings → Secrets and variables → Actions** 에 시크릿 추가:
   - `AWS_ROLE_ARN` = Terraform output `github_actions_role_arn`
2. 끝. (액세스키를 저장하지 않는다 — OIDC로 그때그때 단기 자격증명 발급. 롤 trust는 `main` 브랜치 sub로 한정.)

## 3. 첫 배포 (이미지 채우기)
`terraform apply` 직후 ECR은 비어있어 ECS 태스크가 아직 못 뜬다. 이미지를 한 번 채우면 서비스가 안정화된다:
- `main`에 `backend/**` 변경을 push하거나, Actions에서 **Deploy (AWS ECS)** 를 `workflow_dispatch`로 수동 실행.
- 워크플로우가 테스트 게이트(실 PostGIS Testcontainers) → 이미지 빌드 → ECR push → ECS 태스크 리비전 갱신 → 롤링 배포.

## 4. 확인 & 이후
- `http://<alb_url>/actuator/health` → `{"status":"UP"}` (Flyway 마이그레이션 성공 = PostGIS·GiST 생성됨)
- `http://<alb_url>/swagger-ui.html`
- **이후 `main`에 `backend/**` 변경이 push될 때마다 자동 재배포.** (문서·인프라만 바뀐 push는 `deploy.yml`의 paths 필터로 배포를 트리거하지 않음. CI(test)는 별도 `ci.yml`.)

## 비용 메모
- 상시: **ALB ~$16/월 + Fargate 태스크(0.5 vCPU/1GB 상시 1개) ~$20/월**(Fargate는 프리티어 없음). RDS(db.t3.micro)는 프리티어, ECS control plane 무료, SSM/ECR/CloudFront(저트래픽) 사실상 무료.
- 오토스케일링(CPU 60% target-tracking, min1/max3) 스케일아웃 시 최대 ~$60/월 수준(부하 종료 후 원복).
- **전체 내리기(상시 비용 $0)**: `./infra/teardown.sh` — RDS 삭제보호 해제 + final 스냅샷 충돌 정리 + `terraform destroy`를 한 번에. 데이터 스냅샷은 부활용으로 남긴다(거의 무료). Vercel 프론트는 무료라 그대로 둬도 된다.
- **부활**: `cd infra/terraform && terraform apply` → 첫 배포 → 공공데이터 재적재(아래 '운영 인제스천'). CloudFront 도메인이 새로 발급되면 README 배지·Vercel `GEUNEUL_API_BASE`를 갱신한다.

## 운영 인제스천 (공공데이터 → 프로덕션 RDS)
RDS는 프라이빗 서브넷이라 로컬에서 직접 접속할 수 없다(의도된 보안 설계). 적재는 **같은 VPC 안의 ECS one-off task**로 실행한다 — 서비스와 동일한 태스크 정의(이미지·SSM 비밀·SG)를 재사용:

```bash
# 1) 데이터 스냅샷을 URL로 접근 가능하게 (GitHub Release 자산 권장 — 레포 비대화 방지 + 버저닝)
gh release create data-v1 shelters.csv toilets.csv --title "공공데이터 스냅샷 v1" --notes "무더위쉼터/공중화장실 표준데이터"

# 2) one-off task 실행 (다운로드→파싱→멱등 upsert→종료)
./infra/scripts/prod-ingest.sh cooling_shelter <shelters.csv 릴리즈 URL> UTF-8
# 공중화장실은 59,768행 전량 좌표 미제공 → 카카오 지오코딩 필수(ADR-0003).
# REST 키는 셸 환경변수로만 전달(스크립트가 태스크에 주입 — 레포 하드코딩 금지):
KAKAO_REST_API_KEY=<카카오 REST 키> ./infra/scripts/prod-ingest.sh public_toilet <toilets.csv 릴리즈 URL> MS949
```
멱등(ON CONFLICT upsert)이므로 재실행·데이터 갱신 모두 같은 명령이다. 도서관(오픈API 전량 수집)은 EventBridge Scheduler가 월 1회 무인 동기화한다(ADR-0011).

## HTTPS/도메인
공개 진입점은 **CloudFront 기본 도메인(무료 HTTPS)** — ALB(http)는 오리진으로만 쓴다(ADR-0015). 커스텀 도메인이 생기면 CloudFront에 CNAME+ACM(us-east-1)을 붙이거나 ALB 443 리스너로 전환한다.
