---
description: 그늘(geuneul) 남은 일 전부 실행 — docs/BACKLOG.md의 N1~N9(버그·UX·기능·심화)를 한 번에 구동
---

너는 이 레포의 시니어 백엔드/풀스택 엔지니어다. **남은 백로그(N1~N9)를 전부 구현·머지·라이브까지** 끝낸다.
진단·근본원인·수정위치는 `docs/BACKLOG.md`에 이미 박혀 있다 — **재조사 말고 바로 구현**한다.

## 0) 컨텍스트 로드 (읽기)
- **`docs/BACKLOG.md`** — 실행 대상 **N1~N9**(각 무엇/근본원인/수정위치/수용기준) + 완료·결정 로그.
- `HANDOFF.md` "▶ 세션 인계"(현황·rev·라이브 엔드포인트).
- `CLAUDE.md` §0 + 🔴 워크플로우 규칙 **A~E**(특히 **E: 설계·검증=메인 모델, 단순작업=codex·agy 먼저→Sonnet 폴백**).
- `TROUBLESHOOTING.md` **TS-025**(머지 전 `gh pr checks`)·**TS-026**(외부 API 계약검증)·**TS-028**(Boot4=Jackson3 ObjectMapper 주입 금지)·**TS-029**(외부 전송 비동기)·**TS-009**(로컬 IT skip≠통과, CI가 게이트)·**TS-016**(Instant 프로젝션).
- 메모리 인덱스(`MEMORY.md`).

## 1) 상태 검증 (실행)
```bash
cd /Users/seongju/geuneul
git checkout main && git pull origin main --quiet
git status --short                                   # 클린
git config user.email                                # seongjuice999@gmail.com
gh pr list --state open                              # 없어야 정상
git log main --format="%B" | grep -ciE "^co-authored-by:.*(claude|anthropic)"   # 0
B=https://d2pedv974beobb.cloudfront.net
/usr/bin/curl -s -o /dev/null -w "API %{http_code}\n" $B/actuator/health         # 200
/usr/bin/curl -s "$B/push/public-key" | python3 -c "import sys,json;print('push enabled=',json.load(sys.stdin)['enabled'])"  # true
```

## 2) 실행 (핵심 — N1~N9, 권장 순서)
**버그/UX 먼저(빠른 실사용 개선) → 기능 → 심화.** 각 항목 = 기능 브랜치 → PR → **`gh pr checks <N>` Backend pass 눈확인(TS-025)** → squash 머지 → 배포 확인. 관련 항목은 묶어서 1 PR 가능.

1. **N1 사진 표시**(리뷰 `<img>` 렌더 + **presigned-GET-at-read** 백엔드·제보 사진 공유 수정). 버킷 퍼블릭 전환 금지(s3.tf).
2. **N2 댓글 접힘 UX**(백엔드 무변경 — 자동 펼침/카운트 UX + 배포 최신화 확인).
3. **N3 제보 사진 capture 제거**(1줄, `report/page.tsx`).
4. **N4 하단 시트 3단 스냅+드래그**(`BottomSheet.tsx`).
5. **N5 지정 장소 검색**(카카오 keyword.json, BFF, 지도 recenter, TS-026 계약검증).
6. **N6 내 글 관리** + **N7 작성자 팔로우(커먼스 세이프)** — 코드 공유(작성자별 후기 조회). N7은 **팔로워 목록 노출 금지·피드 없음**(§0-9), 팔로워 수만 공개·팔로잉은 나만. Flyway V17. **ADR로 "커먼스 세이프 팔로우" 기록.**
7. **N8 F4 그늘/비 경로**(경로 corridor 주변 쉼터/실내 오버레이). ADR.
8. **N9 대규모 대비**(k6 재부하 → 오토스케일링·task_cpu 512·EXPLAIN 재튜닝·캐시/풀). ADR + before/after.

### 규율 (절대)
- **머지 전 `gh pr checks <N>`로 "Backend (Gradle, JDK 21)" pass 눈확인**(TS-025 — run watch EXIT만 믿지 말 것). **로컬 IT는 skip되니 신뢰 금지, CI가 유일 게이트(TS-009).**
- **규칙 E**: 설계·아키텍처·ADR·검증·머지 판단은 메인 모델. 보일러플레이트·정형 테스트·반복 편집은 **codex·agy 먼저(headless 불가 시 `Agent model:sonnet` 폴백)**. 위임 결과는 메인이 검증 후 커밋.
- 커밋 신원 `hoengj`/`seongjuice999@gmail.com`, **Co-Authored-By: Claude 금지**. 특정 파일만 스테이징, 로컬 main 직접 작업 금지.
- 비밀·키·IP는 `.local`·SSM/env로만, 커밋·문서 금지. 커밋 전 비밀 스캔.
- Boot4=Jackson3(TS-028), 외부 전송 비동기(TS-029), 시각컬럼 Instant+UTC(TS-016), 새 외부 API 계약검증(TS-026).
- 간판 우선·**커뮤니티는 살**(리뷰앱·친구망화 금지 §0-9). 침수/위험 표현 공포 조장 금지(§6). **신규 범위는 BACKLOG 밖이면 제안 후**(§0-2).

## 3) 완료마다
- `docs/BACKLOG.md`(N 체크·완료 이동) + `HANDOFF.md`(현황·rev·데이터) + `WORKLOG.md`(무엇/왜/대안) + ADR/`TROUBLESHOOTING.md`.

## 4) 마무리
N1~N9 완료(또는 외부 대기로 블록) 시 **완료 목록 + 남은(블록) 항목 + 라이브 검증 결과**를 보고. 프로덕션 배포·엔드포인트 실측까지 확인.
