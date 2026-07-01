# 그늘 (Geuneul) — 여름 생존 지도

> 폭염·장마·벌레·화장실·식수·냉방·콘센트까지, **오늘 밖에서 살아남기 위한 생활 생존 지도.**
> 태그라인: "지금 쉬어갈 그늘을 찾아드립니다."

착수 기준일: 2026-07 (여름방학) · 첫 출시 지역: 숭실대·상도·노량진·서울
성격: 공공데이터 + 유저 제보/후기 + AI 추천 · 우선순위: **1순위 · 단일 플래그십 프로젝트**

**간판(면접 헤드라인) = PostGIS 대용량 지리검색(반경/kNN) + 실시간 UGC 시공간 스코어링.**
> 원래 형제 지도앱 "맛핀"의 로그인/후기/커뮤니티/신뢰도 요소는 이 프로젝트로 흡수·병합했다(2026-07-02). 친구/그룹 그래프·스토리는 제외 — 그늘은 폐쇄 친구망이 아니라 **공개 커먼스**다.
> 타깃 회사/직무는 미정이며 **일부러 특정 회사에 종속시키지 않는다.** 스펙은 "범용 백엔드 기본기 + 지리공간 차별점"으로 짜고, 타깃별 강조점(어떤 기능을 간판으로, 이력서 서사)은 지원 시점에 정한다. (회사 매핑 메모는 `.local/PORTFOLIO-CONTEXT.md`, 커밋 금지.)

---

## 0. 이 파일을 읽는 AI에게 (작업 원칙)

너는 이 레포의 시니어 백엔드/풀스택 엔지니어다. 다음 원칙을 반드시 지킨다.

1. **MVP 우선순위를 지킨다.** 아래 "MVP" 표시된 것부터 만들고, "2차/심화"는 임의로 당겨오지 않는다.
2. **범위를 스스로 늘리지 않는다.** 새 기능 아이디어가 있으면 코드로 넣기 전에 먼저 제안하고 확인받는다.
3. **공공데이터 ingestion은 재실행 가능(idempotent)하게** 만든다. 같은 소스를 두 번 넣어도 중복이 안 생겨야 한다.
4. **좌표/반경 검색은 PostGIS 인덱스(GiST)** 를 쓴다. 애플리케이션 레벨에서 전체 스캔하지 않는다.
5. **모든 주요 API에는 테스트를 작성**한다. (공간쿼리·인제스천은 Testcontainers 실 DB로 검증.)
6. 침수·위험 정보는 **공포를 조장하지 않게** 표현한다("위험!" 대신 "최근 침수 제보 있음, 우회 권장").
7. 제보·후기는 **허위·명예훼손 관리가 필수**다. 신고/검수(모더레이션) 큐를 처음부터 염두에 둔다.
8. AI(요약·제보 분류·상태 정규화)는 **Claude API**를 기본으로 쓴다. 최신 모델을 사용한다. **AI는 곁다리** — 간판은 지리공간이다.
9. **"간판 vs 살"을 구분한다.** 지리공간 + 실시간 UGC 스코어링이 **간판**. 로그인·후기·커뮤니티는 "진짜 유저가 쓰는 제품"으로 만들어주는 **살**이지 주인공이 아니다. 커뮤니티가 주인공이 되면 그냥 리뷰앱이 되어 차별점이 사라진다.

### 🔴 필수 워크플로우 규칙 (모든 작업에 항상 적용)

**A. Git 신원 — GitHub 커밋**
- 이 레포의 모든 커밋은 **반드시** `user.name = hoengj`, `user.email = seongjuice999@gmail.com` 으로만 올라간다. (레포 로컬 config에 이미 고정됨. 전역/다른 계정 사용 금지.)
- 커밋·푸시 전 `git config user.email` 이 `seongjuice999@gmail.com` 인지 확인. 다르면 즉시 `git config user.email "seongjuice999@gmail.com"` 로 교정 후 진행.

**B. 의사결정 프로토콜 — 스펙·방법·로직·알고리즘·라이브러리를 정할 때**
판단 **1순위 = "이게 내 포트폴리오에 넣기 좋은가?"** 그다음 순서로 검토한다:
1. 이 프로젝트(그늘)에 실제로 적합한가?
2. **웹 검색으로 2026 현재 트렌드·베스트프랙티스를 확인**하고 부합하는가? (버전·대안·업계 채택도 확인)
3. 면접관이 봤을 때 "요즘 감각 있고 근거 있게 골랐다"고 느낄 선택인가?
→ 결정하면 **반드시 `WORKLOG.md` 에 "무엇을 / 왜(why) / 검토한 대안 / 트렌드 근거"를 기록**한다.

