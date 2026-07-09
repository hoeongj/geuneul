# ADR-0010. AI 한줄 요약 — 프로바이더는 OpenRouter(Anthropic 키 부재로 이탈), 상세(getById) 전용 additive

- 상태: 승인 (2026-07-10)
- 관련: `domain/ai/OpenRouterClient`, `domain/ai/AiSummaryService`, `PlaceResponse.of(ScoredPlaceView, Double, Double, String)`,
  `PlaceSearchService.getById`, `RedisCacheConfig`(aiSummary 캐시), `application.yml`(ai.openrouter.*),
  `infra/terraform/ssm.tf`·`variables.tf`·`ecs.tf`(스캐폴드, apply 안 함)
- 선행: [ADR-0009](0009-weather-comfort-additive-restore.md)(같은 "요청당 1회·graceful degradation" 패턴을
  AI 요약에도 재사용), CLAUDE.md §3(AI 요약 MVP)·§6(상세화면 AI요약)·§8(AI 스택)·§0-8/9(곁다리 원칙)

## 문제(Context)

CLAUDE.md §8은 "AI(요약·제보 분류·상태 정규화)는 Claude API를 기본으로 쓴다"고 명시한다. 그러나 이
작업 환경에는 **Anthropic API 키가 없다** — `claude-api` 스킬의 인증 해석 순서(ANTHROPIC_API_KEY →
ANTHROPIC_AUTH_TOKEN → OAuth 프로필 → WIF)를 모두 확인했지만 어느 것도 사용 가능하지 않았다. 반면
사용자는 대화 중 명시적으로 멀티프로바이더 무료/저가 폴백 키체인(`.local/ai.env`, OpenRouter/Groq/
Gemini/Cerebras 등)의 존재와 사용을 허용했다. §0-B 의사결정 프로토콜(①적합성 ②2026 트렌드 확인
③방어 가능성)에 따라 이 이탈을 검토하고 기록한다.

기능 자체는 §3("AI 요약: MVP")·§6(상세화면 "AI 요약")에 이미 정의돼 있다 — 장소 상세에서 "최근 제보
기준" 한국어 한 문장 요약. §0-8은 "AI는 곁다리 — 간판은 지리공간이다"를 못 박는다: 이 기능이 비용·
지연·장애 벡터로 지리공간 핵심 경로를 가리면 안 된다.

## 결정(Decision)

### 1) 프로바이더 — OpenRouter(OpenAI 호환 Chat Completions), Anthropic SDK 아님

`SSUAI_OPENROUTER_API_KEY`를 프라이머리로 쓴다. OpenRouter는 OpenAI 호환 단일 엔드포인트로 다수
모델(Anthropic·Google·Qwen 등)을 라우팅하므로, **나중에 Anthropic 키가 생기면 `ai.openrouter.base-url`을
그대로 두고 `ai.openrouter.model`만 `anthropic/claude-*`로 바꾸거나, `OpenRouterClient`를 Anthropic
공식 SDK로 교체(요청/응답 계약이 유사해 전환 비용 낮음)하는 두 경로가 모두 열려 있다** — 이탈이
일방통행이 아니게 설계했다.

클라이언트는 Anthropic SDK/`claude-api` 스킬 대신 Spring `RestClient`로 직접 구현했다(WeatherClient와
동일 패턴 — 이 프로젝트는 외부 HTTP 연동을 이미 이 방식으로 표준화했고, OpenAI 호환 REST 계약은
가볍다: 별도 SDK 의존성 추가가 정당화되지 않는다).

### 2) 모델 — `qwen/qwen3-next-80b-a3b-instruct:free`(설정으로 교체 가능, 하드코딩 아님)

2026-07 웹 검색(§0-B②)으로 확인:
- OpenRouter 무료 티어는 20 req/min(요청당 1회 요약 캐시라 이 프로젝트 트래픽에 충분).
- Qwen3 계열은 119개 언어·방언을 지원하며 **중국어·일본어·한국어에서 특히 우수**하다고 평가됨
  (OpenRouter/업계 벤치마크 요약, WebSearch 결과). 무료(`:free`) 변형이 OpenRouter에 존재함
  (`qwen/qwen3-next-80b-a3b-instruct:free` — instruction-tuned, "빠르고 안정적인 응답에 최적화").
