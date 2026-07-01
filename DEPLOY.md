# 배포 (AWS — 회사 실사용 스택, IaC + push→자동배포)

> **ECS Fargate**(관리형 컨테이너) + **RDS PostgreSQL(PostGIS)** + **Terraform**(IaC) + **GitHub Actions OIDC**(키 없는 배포) + **ECR** + **ALB**.
> 원칙: mp가 자가호스팅 k3s를 증명 → 그늘은 **회사들이 실제 쓰는 AWS**로 그 갭을 보완. EKS($73/월)는 배제(mp가 k8s 이미 증명). 비용은 $200 크레딧 + RDS 프리티어 + ECS 무료 control plane + NAT 없음으로 최소화(상시 비용은 ALB ~$16/월 정도).
>
> ⚠️ 모든 비밀(DB 비번·키)은 SSM/환경변수로만. 레포 커밋 금지(규칙 D). `terraform.tfvars`·`*.tfstate`는 gitignore됨.

## 사전 준비
- AWS 계정(신규면 $200 크레딧), AWS CLI 로그인(`aws configure` 또는 SSO).
- Terraform 설치(`brew install terraform`).

## 1. 인프라 프로비저닝 (Terraform)
```bash
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars   # db_password 채우기 (gitignore됨)
terraform init
terraform plan       # 생성될 리소스 리뷰 (VPC/RDS/ECS/ALB/ECR/IAM)
terraform apply
```
apply 후 output 확인:
- `alb_url` — 공개 URL
- `ecr_repository_url` — 이미지 push 대상
- `github_actions_role_arn` — 다음 단계에 사용

> RDS는 PostGIS를 Flyway `V1__enable_postgis.sql`(CREATE EXTENSION)로 활성화하므로 별도 작업 불필요.

## 2. GitHub → AWS 배포 연결 (OIDC, 키 없음)
1. GitHub 레포 **Settings → Secrets and variables → Actions** 에 시크릿 추가:
   - `AWS_ROLE_ARN` = Terraform output `github_actions_role_arn`
2. 끝. (액세스키를 저장하지 않는다 — OIDC로 그때그때 단기 자격증명 발급.)

## 3. 첫 배포 (이미지 채우기)
`terraform apply` 직후 ECR은 비어있어 ECS 태스크가 아직 못 뜬다. 이미지를 한 번 채우면 서비스가 안정화된다:
- `main`에 `backend/**` 변경을 push하거나, Actions에서 **Deploy (AWS ECS)** 를 `workflow_dispatch`로 수동 실행.
- 워크플로우가 이미지 빌드 → ECR push → ECS 태스크 리비전 갱신 → 롤링 배포.

## 4. 확인 & 이후
- `http://<alb_url>/actuator/health` → `{"status":"UP"}` (Flyway 마이그레이션 성공 = PostGIS·GiST 생성됨)
- `http://<alb_url>/swagger-ui.html`
- **이후 `main` push마다 자동 재배포.** (CI(test)는 별도 `ci.yml`, 배포는 `deploy.yml`.)

## 비용 메모
- 상시: **ALB ~$16/월**(안정적 URL 대가). RDS/EC2 컴퓨팅은 프리티어, ECS control plane 무료, SSM/ECR 사실상 무료. → $200 크레딧으로 수개월~1년 커버.
- 내리기: `terraform destroy` (RDS `skip_final_snapshot=true`라 즉시 삭제).
- 심화(P4): ECS Service Auto Scaling(HPA 상당)을 k6 부하테스트와 함께 추가 → mp가 안 한 새 DevOps 신호.

## HTTPS/도메인(선택, 나중)
ACM 인증서 + Route53(또는 외부 도메인) + ALB 443 리스너로 확장. 지금은 HTTP로 파이프라인부터 검증.