**C. 로그 문서 — 작업할 때마다 누적 작성 (면접·서류 제출용)**
모든 문서는 **면접관·서류 심사자가 읽고 좋게 느끼도록** 작성한다.
- `WORKLOG.md` — 언제 무슨 작업을 했는지 + 스펙/로직/알고리즘 **선택 이유(why)**. 시간순 append.
- `TROUBLESHOOTING.md` — 문제 상황 → 원인 분석 → 해결 과정을 **매우 구체적으로** + **"면접 어필 포인트"** 명시. append.
- **작업이 끝날 때마다 두 파일에 이어서 기록한다. 덮어쓰지 말고 계속 쌓는다.** 선택이 아니라 필수 산출물이다.

**D. 개인정보·비밀 — 대화는 OK, GitHub 유출은 절대 금지**
- 작업을 위해 사용자가 **대화 중 공유하는 개인정보(토큰·세션·비밀번호·API 키·서버 주소·학번 등)는 보고 사용해도 된다.** 사용자가 명시적으로 허용함. 이걸로 머뭇거리지 말 것.
- 그러나 **그 어떤 비밀도 git에 커밋되거나 GitHub에 푸시되면 안 된다.** 경계선은 "대화(허용) ↔ 레포/원격(금지)".
- 비밀은 **`.local/` 또는 `.env`(둘 다 gitignore됨)에만** 둔다. 코드·문서·설정·주석·로그(WORKLOG/TROUBLESHOOTING 포함)에 실제 키/비번/토큰/서버 IP를 **하드코딩·인용 금지.** 공개 예시는 `.env.example`로 placeholder만.
- **커밋·푸시 전 비밀 유출 스캔 필수.** staged diff에 키/비번/토큰/개인정보가 없는지 확인하고, `.gitignore`에 `.local/`·`.env*`·`*.key`·`*.pem`·`secrets/`가 포함돼 있는지 확인.
- 실수로 커밋되면 **즉시 히스토리에서 제거하고 해당 비밀은 폐기·재발급.**
- 개인정보 원본 위치: `.local/myInfo.source.txt` (gitignore됨). 새 비밀(Kakao/공공데이터/Claude API 키 등)도 반드시 `.local/`·`.env`로만.

---

## 1. 한 줄 정의와 핵심 가치

지도에 **위치만 찍지 말고 "지금 상태"를 보여준다.** 기존 공공지도는 "여기 화장실 있음"까지만 알려주지만, 그늘은
"지금 시원한지, 앉을 수 있는지, 콘센트 있는지, 벌레 많은지, 침수됐는지"를 **최근 제보 기준으로** 보여준다.

핵심 차별점 = **survival_score(지금 갈만함 점수)** + **최근성(freshness) 기반 실시간 체감 정보**.

**UGC 2단 구조 (이 프로젝트의 핵심 설계):**
- **제보(report)** = 휘발성 실시간 상태("지금 시원함/벌레 많음", `expires_at` 있음) → **survival_score의 freshness**를 굴린다. 한 탭으로 올리는 낮은 부담 입력.
- **후기(review)** = 영구 정성 평가(카카오맵 리뷰 같은) → **장소 평판·커뮤니티** 콘텐츠. 로그인 필요.

→ "휘발성 상태 vs 영구 평판" 2단 + 신뢰도(trust) 가중이 그늘만의 설계 포인트.

## 2. 타깃 사용자 & 시나리오

| 사용자 | 상황 | 앱이 해결할 일 |
|---|---|---|
| 대학생 | 수업 사이 1~2시간, 돈 쓰기 싫음 | 무료로 앉을 수 있고 시원한 곳 + 콘센트 여부 |
| 러너/산책러 | 한강·공원에서 물·화장실 필요 | 음수대·화장실·그늘·벌레 제보 |
| 커플/친구 | 더워서 실내로 피하고 싶음 | 눈치 안 보고 잠깐 쉴 실내 |
| 통학러 | 비 오고 길 미끄러움 | 침수·미끄럼·지하 이동 루트 제보 |
| 노트북 유저 | 카페 말고 잠깐 충전할 곳 | 콘센트/와이파이/자리 필터 |

