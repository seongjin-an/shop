```text
resource 스키마의 키-값으로 제시된 환경일 때 파이프라인 작동이 된다고 보면 되는지?
그리고transform/saga_id 에서 statements, java sdk 에서 gRPC 로 받은 데이터 토대로 덮어쓴다는건지? 그 gRPC 로 넘어온 데이터 예시는?
attributes 의 스키마는..
```
하나씩 체크해줄게.

## 1. "resource의 키-값 환경일 때만 파이프라인이 작동"? → **틀렸음** ❌

혼동하고 있는 것 같아. `resource` processor는 **필터가 아니야**. 조건 없이 **무조건 실행**되면서 해당 키-값을 **덧붙이거나 덮어쓰는** 역할이야.

```yaml
resource:
  attributes:
    - key: deployment.environment
      value: local
      action: upsert
```

이 설정의 실제 동작:

```
[들어온 span]                            [나가는 span]
  service.name = "shop-order"              service.name = "shop-order"
  (그 외 아무것도 없음)          ──▶       deployment.environment = "local"  ← 추가됨
```

즉 **"조건"이 아니라 "행동"**. 모든 span에 `deployment.environment=local` 라벨을 붙여주는 도장 찍기 작업.

### 진짜 "조건부 실행"이 필요하면 `filter` processor

특정 환경에서만 통과시키고 싶으면 별도의 `filter` processor를 씀:

```yaml
processors:
  filter/only_prod:
    traces:
      span:
        - 'resource.attributes["deployment.environment"] != "prod"'
        # ↑ 이 조건에 해당하는 span은 drop (버림)
```

또는 pipeline 자체를 분기(route):

```yaml
# prod 환경용 pipeline / local 환경용 pipeline 따로 구성
```

**요약**: `resource`는 "라벨링", `filter`는 "걸러내기". 네가 생각한 "환경 조건부"는 filter의 역할.

---

## 2. "transform/saga_id가 Java SDK에서 gRPC로 받은 데이터를 덮어쓴다?" → **반쯤 맞음** 🟡

### 맞는 부분
- Spring Boot 앱의 OTel Java Agent가 span 데이터를 **OTLP 프로토콜 + gRPC 전송**으로 Collector의 4317 포트로 보냄. ✅
- 그 받은 span의 attribute를 transform이 덮어쓰는 게 맞음. ✅

### 보완할 부분
- "Java SDK"라기보다는 **OTel Java Agent**(auto-instrumentation). 우린 코드에 SDK를 안 심었고, `-javaagent:...jar`로만 붙였잖아.
- 덮어쓰는 게 아니라 **새 attribute를 추가**. 원본 `messaging.kafka.message.key`는 그대로 남고, `saga.id`가 새로 생김.

### gRPC로 넘어오는 span 데이터 예시

OTLP 프로토콜은 내부적으로 Protobuf. 사람이 읽을 수 있게 JSON으로 풀면 대략 이렇게 생겼어:

```json
{
  "resourceSpans": [
    {
      "resource": {
        "attributes": [
          { "key": "service.name",        "value": { "stringValue": "shop-order" } },
          { "key": "telemetry.sdk.name",  "value": { "stringValue": "opentelemetry" } },
          { "key": "telemetry.sdk.language", "value": { "stringValue": "java" } },
          { "key": "host.name",           "value": { "stringValue": "ansj-mac" } }
        ]
      },
      "scopeSpans": [
        {
          "scope": {
            "name":    "io.opentelemetry.kafka-clients-2.6",
            "version": "2.6.0-alpha"
          },
          "spans": [
            {
              "traceId": "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6",
              "spanId":  "d4e5f6a7b8c9d0e1",
              "name":    "order-created publish",
              "kind":    "SPAN_KIND_PRODUCER",
              "startTimeUnixNano": "1745058000000000000",
              "endTimeUnixNano":   "1745058000012000000",
              "attributes": [
                { "key": "messaging.system",              "value": { "stringValue": "kafka" } },
                { "key": "messaging.destination.name",    "value": { "stringValue": "order-created" } },
                { "key": "messaging.kafka.message.key",   "value": { "stringValue": "8f3c9e12-4a5b-..." } },
                                                       // ↑ sagaId 가 여기 박혀있음
                { "key": "messaging.kafka.partition.id",  "value": { "intValue":    "2" } },
                { "key": "net.peer.name",                 "value": { "stringValue": "localhost" } },
                { "key": "net.peer.port",                 "value": { "intValue":    "9094" } }
              ],
              "status": { "code": "STATUS_CODE_OK" }
            }
          ]
        }
      ]
    }
  ]
}
```