- 대안 Gemma 4(31B, 무료, 140+ 언어)도 검토했으나, 한국어 등 아시아 언어 특화 근거가 Qwen3만큼
  명시적이지 않아 후순위.

**모델은 `application.yml`의 `ai.openrouter.model`(env `OPENROUTER_MODEL`)로만 결정** — 코드에
하드코딩하지 않는다. OpenRouter의 무료 모델 라인업은 주기적으로 회전한다(오늘 있는 모델이 다음 달엔
없을 수 있음, WebSearch로 확인된 업계 공통 주의사항) — 설정값 교체만으로 대응 가능하게 만들어
이 리스크를 흡수했다.

### 3) 설계 제약(지시사항 그대로 반영, ADR-0009 패턴 재사용)

| 제약 | 구현 |
|---|---|
| 상세(getById) 전용, 목록/반경/bounds 미포함 | `PlaceSearchService.searchRadius/searchBounds/searchNearest`는 `AiSummaryService`를 호출하지 않음(`PlaceSearchServiceTest`가 각 경로에서 `never()` 검증). `PlaceResponse.of(ScoredPlaceView, Double, Double)`(3-인자, 기존 계약)는 aiSummary=null로 위임, 4-인자 오버로드만 실제로 채운다. |
| Redis 캐시(장소별, TTL 1~6h) | `RedisCacheConfig`에 `aiSummary` 캐시 신설, TTL 3시간(지시 범위 중간값) — WeatherClient와 동일하게 값 직렬화는 타입 바인드(`JacksonJsonRedisSerializer<String>`, TS-011 재발 방지). |
| 제보 없으면 생성 안 함 | `AiSummaryService.summarize`가 유효 제보 0건이면 `OpenRouterClient`를 호출조차 하지 않고 empty 반환(정적 "정보 부족" 문구 대신 — 상세 화면이 이미 "최근 제보 없음"을 별도로 보여주므로 AI가 같은 말을 반복할 필요가 없고, empty는 캐시되지 않아 다음 제보가 오면 즉시 재평가됨). |
| graceful degradation, 절대 500 금지 | `OpenRouterClient.complete`가 키 미설정·네트워크 오류·타임아웃·5xx·빈 응답·JSON 파싱 실패를 전부 `Optional.empty()`로 흡수(WeatherClient와 동일 패턴). `AiSummaryService`는 그 위에 얹을 뿐 추가 예외 경로가 없다. `PlaceSearchService.getById`는 `aiSummaryService.summarize(id).orElse(null)`로 단순 위임 — AI가 죽어도 상세 API는 200이다. |
| 입력 = 유효(만료 전) 최근 제보 요약 | `ReportRepository.findTop20ByPlaceIdAndExpiresAtAfterOrderByCreatedAtDesc`(기존 쿼리, "최근 제보" 섹션과 동일 소스) 재사용 — 새 쿼리를 만들지 않았다. 타입별 최신 1건으로 중복 제거 후 최신순 상한 8종만 프롬프트에 담아 토큰·비용을 방어한다. |
| 공포 조장 금지(§0-6) | 시스템 프롬프트에 "침수 등 위험 제보는 '위험!'처럼 공포를 조장하지 말고 '최근 침수 제보 있음, 우회 권장'처럼 순화하라"를 명시(단위테스트로 프롬프트 내용을 못 박음). |
| 격리 — survival_score/검색 시맨틱 불변 | `domain/ai` 별도 패키지. `PlaceResponse`에 aiSummary는 표시용 필드로만 추가 — `SurvivalScore` 계산·정렬·필터링 로직은 전혀 건드리지 않는다. |
| 타임아웃 짧게(2~3s) | `SimpleClientHttpRequestFactory` connect=1.5s·read=2.5s. |

## 검토한 대안(Alternatives)