## 3. 기능 범위 (MVP / 2차 / 심화 명확히)

| 분류 | 기능 | 단계 |
|---|---|---|
| 생존 인프라 | 무더위쉼터, 공중화장실, 음수대, 공원, 공공기관, 도서관, 지하상가 | **MVP** |
| 지리검색 | 반경 검색(ST_DWithin), 최근접(kNN `<->`), bounds 조회, 마커 클러스터 | **MVP** |
| 체감 편의 | 시원함, 앉을 수 있음, 콘센트, 와이파이, 눈치 안 보임, 공부 가능 | **MVP 일부** |
| 여름 리스크 | 벌레/러브버그/모기, 악취, 흡연냄새, 미끄럼, 침수, 공사먼지 | **MVP** |
| 실시간 제보 | 지금 시원함/더움/물 있음/화장실 깨끗함/벌레 많음/침수됨 (휘발성) | **MVP** |
| 로그인 | 카카오/구글 소셜 로그인(OAuth), JWT 세션 | **MVP** |
| 후기(리뷰) | 장소별 영구 후기(별점/코멘트/사진) | **MVP** |
| 추천 | 날씨·거리·운영시간·제보 신뢰도 반영 "지금 갈만함" 점수 | **MVP** |
| AI 요약 | "최근 제보 기준 시원하지만 화장실은 별로" 한 문장 요약 | **MVP** |
| 신뢰도 | 유저 trust_score, 제보/후기 가중, 스팸 억제 | **MVP 일부** |
| 커뮤니티 | 댓글, 사진 인증, "유용했어요", 허위 제보/후기 신고 | **2차** |
| 모더레이션 | 신고 큐, 장소 병합, 데이터 수동 보정 (관리자) | **2차** |
| 알림 | 내 주변 침수/벌레 급증, 관심 장소 상태 변화, 폭염 피난 추천 | **심화** |
| 루트 | 비 피하는 경로, 그늘 경로, 화장실 포함 경로 | **심화** |

**비목표(하지 않는다):** 전국 커버리지 욕심 금지(첫 출시는 서울, 특히 숭실대 주변만 촘촘히). 유료화·광고 MVP에 없음. **친구/그룹 소셜 그래프·스토리·팔로우 피드는 하지 않는다**(맛핀에서 넘어온 것 중 제외분 — 그늘은 공개 커먼스).

## 4. 데이터 소스

초기 데이터는 공공데이터로 확보하고, **체감 정보는 유저 제보/후기로** 쌓는다.

| 데이터 | 목적 | 수집 방식 | 주의점 |
|---|---|---|---|
| 무더위쉼터 | 시원한 공공 피난 장소(기본 레이어) | 공공데이터 API/CSV | 운영시간 최신성 검증 |
| 공중화장실 | 러닝·데이트·통학 필수 | 전국공중화장실표준데이터(2026-06 기준 약 52,255건) + 주소 지오코딩 | 2025-02 이후 일부 항목 WGS84 좌표 미제공 → **주소 지오코딩 보완**(카카오 로컬 API) |
| 음수대/식수 | 러닝·폭염 핵심 | 서울시 공원음수대 데이터, 추후 지자체 확장 | 운영 여부는 제보로 보완 |
| 날씨 | 폭염·강수·습도 기반 점수 | 기상청/공공 API 초단기예보 | **Redis TTL 캐싱 필수**(rate limit) |
| 침수/안전 | 장마철 위험 회피 | 서울/지자체 공간정보 + 유저 제보 | 표현 주의(공포 조장 금지) |
| 체감 제보/후기 | 시원함/벌레/냄새/콘센트/자리/평판 | 앱 내 제보·후기 | 허위·명예훼손 관리(모더레이션) |

## 5. survival_score 알고리즘

```
survival_score =
    0.25 * distance_score        # 가까울수록 높음
  + 0.20 * open_now_score        # 지금 열려 있으면 높음
  + 0.20 * comfort_score         # 시원함/앉을 곳/콘센트/물/화장실
  + 0.20 * report_freshness_score# 최근 제보일수록 높음 (신뢰도 가중)
  - 0.15 * risk_score            # 벌레/냄새/침수/혼잡

report_freshness_score:
  0~1시간 = 1.0 | 1~3시간 = 0.8 | 오늘 = 0.6 | 이번 주 = 0.3 | 1주 이상 = 0.1
```

