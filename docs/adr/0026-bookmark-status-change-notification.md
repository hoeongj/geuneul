# ADR-0026. 관심 장소 상태 변화 알림 — 북마크 장소 단건 유의미 제보(침수·미끄럼) 이벤트 훅(C3)

- 상태: 승인(구현 반영, 2026-07-11)
- 관련: ADR-0018(알림 §2 "관심 장소 상태 변화"를 범위 밖으로 남김), ADR-0016(급증 LISTEN/NOTIFY),
  `ReportNotificationListener`·`NotificationService.onBookmarkStatus`·`NotificationDeliveryRepositoryImpl.insertBookmarkStatusReturning`,
  `ReportRepository.findRecentMeaningfulReports`·`MeaningfulReportView`, `bookmarks`(A7·V14), CLAUDE.md §2(통학러 침수 우회)·§6(공포 조장 금지)·§9(기능 증식 금지)
- 후속: BACKLOG C3

## 문제(Context)

ADR-0018 §2는 급증(≥3건/10분)일 때만 BOOKMARK_SURGE로 관심 장소 알림을 보내고, **"단건 상태 변화"는 범위 밖**으로 남겼다.
그런데 §2 시나리오(통학러: "비 오고 길 미끄러움 → 침수·미끄럼 우회")는 급증을 기다릴 수 없다 — **침수 제보 1건**이라도
관심 장소에 뜨면 저장한 유저가 즉시 우회할 수 있어야 실효가 있다. 기존 파이프라인은 급증 게이트(`surgeForPlace().ifPresent`)
에 막혀 단건은 알림이 안 갔다.

## 결정(Decision)

**북마크한 장소에 유의미한 안전 제보(FLOOD 침수·SLIPPERY 미끄럼)가 1건이라도 올라오면 저장한 유저에게 인앱 알림 1건(+F2 푸시)을 보낸다.
새 규칙타입·화면·마이그레이션 없이, 기존 V9 LISTEN/NOTIFY 이벤트와 BOOKMARK_SURGE 규칙·토글을 그대로 재사용한다.**

- **이벤트-드리븐(온디맨드 아님)**: `ReportNotificationListener.handle()`이 모든 제보 INSERT의 NOTIFY를 받으므로, 급증 처리와
  **별개로 무조건** `NotificationService.onBookmarkStatus(placeId)`를 호출한다(급증 게이트 밖). 신규 인프라 0.
- **유의미 타입만, 타입별 최신 1건**: `ReportRepository.findRecentMeaningfulReports(placeId, {FLOOD,SLIPPERY}, since)`가
  since(=now-10분) 이후·미만료·미숨김 제보를 `DISTINCT ON (report_type)`으로 **타입마다 최신 1건**씩 반환한다(FLOOD·SLIPPERY가
  거의 동시에 들어와도 둘 다 알림 — LIMIT 1이면 더 최근 타입이 오래된 타입 알림을 가림). 없으면 알림 없음. 반환 투영은
  `reportType`+`createdAt`(Instant, TS-016)이고, 서비스가 타입별로 루프한다.
- **dedup 버킷 = 제보 created_at 기준**: `bucket = report.createdAt / COOLDOWN_MS`. 벽시계(now/COOLDOWN)가 아니라 **제보 시각**에서
  버킷을 뽑아, 같은 제보가 이후 무관한 제보 이벤트(예: COOL)에 딸려 버킷 경계를 넘어 재알림되는 것을 없앤다(sliding since ↔ epoch bucket 불일치 제거).
- **규칙·발송 재사용 + RETURNING**: `insertBookmarkStatusReturning`(커스텀 조각)이 dedup_key 접두사 `bmstatus:`(surge와 분리)·
  type=`BOOKMARK_SURGE` 재사용·bookmarks JOIN·`ON CONFLICT DO NOTHING RETURNING user_id`로 삽입하고 **이 트랜잭션이 실제 삽입한
  user_id만** 돌려준다. per-user dedup_key라 각 알림은 정확히 한 호출만 삽입 성공 → 반환 목록에만 푸시.
- **§6 중립 단수 문구**: "최근 침수 제보가 있어요 · 우회를 권장해요"(FLOOD) / "최근 바닥이 미끄럽다는 제보가 있어요 · 조심해서 이동하세요"(SLIPPERY).
- **푸시(F2)**: RETURNING이 돌려준 유저에게만 `pushService.sendToUser`(비동기·실패 격리, TS-029, push 비활성이면 no-op).
  삽입 집합과 푸시 집합이 항상 일치해 멀티 인스턴스에서도 **정확히 1회 푸시**(누락·중복 없음).

## 근거(Why)