| 대안 | 기각 이유 |
|---|---|
| **Anthropic 키가 생길 때까지 기능 보류** | §3/§6이 MVP로 지정한 기능을 인프라 공백 때문에 미루는 건 과도하게 보수적 — OpenRouter를 프라이머리로 쓰고 이탈을 투명하게 기록하는 쪽이 §0-B③(방어 가능성)을 충족하면서 스펙을 실현한다. |
| **OpenRouter 오토라우터(`openrouter/free`)** | 요청마다 실제 서빙 모델이 달라질 수 있어 MockRestServiceServer 계약 테스트·품질 예측 가능성이 떨어진다. 명시적 모델 ID가 테스트 가능성·재현성 면에서 낫다. |
| **유료 모델을 기본값으로(예: OpenRouter 경유 Gemini Flash)** | 곁다리 기능에 기본값으로 비용을 발생시키는 건 "AI는 곁다리, 비용 방어" 원칙과 어긋난다. 무료 모델을 기본값으로 두고, 품질이 부족하면 `OPENROUTER_MODEL` 환경변수만 바꿔 유료 모델로 전환할 수 있게 설정으로 열어뒀다. |
| **캐시 키에 제보 내용 해시 포함(제보가 바뀌면 즉시 캐시 무효화)** | 지시사항이 "장소별" 캐시를 명시했고, TTL 1~6h 안에서 일정 수준의 지연을 감수하는 게 설계 의도로 읽힌다. 해시 키는 구현 복잡도만 늘리고(과설계, §0-2) 캐시 히트율을 낮춰 비용 방어 효과를 해친다. |
| **제보 없을 때 "정보 부족" 정적 문구를 캐시** | 지시사항이 허용한 대안이지만, 정적 문구를 캐시하면 그 TTL 동안 새 제보가 들어와도 요약이 갱신되지 않는 부작용이 있다. "생성 안 함(null)"을 택해 프론트가 기존처럼 "최근 제보 없음"을 별도로 보여주게 하고, 텍스트 중복도 피했다. |

## 결과(Consequences)

- `GET /places/{id}` 응답에 `aiSummary`(nullable) 필드가 additive로 추가됨 — 기존 클라이언트(프론트)는
  필드를 무시해도 무방하고, 목록/반경/bounds 응답 스키마는 전혀 변경되지 않는다.
- AI 프로바이더가 Anthropic이 아니므로, CLAUDE.md §8을 "기본" 그대로 준수하지 않는다 — 이 ADR과
  WORKLOG가 그 이탈의 유일한 근거 문서다. Anthropic 키가 확보되면 `ai.openrouter.model`을
  `anthropic/*`로 바꾸는 최소 변경으로 전환 가능(OpenRouter가 Anthropic 모델도 라우팅), 또는
  `OpenRouterClient`를 Anthropic 공식 SDK로 완전히 교체하는 더 큰 리팩터도 가능(계약이 유사해 비용 낮음).
- Redis(ElastiCache)가 죽어도(TS-011/TS-012 하드닝된 `CacheErrorHandler`가 이미 처리) AI 요약은 매
  요청 새로 계산될 뿐 500을 내지 않는다 — 캐시는 비용 최적화이지 가용성 전제조건이 아니다.
- SSM 파라미터(`/geuneul/openrouter_api_key`)·태스크데프 secret 항목은 Terraform 코드로만 스캐폴드됨
  — `terraform apply`·태스크데프 재등록은 오케스트레이터가 별도로 실행해야 한다(코드/문서에 실제 키
  없음, 규칙 D).

## 근거(References)

- OpenRouter 무료 티어 요청 한도(20 req/min)·모델 라인업 회전 주의사항: OpenRouter 공식 블로그
  "Free LLM API in 2026: 13 Options Ranked and Compared"(openrouter.ai/blog).
- Qwen3 다국어(119개 언어) 지원 및 한중일 성능 우위: Qwen 공식 발표(x.com/Alibaba_Qwen), Qwen3
  Technical Report(arxiv.org/html/2505.09388v1).
- 무료 모델 ID 확인(`qwen/qwen3-next-80b-a3b-instruct:free`): openrouter.ai/qwen 모델 카탈로그(2026-07).
- CLAUDE.md §0-B(의사결정 프로토콜)·§0-8/9(AI는 곁다리)·§3/§6(AI 요약 MVP)·§8(AI 스택 기본값).
