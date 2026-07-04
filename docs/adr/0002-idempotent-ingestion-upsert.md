# ADR-0002. 공공데이터 인제스천은 자연키 ON CONFLICT 배치 upsert

- 상태: 승인 (2026-07-02)
- 관련: `places (source, source_external_id)` UNIQUE, `PlaceBulkUpsertRepository`, `StandardCsvParser`
- 개정: 트랜잭션 경계는 [ADR-0003](0003-geocoding-pipeline.md)에서 upsert 단위로 변경(지오코딩 도입).

## 문제(Context)

공공데이터(무더위쉼터, 공중화장실 약 60k — 실측 59,768행)는 **주기적으로 갱신**되고, 인제스천은 실패·재실행이
일상이다. "같은 소스를 두 번 넣어도 중복이 안 생겨야 한다"(CLAUDE.md 원칙 3).
동시에 수만 행을 넣어야 하므로 JPA 엔티티 단위 save()는 성능·메모리에서 불리하다.

## 결정(Decision)

1. **자연키 = `(source, source_external_id)` UNIQUE 제약** (V2)
   - 표준데이터의 고유번호(예: 쉼터시설번호)를 external_id로 사용.
   - 고유번호가 없는 행은 `sha256(name|address)` 결정적 대체키 — 같은 행은 재적재 때
     항상 같은 키로 수렴한다.
2. **JDBC 배치 `INSERT ... ON CONFLICT ... DO UPDATE`** (1,000행 청크)
   - 존재하면 갱신(이름/주소/좌표/updated_at), 없으면 삽입 — 한 문장으로 멱등.
   - geom은 DB에서 `ST_SetSRID(ST_MakePoint(lng,lat),4326)` 생성 → 앱의 WKB 직렬화 생략.
3. **트랜잭션 경계** — 초기엔 파일 단위 단일 트랜잭션. **→ ADR-0003에서 upsert 단위로 개정**(지오코딩 도입 후 60k 외부 HTTP를 한 tx에 넣지 않기 위해; 멱등이라 부분 실패도 재실행으로 수렴).
4. **좌표 결측·범위 밖 행은 버리지 않는다** — 주소가 있으면 지오코딩 후보(needGeocode)로 넘겨
   같은 실행 안에서 좌표를 얻어 적재한다(ADR-0003 파이프라인). 적재 불가 행(이름 결측·표준데이터
   이중헤더 라벨 에코 행·유효좌표와 주소가 모두 없는 행)만 skipped로 계수해 손실 규모를 수치로 확인한다.

## 검토한 대안(Alternatives)

| 대안 | 기각 이유 |
|---|---|
| JPA `saveAll()` + 사전 존재 조회 | 조회+더티체킹으로 N배 느리고, 조회-삽입 사이 경합 여지. 60k행 스케일에 부적합 |
| DELETE 후 전체 재삽입 | id가 매번 바뀌어 제보/후기의 FK가 끊어짐. 다운타임성 공백 발생 |
| Spring Batch | 소스 1개 단계에서 과설계(mp의 "필요 입증 후 도입" 원칙). 스케줄·재시도가 필요해지는 P3+에서 재검토 |
| MERGE (SQL:2003) | PostgreSQL 15+ 지원하지만 ON CONFLICT가 더 관용적·PG 표준 관행 |

## 결과(Consequences)

- 인제스천이 **재실행 가능(idempotent)** — IT로 검증(`IngestionIdempotencyIT`: 2회 적재 후 행 수 동일, 변경 필드는 갱신).
- UPDATE가 no-op이어도 updated_at이 갱신됨(허용 — 최신 동기화 시각 의미로 사용).
- 대체키 행은 이름/주소가 바뀌면 새 행으로 인식됨(한계) → 고유번호 있는 소스를 우선 사용.

## 근거(References)

- [PostgreSQL: INSERT ON CONFLICT](https://www.postgresql.org/docs/current/sql-insert.html#SQL-ON-CONFLICT)
- 전국무더위쉼터표준데이터 (공공데이터포털 15013199) — 쉼터시설번호 필드 확인
- dom(con-dorm)의 SHA-256 변경감지 크롤 경험 → 여기서는 행 단위 자연키로 발전
