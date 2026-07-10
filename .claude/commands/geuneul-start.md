---
description: 그늘(geuneul) 다음 세션 시작 — 현황 검증 후 후속 백로그(docs/BACKLOG.md F1~F6) 실행
---

너는 이 레포의 시니어 백엔드/풀스택 엔지니어다. 새 세션을 시작한다. 아래를 **순서대로** 수행하라.

> **현재 상태(2026-07-10 기준)**: 로드맵 W0~P4 + 심화+additive 코드(A1~A7·B1~B2)까지 **전량 라이브**(PR #61~#69, V13~V15, ADR 0017~0019). 데이터 op(A3 쉼터 냉방 57,070 백필·A8 6대 광역시)도 완료. **남은 건 `docs/BACKLOG.md`의 후속 백로그(F1~F6)** — 대부분 외부 키·실기기·트래픽 대기다. **F1(A8 나머지 지역 확장)만 쿼터로 바로 가능.**

## 1) 컨텍스트 로드 (읽기)
- **`docs/BACKLOG.md`** — 이번 세션 실행 대상(**F1~F6 후속 백로그** + 완료 요약 + 신규 심화 아이디어).
- `HANDOFF.md` 의 **"▶ 세션 인계"**(현황·rev·데이터 카운트·라이브 엔드포인트).
- `CLAUDE.md` 의 **§0 작업 원칙** + **🔴 필수 워크플로우 규칙 A~E**.
  - **규칙 E(모델 역할 분리)**: 설계·검증·머지 판단은 **세션 메인 모델(Opus/Fable)**이 직접, 단순·기계적 실작업(보일러플레이트·정형 테스트·스크립트 실행·문서 반복 갱신)은 **하위 모델(`Agent` `model: sonnet` / codex / agy)**에 위임. 위임 결과는 메인 모델이 검증 후 커밋(blind 머지 금지).
- `TROUBLESHOOTING.md` 의 **TS-025**(머지 전 `gh pr checks`)·**TS-026/027**(외부 API 계약 실호출 검증·IP 잠금)·**TS-016/009**(Instant 프로젝션·로컬 IT skip≠통과).
- 메모리 인덱스(`MEMORY.md`) 관련 항목.

## 2) 상태 검증 (실행)
```bash
cd /Users/seongju/geuneul
git checkout main && git pull origin main --quiet
git status --short                                   # 클린이어야 함
git config user.email                                # seongjuice999@gmail.com 확인
gh pr list --state open                              # 열린 PR 확인(없어야 정상)
git log main --format="%B" | grep -ciE "^co-authored-by:.*(claude|anthropic)"   # 0이어야 함
B=https://d2pedv974beobb.cloudfront.net
/usr/bin/curl -s -o /dev/null -w "API %{http_code}\n" $B/actuator/health
/usr/bin/curl -s -o /dev/null -w "App %{http_code}\n" https://geuneul.vercel.app
# 이번 세션 산출 엔드포인트 라이브 확인
/usr/bin/curl -s -o /dev/null -w "bookmarks %{http_code}(401기대)\n" $B/me/bookmarks
/usr/bin/curl -s -o /dev/null -w "notifications %{http_code}(401기대)\n" $B/notifications
/usr/bin/curl -s -o /dev/null -w "routes %{http_code}(200기대)\n" -G $B/routes/toilet \
  --data-urlencode fromLat=37.5665 --data-urlencode fromLng=126.978 --data-urlencode toLat=37.57 --data-urlencode toLng=126.983
# 데이터 스팟체크(쉼터 냉방·상권)
/usr/bin/curl -s -G "$B/places" --data-urlencode lat=37.5665 --data-urlencode lng=126.978 --data-urlencode radius=2000 \
  --data-urlencode category=COOLING_SHELTER -o /dev/null -w "shelters %{http_code}\n"
```

## 3) 백로그 실행 (핵심 — `docs/BACKLOG.md`의 F1~F6)
**준비된 것부터** 착수한다(전량 강행 아님 — 대부분 외부 대기). 각 항목의 무엇/왜/함정/수용기준은 `docs/BACKLOG.md`에 상세.
- **F1. A8 상권 나머지 지역 확장** ← 쿼터만 있으면 **바로 가능**. `prod-ingest-stores.sh <bbox> 1500`, **반드시 순차(IngestBatchLock)**, data.go.kr 일일 쿼터라 하루 단위.
- **F2. B1 Web Push 전송** — VAPID 키 있음(`.local/webpush.env`). 크립토 라이브러리 + **실기기(설치 PWA) 검증** 필요 → 실기기 준비 시.
- **F3. B2 카카오모빌리티 도로 폴리라인** — **활용신청·키(사용자 액션)**. 키 확보 시 `DirectionsProvider` Kakao 구현(TS-026 계약검증) + 지도 폴리라인 UI.
- **F4. B2 그늘/비 경로**(설계) · **F5. HEAT_ESCAPE 알림 평가**(날씨 트리거) · **F6. A9 Fargate cpu 512**(트래픽 대기).
- **중간 사용자 입력 지점**: F2 실기기·F3 카카오 키·F1 대량 쿼터. 도달 시 **한 줄 제안 후 진행**(막히면 다음으로).
- 신규 심화 아이디어(P5 실사용·관측성 심화·A1 후속)는 `docs/BACKLOG.md` 하단 — **제안 후 착수**(§0-2 범위 임의 확장 금지).

## 4) 규율 (절대 지킬 것)
- **머지 전 반드시 `gh pr checks <N>`로 "Backend (Gradle, JDK 21)" pass 눈으로 확인**(TS-025 — `gh run watch` EXIT만 믿지 말 것).
- 기능 브랜치 + PR + CI green 후 squash 머지. **로컬 main 직접 작업 금지**, 특정 파일만 스테이징.
- 커밋 신원 `ghdtjdwn`/`seongjuice999@gmail.com`. **커밋에 `Co-Authored-By: Claude` 트레일러 금지**(§A).
- 새 외부 API는 **코드 前 실호출로 계약 검증**(TS-026/027). 비밀·개인정보(키·IP)는 `.local`·env로만, 문서·커밋 금지(§D).
- 의사결정은 웹 2026 트렌드 확인 + `WORKLOG.md`(무엇/왜/대안/근거), 사고는 `TROUBLESHOOTING.md`, 아키텍처는 `docs/adr/`.
- 네이티브 프로젝션 시각컬럼=`Instant`+DTO UTC 부착(TS-016). 로컬 IT는 colima skip → **SKIP≠통과, CI가 유일 게이트**(TS-009).
- 간판(지리·스코어링) 우선, **커뮤니티는 살**(리뷰앱화 금지 §0-9). 침수/위험 표현 **공포 조장 금지**(§6). **범위 임의 확장 금지 — 신규는 제안 후**(§0-2).

## 5) 완료마다
- `docs/BACKLOG.md`(F 체크·완료 이동) + `HANDOFF.md`(현황·데이터·rev) 갱신 + WORKLOG/ADR/TROUBLESHOOTING 기록.

## 6) 마무리
착수분 완료(또는 외부 대기로 블록) 시, **완료 목록 + 남은(블록된) 항목 + 다음 추천**을 사용자에게 보고한다. `$ARGUMENTS`
