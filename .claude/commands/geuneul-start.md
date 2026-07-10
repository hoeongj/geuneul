---
description: 그늘(geuneul) 다음 세션 시작 — 현황 파악·상태 검증·작업 선택
---

너는 이 레포의 시니어 백엔드/풀스택 엔지니어다. 새 세션을 시작한다. 아래를 **순서대로** 수행하라.

## 1) 컨텍스트 로드 (읽기)
- `HANDOFF.md` 의 **"▶ 세션 인계"** + **"✅ 외부 승인 불필요 백로그"** + **"⏳ 외부 승인 대기"** 섹션
- `CLAUDE.md` 의 **§0 작업 원칙** + **🔴 필수 워크플로우 규칙 A~D**
- 최근 `TROUBLESHOOTING.md` 의 **TS-025**(머지 전 `gh pr checks` 필수), **TS-016/009**(네이티브 프로젝션 Instant·로컬 IT skip)
- 메모리 인덱스(`MEMORY.md`)의 관련 항목

## 2) 상태 검증 (실행)
```bash
cd /Users/seongju/geuneul
git checkout main && git pull origin main --quiet
git status --short                                   # 클린이어야 함
git config user.email                                # seongjuice999@gmail.com 확인
gh pr list --state open                              # 열린 PR 확인
gh run list --branch main --event push --limit 3 --json name,conclusion -q '.[] | "\(.name): \(.conclusion)"'
git log main --format="%B" | grep -ciE "^co-authored-by:.*(claude|anthropic)"   # 0이어야 함
# 라이브 헬스
/usr/bin/curl -s -o /dev/null -w "API %{http_code}\n" https://d2pedv974beobb.cloudfront.net/actuator/health
/usr/bin/curl -s -o /dev/null -w "App %{http_code}\n" https://geuneul.vercel.app
```

## 3) 외부 승인 2건 풀렸는지 확인 (실행)
남은 유일한 블로커. serviceKey는 `.local/datago.env`. 승인되면 resultCode가 바뀐다.
```bash
# 상권정보 카페/스터디카페(B553077) — 미승인이면 403, 승인되면 정상 응답
source .local/datago.env 2>/dev/null
/usr/bin/curl -s -o /dev/null -w "storeapi(B553077) %{http_code}\n" \
  "https://apis.data.go.kr/B553077/api/open/sdsc2/storeListInRadius?serviceKey=${DATA_GO_KR_SERVICE_KEY}&radius=500&cx=126.95&cy=37.51&type=json&numOfRows=1"
# 쉼터 전국(safetydata DSSP-IF-10942) — 전용 키 필요, 있으면 .local에서 확인
```
- **403/미승인이면** → 그 작업은 아직 불가. 사용자에게 승인 상태를 물어보고, 아래 4)의 승인 불필요 후속을 제안.
- **승인됐으면** → HANDOFF "⏳ 외부 승인 대기" 항목의 계획대로 착수(library 패턴 재사용, 계약 검증 → 멱등 적재).

## 4) 승인 불필요 후속 (additive, 바로 가능)
전 백로그 ①~⑨는 완료됐다. 남은 additive(핸드오프 기재):
- 급증 알림 **프론트 EventSource 구독**(`/alerts/stream`) + 지도 급증 배지
- **popular-times 히트맵 UI**(상세 화면, 백엔드 `/places/{id}/popular-times` 준비됨)
- 후기 **커뮤니티 최소 UI**(댓글/리액션 — 간판 안 가리게, §0-9)
- **verified → 유저 trust_score 연동**(현재는 제보 단위 뷰 가중만)
- **시설 comfort의 survival_score SQL 통합**(현재는 표시 등급만, 스코어드 쿼리에 feature 조인)
- 트래픽 붙으면 **Fargate task_cpu 512**(TS-005)

## 5) 규율 (절대 지킬 것)
- **머지 전 반드시 `gh pr checks <N>`로 "Backend (Gradle, JDK 21)" pass 눈으로 확인** (TS-025 — `gh run watch` EXIT만 믿지 말 것).
- 기능 브랜치 + PR + CI green 확인 후 머지. **로컬 main에서 직접 작업 금지**, 특정 파일만 스테이징.
- 커밋 신원 `hoengj`/`seongjuice999@gmail.com`. **커밋에 `Co-Authored-By: Claude` 트레일러 금지**(§A).
- 새 기능은 **착수 전 스코프 한 줄 제안**. 간판(지리공간·스코어링) 우선, 커뮤니티는 살(리뷰앱화 금지 §0-9).
- 의사결정은 웹으로 2026 트렌드 확인 + `WORKLOG.md`에 무엇/왜/대안/근거 기록. 사고는 `TROUBLESHOOTING.md`.
- 네이티브 쿼리 인터페이스 프로젝션에 시각 컬럼 있으면 `Instant`로 받고 DTO에서 UTC 부착(TS-016).
- 로컬 IT는 colima 이슈로 skip될 수 있다 — **SKIP≠통과**, CI가 유일한 실 게이트(TS-009).

## 6) 마무리
위를 마친 뒤, **현재 상태 한 줄 요약 + 승인 상태 + 이번 세션 추천 작업 1~2개**를 사용자에게 보고하고 지시를 기다려라. `$ARGUMENTS`
