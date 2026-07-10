---
description: 그늘(geuneul) 다음 세션 시작 — 현황 검증 후 심화+additive 백로그를 순서대로 실행
---

너는 이 레포의 시니어 백엔드/풀스택 엔지니어다. 새 세션을 시작한다. 아래를 **순서대로** 수행하라.

> **이번 세션의 목표(사용자 지시 2026-07-10)**: `docs/BACKLOG.md`의 **심화 + additive 항목을 하나도 빠짐없이 전부** 착수·완료한다. 외부 승인 블로커는 0건이다(상권 #56·쉼터 #58 해소). 남은 건 전부 이 백로그다.

## 1) 컨텍스트 로드 (읽기)
- **`docs/BACKLOG.md`** — 이번 세션 실행 대상(A1~A9 additive, B1~B2 심화). **완료 체크리스트 포함.**
- `HANDOFF.md` 의 **"▶ 세션 인계"**(현황·rev·데이터 카운트) + 하단 백로그/⏳ 섹션
- `CLAUDE.md` 의 **§0 작업 원칙** + **🔴 필수 워크플로우 규칙 A~D**
- `TROUBLESHOOTING.md` 의 **TS-025**(머지 전 `gh pr checks` 필수)·**TS-026/027**(외부 API 계약은 코드 前 실호출 검증·IP 잠금 우회)·**TS-016/009**(네이티브 프로젝션 Instant·로컬 IT skip≠통과)
- 메모리 인덱스(`MEMORY.md`) 관련 항목

## 2) 상태 검증 (실행)
```bash
cd /Users/seongju/geuneul
git checkout main && git pull origin main --quiet
git status --short                                   # 클린이어야 함
git config user.email                                # seongjuice999@gmail.com 확인
gh pr list --state open                              # 열린 PR 확인
git log main --format="%B" | grep -ciE "^co-authored-by:.*(claude|anthropic)"   # 0이어야 함
# 라이브 헬스
/usr/bin/curl -s -o /dev/null -w "API %{http_code}\n" https://d2pedv974beobb.cloudfront.net/actuator/health
/usr/bin/curl -s -o /dev/null -w "App %{http_code}\n" https://geuneul.vercel.app
# 데이터 라이브 스팟체크(쉼터·카페·스터디)
for c in COOLING_SHELTER CAFE STUDY_CAFE; do
  /usr/bin/curl -s -G "https://d2pedv974beobb.cloudfront.net/places" \
    --data-urlencode "lat=37.5665" --data-urlencode "lng=126.9780" --data-urlencode "radius=2000" \
    --data-urlencode "category=$c" -o /dev/null -w "$c %{http_code}\n"
done
```

## 3) 백로그 실행 (핵심 — `docs/BACKLOG.md` 순서대로)
**A(additive)부터 B(심화)까지 전 항목**을 착수한다. 각 항목 = 원칙적으로 1 PR.
- **A1→A9**: 시설 comfort SQL 통합 · verified→trust · 쉼터 air_conditioned 백필 · 급증 SSE 프론트 · popular-times 히트맵 · 커뮤니티 최소 UI · bookmarks · 상권 전국 확장 · (조건부) Fargate cpu 512
- **B1 알림 · B2 루트**: **ADR 선행**(웹 2026 트렌드 확인 → 전달방식/외부 경로API 결정 → `docs/adr/` 기록) 후 구현.
- 각 항목의 무엇/왜/파일/수용기준/함정은 `docs/BACKLOG.md`에 상세히 있다. **그 문서를 항목별로 다시 열어 근거대로 진행**한다.
- **중간 사용자 입력 지점**(BACKLOG 명시): B2 외부 경로 API 키·B1 Web Push VAPID·A3/A8 대량 재적재. 도달 시 **한 줄 제안 후 진행**(막히면 다음 항목으로 넘어가 병렬 진행).

## 4) 규율 (절대 지킬 것)
- **머지 전 반드시 `gh pr checks <N>`로 "Backend (Gradle, JDK 21)" pass 눈으로 확인**(TS-025 — `gh run watch` EXIT만 믿지 말 것).
- 기능 브랜치 + PR + CI green 후 머지. **로컬 main 직접 작업 금지**, 특정 파일만 스테이징.
- 커밋 신원 `ghdtjdwn`/`seongjuice999@gmail.com`. **커밋에 `Co-Authored-By: Claude` 트레일러 금지**(§A).
- 새 외부 API는 **코드 前 실호출로 계약 검증**(TS-026/027 — 봉투·필드·타입·IP 잠금). 비밀·개인정보(키·IP)는 `.local`·env로만, 문서·커밋 금지(§D).
- 의사결정은 웹 2026 트렌드 확인 + `WORKLOG.md`(무엇/왜/대안/근거), 사고는 `TROUBLESHOOTING.md`, 아키텍처 결정은 `docs/adr/`.
- 네이티브 프로젝션 시각컬럼=`Instant`+DTO UTC 부착(TS-016). 로컬 IT는 skip될 수 있음 → **SKIP≠통과, CI가 유일 게이트**(TS-009).
- 간판(지리·스코어링) 우선, **커뮤니티는 살**(리뷰앱화 금지 §0-9). 침수/위험 표현 **공포 조장 금지**(§6).

## 5) 완료마다
- `docs/BACKLOG.md`의 **완료 체크리스트 체크** + `HANDOFF.md`(현황·데이터·rev) 갱신 + WORKLOG/ADR/TROUBLESHOOTING 기록.

## 6) 마무리
전 항목 완료(또는 사용자 입력 대기로 블록) 시, **완료 목록 + 남은(블록된) 항목 + 다음 추천**을 사용자에게 보고한다. `$ARGUMENTS`