`transform/saga_id` 가 이 데이터에 대해 하는 일:

```
attributes["messaging.kafka.message.key"] 값을 읽음 → "8f3c9e12-4a5b-..."
                                                        │
                                                        ▼
attributes["saga.id"] 에 set (없으니까 새로 추가됨)   → "8f3c9e12-4a5b-..."
```

처리 후 span의 attributes는:

```json
[
  { "key": "messaging.system",            "value": {...} },
  { "key": "messaging.destination.name",  "value": {...} },
  { "key": "messaging.kafka.message.key", "value": { "stringValue": "8f3c..." } },  // 그대로
  { "key": "saga.id",                     "value": { "stringValue": "8f3c..." } },  // ⭐ 추가됨
  ...
]
```

이 가공된 span이 `otlp/tempo` exporter를 통해 Tempo로 재전송됨.

### 실제로 이 데이터를 직접 보려면

`debug` exporter의 verbosity를 올리면 Collector 로그에 그대로 찍힘:

```yaml
debug:
  verbosity: detailed
```

그리고 pipeline에 추가:
```yaml
exporters: [otlp/tempo, debug]
```

하면 `docker logs otel-collector` 로 위 JSON 같은 구조가 보임.

---

## 3. `attributes` processor 스키마 — 다시 차근차근

핵심: `attributes`는 `actions` **배열**을 가진다. 각 원소(action)는 **"어떤 key에 어떤 동작을 어떤 값으로 할지"**를 정하는 한 줄 명령.

### 기본 구조

```yaml
attributes/<별칭>:
  actions:              # 배열 (여러 개 가능, 위→아래 순서로 실행)
    - key: <대상 attribute 이름>
      action: <동작>
      <동작에 필요한 추가 필드들>
    - key: ...
      action: ...
```

### 가장 쉬운 예시 3개

#### 예시 A: 리터럴 값으로 attribute 추가

```yaml
attributes/add_env:
  actions:
    - key: app.team
      action: upsert
      value: "commerce"
```

동작: 모든 span에 `app.team="commerce"` 를 붙임. 단순.

#### 예시 B: 민감정보 삭제

```yaml
attributes/redact:
  actions:
    - key: http.request.header.authorization
      action: delete

    - key: db.statement
      action: hash          # 쿼리 원본 대신 해시로 대체
```

동작:
- `http.request.header.authorization` 키가 있으면 **제거**.
- `db.statement` 키가 있으면 값을 **SHA-1 해시로 교체** (쿼리 형태는 남되 원문은 감춰짐).

`delete`/`hash`는 `value`/`from_*` 필드가 **필요 없어** — key만 있으면 됨.

#### 예시 C: context(baggage)에서 값을 꺼내 붙이기 — 우리 프로젝트

```yaml
attributes/baggage:
  actions:
    - key: saga.id          # ← 이 이름의 attribute를
      action: upsert        # ← 있으면 갱신, 없으면 생성
      from_context: baggage.saga.id
      # ↑ 값의 출처: W3C Baggage header 의 "saga.id" 필드
```

한글로 풀어 쓰면:

> "들어온 span에 대해, 이 요청의 Baggage header 안에 `saga.id` 라는 항목이 실려있으면, 그 값을 꺼내서 span attribute의 `saga.id` 에 박아줘."

### 스키마 필드 조합 규칙

`action` 값에 따라 **필요한/금지되는 부가 필드가 다름**:

| action | 필요 필드 | 선택 필드 | 금지 필드 |
|---|---|---|---|
| `insert` | key + (value **또는** from_attribute **또는** from_context 중 하나) | — | 나머지 값 출처 |
| `update` | 동일 | — | — |
| `upsert` | 동일 | — | — |
| `delete` | key | — | value, from_* (붙이면 무시 또는 에러) |
| `hash` | key | — | value, from_* |
| `extract` | key + pattern | — | value, from_* |
| `convert` | key + converted_type | — | — |

**"값의 출처 3형제"**:
- `value: ...` — 리터럴 고정값
- `from_attribute: ...` — 다른 attribute에서 복사
- `from_context: ...` — 요청 metadata/baggage에서 복사

**세 개 중 정확히 하나**만 쓸 수 있음.

### 실전 감각 잡는 예시

여러 action을 조합한 케이스:

```yaml
attributes/enrich_and_clean:
  actions:
    # 1. baggage에서 tenant 추출
    - key: tenant.id
      action: upsert
      from_context: baggage.tenant_id

    # 2. 기존 user.id 를 user_id_hash 로 이름 변경 + 해시
    - key: user_id_hash
      action: upsert
      from_attribute: user.id
    - key: user.id
      action: delete
    - key: user_id_hash
      action: hash

    # 3. URL에서 host 뽑아내기
    - key: http.url
      action: extract
      pattern: ^https?://(?P<http_host>[^/]+)(?P<http_path>/.*)?$

    # 4. 내부 디버깅용 임시 attribute 제거
    - key: internal.debug.token
      action: delete
```

실행 순서는 **위에서 아래**로. 그래서 "복사 → 해시 → 원본 삭제" 같은 순차 처리가 가능.

### `attributes` vs `resource` vs `transform` 비교

| processor | 다루는 범위 | 스키마 스타일 | 표현력 |
|---|---|---|---|
| `attributes` | **span/log/metric attribute** | 간단한 action 나열 | 단일 key 대상 작업 |
| `resource` | **resource attribute** (서비스 레벨) | `attributes` 와 거의 동일 | 리소스 태깅 전용 |
| `transform` | 모든 레벨 (context 선택) | OTTL DSL | 표현식·조건문 가능 ⭐ 가장 강력 |

예를 들어 우리 `transform/saga_id`가 하는 "Kafka message key → saga.id 복사"도 사실 attributes processor로 못 함 — 왜냐하면 attributes의 `from_attribute`는 **현재 span의 attribute에서만** 복사 가능한데, OTel 관례상 `messaging.kafka.message.key`는 같은 span에 있긴 해. 그래서 이걸 쓸 수도 있었음:

```yaml
attributes/saga_from_kafka:
  actions:
    - key: saga.id
      action: upsert
      from_attribute: messaging.kafka.message.key
```

근데 문제는 **"key가 없을 때만 스킵"** 같은 조건이 attributes엔 없고, transform에는 `where ... != nil` 이 있어서 더 안전. 그래서 transform을 택한 거.

---

## 종합 체크

1. ❌ `resource`는 조건이 아니라 **태깅(라벨 추가)**. 조건부 실행은 `filter` processor.
2. 🟡 **OTel Java Agent**가 OTLP/gRPC로 Collector에 전달 → transform이 그 span data의 attribute를 가공 (추가). 예시 JSON 위에 있음.
3. ✅ 정리: **"actions 배열 = 한 줄 명령의 리스트"**, 각 명령은 **"어떤 key에(key), 무슨 동작을(action), 어떤 값으로(value/from_attribute/from_context)"** 의 3요소 조합. delete/hash는 값 출처 필요 없음.