- **왜 이벤트-드리븐(급증 재사용)인가 — 온디맨드(F5 폭염) 아님**: V9가 이미 모든 제보 INSERT마다 NOTIFY하므로 `handle()`에 한 줄
  훅이면 신규 스케줄러 0(§0-2 과설계 금지). F5가 온디맨드로 간 이유(폭염은 상태라 트리거 소스 없음)는 여기 적용 안 됨 —
  제보는 이산 이벤트라 트리거가 이미 존재한다. 침수 우회는 시급(§2 통학러)이라 실시간이 맞다.
- **왜 급증 게이트 밖·무조건 호출인가**: 단건은 정의상 급증(≥3건) 미달이라 `surgeForPlace().ifPresent()` 안에 두면 영영 안 간다.
  onSurge와 독립적으로 예외 격리해 호출(한쪽 실패가 다른쪽·리스너 루프를 안 죽임).
- **왜 유의미 타입을 FLOOD·SLIPPERY로 좁히나**: 관심 장소 단건 알림을 모든 제보로 열면 COOL/SEAT_OK까지 스팸이 된다. §2가
  요구하는 "안전 우회"의 핵심은 침수·미끄럼(장마철 위험)이라 이 둘로 좁혀 신호 대 잡음을 지킨다. `since=now-10분`은 오래된
  제보를 알림 후보에서 거르는 **최근성 필터**이고, dedup은 제보 created_at 버킷이 담당한다(둘의 역할 분리).
- **왜 dedup 버킷을 제보 시각에서 뽑나(벽시계 아님)**: 초기 설계는 `since`를 sliding(now-10분)으로, dedup bucket을 epoch-aligned
  (now/10분)으로 서로 다르게 앵커링해, 10분 내 침수 제보가 버킷 경계를 넘는 후속 제보(무관한 COOL 포함)에 딸려 **재알림**됐다
  (적대적 리뷰 확정). 버킷을 제보 created_at에서 뽑으면 같은 제보는 항상 같은 dedup_key → 후속 이벤트가 몇 번을 재조회해도 1건.
  제보의 **자기 INSERT 이벤트**가 자신을 즉시 알리므로(놓침 없음), 버킷 정렬로 인한 경계 놓침도 없다.
- **왜 타입별 최신 1건(DISTINCT ON)인가**: V9 NOTIFY는 place_id만 실어 "방금 그 제보의 타입"을 알 수 없어 "최근 유의미"를 재조회한다.
  LIMIT 1(최신 1개)이면 FLOOD·SLIPPERY가 ms 차로 들어올 때 더 최근 타입이 오래된 타입 알림을 가려 **안전 알림 하나를 잃는다**(리뷰 확정).
  `DISTINCT ON (report_type)`로 타입별 최신을 각각 반환해 둘 다 자기 dedup_key로 발송.
- **왜 RETURNING(커스텀 조각)인가 — 사전 SELECT + inserted>0 가드는 틀렸다**: 리뷰가 반증한 핵심 — 사전 수신자 SELECT와 INSERT는
  별도 스냅샷이고 dedup_key가 per-user(ruleId 포함)라 "all-or-nothing"이 아니다. 두 문 사이에 새 북마커가 커밋되면 in-app 행은
  생기지만 stale 수신자에 없어 **푸시 누락**, 멀티 인스턴스 row-split이면 stale 목록을 다시 밀어 **중복 푸시**가 났다. `INSERT ...
  RETURNING user_id`는 이 트랜잭션이 실제 삽입한 유저만 돌려주므로 삽입=푸시 집합이 항상 일치한다. Spring Data @Query가 INSERT를
  @Modifying(int) 전용으로 볼 수 있어(TS-009 CI 리스크 회피) **EntityManager 커스텀 조각**으로 확실히 실행한다.
- **왜 기존 BOOKMARK_SURGE 규칙 재사용(새 토글 금지)인가**: §9 — 새 규칙타입·토글·화면을 만들면 기능이 증식해 커뮤니티/설정이
  주인공화한다. "관심 장소 소식" 토글 하나가 급증·단건 상태변화를 함께 관장하는 게 사용자 모델상 자연스럽다.
- **왜 마이그레이션 0인가**: notification_deliveries.type VARCHAR(24)·dedup_key VARCHAR(200)이 `bmstatus:` 키를 수용하고
  규칙을 재사용하므로 컬럼 추가가 없다.

## 결과(Consequences)

- 마이그레이션·외부 API 0. `notification_deliveries`에 `NotificationService`에 ReportRepository 주입 1건 추가(생성자).
- 급증(onSurge)과 단건(onBookmarkStatus)이 동시에 나면 dedup_key가 달라(`bmsurge:` vs `bmstatus:`) 2건이 가능하다 —
  정보가 다르므로 허용(급증="몰리고 있어요" / 단건="침수 우회 권장"). 사용자에겐 오히려 맥락이 는다.
- 확장점: 유의미 타입 집합(FLOOD·SLIPPERY)은 상수라 조정 가능. 필요 시 별도 규칙타입으로 분리할 수 있으나 현재는 §9로 병합 유지.