- 마커 색은 카테고리가 아니라 **survival_score 구간**으로 칠한다(초록=지금 좋음 / 노랑=보통 / 회색=정보 부족).
- 제보는 **trust_score로 가중**한다(신뢰도 낮은 유저·스팸 제보의 영향 축소).
- **후기(review)는 survival_score와 분리**된 "장소 평판(reputation)"으로 관리한다(휘발성 상태 ≠ 영구 평판). 시공간 랭킹은 DB(PostGIS/SQL) 레이어에서 계산하는 것을 지향한다.

## 6. MVP 화면

| 화면 | 필수 요소 |
|---|---|
| 홈 지도 | 현재 위치, 카테고리/피처 필터, 마커 클러스터, survival_score 색상 |
| 장소 상세 | 주소, 운영시간, 시설, 최근 제보, **후기 목록**, 사진, AI 요약 |
| 제보하기 | 상태 선택, 사진 선택, 한 줄 코멘트, 익명 여부(로그인 시 신뢰도 반영) |
| 후기 작성 | 별점, 코멘트, 사진 (로그인 필요) |
| 로그인/프로필 | 카카오/구글 로그인, 내 제보·후기·신뢰도 |
| 추천 탭 | "지금 30분 버틸 곳" / "화장실 급할 때" / "비 피할 곳" |
| 관리자(2차) | 허위 제보/후기 신고 큐, 장소 병합, 데이터 수동 보정 |

## 7. 기술 스택

- **Frontend**: Next.js + TypeScript, PWA 우선(모바일 웹). 지도 UX·공유 링크·SEO에 유리.
- **Backend**: **Spring Boot 4 + Java 21**. (mp 생태계와 정렬 — Boot 3 아님. 백엔드 취업 포지션과 정렬.)
- **DB**: PostgreSQL + **PostGIS**. 공간 매핑은 **Hibernate Spatial + JTS**, 컬럼 `geometry(Point,4326)`(WGS84). 반경검색 `ST_DWithin`(GiST 자동 인덱스), 최근접 `<->`+`ORDER BY`(KNN). 스키마 마이그레이션은 **Flyway**.
- **Geocoding**: **카카오 로컬 API**(주소→좌표, 지번/도로명) — 공중화장실 WGS84 결측 보완. 결과 좌표는 저장(멱등·rate limit 회피). 대안: VWorld·국토부 지오코더.
- **Cache**: Redis (날씨 초단기예보 TTL 캐시, rate limit, 조회 캐시)
- **Realtime/Event**: 제보 급증 알림 등은 **Redis Streams / Postgres LISTEN·NOTIFY**로. (Kafka 등 과설계 금지 — 필요 입증 후에만.)
- **Storage**: S3 호환 (제보/후기 사진, presigned URL)
- **Auth**: **카카오/구글 소셜 로그인(OAuth2) + JWT 세션**
- **Map**: Kakao Maps (국내 POI/UX)
- **AI**: Claude API (장소 요약, 제보 분류, 상태 정규화) — 곁다리
- **Infra**: **기존 Oracle Cloud k3s + ArgoCD GitOps 재사용**(새로 구축 X). Docker Compose는 로컬 개발용. GitHub Actions CI(테스트 게이트).
- **Test/Ops**: Testcontainers(PG16+PostGIS/Redis), JaCoCo, k6(부하테스트), OpenTelemetry/Grafana(관측성), gitleaks.

## 8. ERD 초안

```
users(id, provider, provider_id, email, nickname, profile_image, trust_score, role, created_at)
  # provider: KAKAO | GOOGLE ; role: USER | ADMIN

places(id, name, category, address, geom(Point,4326), source, source_external_id,
       external_map_url, open_hours_json, geocoded, created_at, updated_at)
  # source_external_id + source = 멱등 upsert 자연키 ; geocoded = 지오코딩 보완 여부

place_features(id, place_id, feature_type, value, source, confidence)
  # feature_type: air_conditioned, outlet, wifi, restroom, water, seating, no_eyes

reports(id, user_id, place_id, report_type, status_value, comment,
        photo_url, confidence, is_anonymous, created_at, expires_at)
  # report_type: COOL, HOT, BUG, ODOR, SMOKE, FLOOD, SLIPPERY, WATER_OK, RESTROOM_CLEAN
  # 휘발성 — expires_at 지나면 freshness/score에서 제외

reviews(id, user_id, place_id, rating, comment, photos_json, created_at, updated_at)
  # 영구 평판 — 로그인 필요, survival_score와 분리

review_comments(id, review_id, user_id, comment, created_at)          # 2차: 커뮤니티
reactions(id, target_type, target_id, user_id, type)                  # 2차: 유용했어요 등
reports_flags/reviews_flags(id, target_id, reporter_id, reason, status, created_at)  # 신고/검수 큐
trust_scores(id, target_type, target_id, score, factors_json, calculated_at)
bookmarks(id, user_id, place_id, memo, created_at)
notifications(id, user_id, type, condition_json, is_active, created_at)   # 심화
```

## 9. API 초안

```
# 인증
POST /auth/kakao , POST /auth/google          # 소셜 로그인 → JWT
GET  /me                                        # 내 프로필/신뢰도

# 지도/장소 (PostGIS)
GET  /places?lat=&lng=&radius=&category=&feature=   # 반경 마커 조회
GET  /places?bounds=                                # bounds 조회
GET  /places/nearest?lat=&lng=&type=                # kNN 최근접
GET  /places/{id}                                   # 상세 + 최근 제보 + 후기 + AI 요약

# UGC
POST /reports                                       # 장소 상태 제보(휘발성)
POST /reviews                                        # 장소 후기(영구, 로그인)
GET  /places/{id}/reviews
POST /reviews/{id}/comments                          # 2차
POST /photos/presign                                 # 사진 업로드 URL 발급
POST /flags                                          # 허위 제보/후기 신고

# 추천/관리
GET  /recommendations?lat=&lng=&scenario=            # scenario: rest30 | restroom | rain
GET  /admin/flags/pending                            # 관리자 검수 큐
POST /notifications/rules                             # (심화) 관심 알림 설정
```

## 10. 로드맵 (대형 심화 — mp급 깊이, 단 mp 복제 금지)

| Phase | 목표 | 산출물 |
|---|---|---|
| **W0 · 세팅** | 레포·기술 결정·인프라 연결 | Docker Compose(PostGIS+Redis), Flyway 스키마, Kakao Maps SDK, CI 스켈레톤 |
| **P1 · 지리 코어** | 지도 엔진 + 공공데이터 | place/report/user 엔티티, **공공데이터 idempotent ingestion + 지오코딩**, 반경/kNN/bounds API, Swagger, Testcontainers |
| **P2 · UGC + 인증** | 로그인·제보·후기 | 카카오/구글 OAuth+JWT, 제보(휘발)+후기(영구) 2단, 사진 presign, 신뢰도, 신고/검수 큐 |
| **P3 · 스코어·추천·AI** | survival_score + 요약 | 시공간 랭킹(SQL), 추천 시나리오, 날씨 API+Redis TTL 캐시, AI 한줄 요약(Claude) |
| **P4 · 심화(간판)** | 성능·실시간·관측 | **k6 부하테스트 + EXPLAIN 인덱스 튜닝**, 실시간 이벤트(제보 급증 알림), 캐시 전략, 관측성(OTel/Grafana), **ADR 문서** |
| **P5 · 배포·실사용** | 실서비스 | k3s+ArgoCD 배포, 숭실대 주변 seed data, 여름 필드테스트·피드백 |

## 11. 포트폴리오 포인트

**간판:** PostGIS 반경/kNN 대용량 지리검색 · 공공데이터 idempotent ETL + 주소 지오코딩 · 2단 UGC(제보/후기) 시공간 스코어링.
**심화:** 부하테스트(k6)·EXPLAIN 인덱스 튜닝 · 실시간 이벤트 · 캐시 전략 · 신뢰도/모더레이션 · ADR 기반 의사결정 기록.
**살:** 소셜 로그인(OAuth)·후기 커뮤니티·PWA 지도 프론트.

> ⚠️ 법률 자문 아님. 침수/안전 표현, 후기 명예훼손, 위치 데이터는 배포 전 표현·정책 검토 권장.
