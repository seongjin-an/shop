맞아, 정확히 반반 섞여있어. 정리해줄게.

## 네이밍 규칙: `<타입>/<별칭>`

OTel Collector config에서 processor/receiver/exporter 이름은 **`타입/별칭` 형식**이다.

```yaml
transform/saga_id
↑        ↑
│        └── 별칭 (alias): 내가 임의로 짓는 이름
└─────────── 타입 (type): Collector가 아는 **예약어**
```

- `transform`, `attributes`, `batch`, `resource`, `otlp`, `otlphttp`, `debug` — **예약어** (각각이 특정 processor/exporter 구현체)
- `/saga_id`, `/baggage` — **내 맘대로** (단지 구분 라벨)

### 왜 별칭을 붙이나?

**같은 타입의 processor를 여러 개 쓰고 싶을 때** 구분하려고.

예시: `attributes` processor 2개를 다른 용도로 쓴다고 하자.

```yaml
processors:
  attributes/baggage:          # 1번 인스턴스
    actions:
      - key: saga.id
        action: upsert
        from_context: baggage.saga.id

  attributes/pii_redact:       # 2번 인스턴스
    actions:
      - key: user.email
        action: delete
      - key: user.phone
        action: delete

service:
  pipelines:
    traces:
      processors: [batch, attributes/baggage, attributes/pii_redact]
```

별칭이 없으면 `attributes:` 블록이 두 번 나와서 YAML 파싱 에러. 그래서 `/saga_id`, `/baggage` 같은 구분자가 필요한 거.

### 별칭이 필요 없으면 생략 가능

처음 쓰는 코드에서 `batch`는 `batch/앙뭐시기`가 아니라 그냥 `batch`로 쓰고 있잖아? 인스턴스가 하나면 별칭 생략해도 됨.

```yaml
processors:
  batch:              # 별칭 없음 (유일한 batch)
    timeout: 2s

  batch/slow:         # 별칭 있음 (다른 설정의 batch)
    timeout: 30s
    send_batch_size: 10000
```

---

## 하위 키들: 타입별로 **정해진 스키마**

처리기 내부의 키는 내가 짓는 게 아니고 **해당 타입의 문서에 정의된 필드**다. 타입마다 스키마가 달라서 같은 개념도 이름이 다르다.

### `transform` processor의 스키마

```yaml
transform/saga_id:
  trace_statements:      # ← transform의 예약 필드 (또는 log_statements, metric_statements)
    - context: span      # ← transform의 예약 필드 (OTTL context)
      statements:        # ← transform의 예약 필드
        - set(...)       # ← OTTL 함수 (set, delete_key, limit, truncate_all 등은 OTTL 예약 함수)
```

- `trace_statements` / `log_statements` / `metric_statements` — **시그널 타입별 진입점**. 트레이스면 `trace_statements`.
- `context` — OTTL이 어떤 레벨에서 작동할지. 허용 값: `resource`, `scope`, `span`, `spanevent`, `metric`, `datapoint`, `log`. **이 값들은 열거형 예약어**.
- `statements` — OTTL 문장 배열. 내용은 OTTL DSL.

### `attributes` processor의 스키마

```yaml
attributes/baggage:
  actions:               # ← attributes의 예약 필드
    - key: saga.id       # ← 액션의 예약 필드 (대상 attribute 이름)
      action: upsert     # ← 예약 필드, 값은 열거형 (insert/update/upsert/delete/hash/extract)
      from_context: baggage.saga.id   # ← 예약 필드 (값의 출처)
```

`attributes`는 `trace_statements`가 없고 `actions`가 있다. **타입이 다르면 스키마도 완전히 다름**.

### `batch`의 스키마

```yaml
batch:
  timeout: 2s            # ← batch의 예약 필드
  send_batch_size: 1024  # ← batch의 예약 필드
  send_batch_max_size: 2048   # ← 예약 필드 (선택)
```

### `resource`의 스키마 (attributes와 거의 동일)

```yaml
resource:
  attributes:            # ← resource의 예약 필드 (attributes/... 의 `actions` 와 비슷한 역할이지만 이름 다름)
    - key: deployment.environment
      value: local
      action: upsert
```

어, 재밌는 함정: `resource`는 `attributes:`라는 키를 쓰고, `attributes/...` 프로세서는 `actions:`를 쓴다. **같은 아이디어, 다른 이름** — 이게 "타입별 스키마"의 의미.

---

## 그래서 "예약어 vs 별칭" 최종 정리

### 내가 짓는 것 (자유)
- `/saga_id`, `/baggage`, `/pii_redact` 같은 **처리기 인스턴스 별칭**
- `saga.id` 같은 **attribute 이름** (OTel 컨벤션은 있지만 기술적으론 자유)
- `deployment.environment` 의 `value: local` — 값 자체는 자유

### OTel이 정한 예약어
- 섹션 키: `receivers`, `processors`, `exporters`, `extensions`, `connectors`, `service`, `pipelines`, `telemetry`
- 처리기 타입: `batch`, `transform`, `attributes`, `resource`, `filter`, `tail_sampling`, `memory_limiter`, ...
- Exporter 타입: `otlp`, `otlphttp`, `debug`, `prometheus`, `kafka`, ...
- 각 타입의 하위 필드: `trace_statements`, `log_statements`, `actions`, `timeout`, `endpoint`, `tls`, `protocols`, ...
- 열거형 값: `context: span|resource|scope|...`, `action: insert|update|upsert|delete|hash|extract`
- OTTL 함수/구문: `set(...)`, `delete_key(...)`, `where`, `==`, `!=`, `nil`, ...

### 파이프라인 참조 시 반드시 "타입/별칭" 전체 이름

```yaml
service:
  pipelines:
    traces:
      processors: [batch, resource, transform/saga_id, attributes/baggage]
      #           ↑      ↑         ↑                  ↑
      #           별칭없음 별칭없음   별칭있음 (전체이름) 별칭있음 (전체이름)
```

여기서 `saga_id`만 쓰면 에러 — 반드시 **앞의 타입까지** 전체 이름으로 참조해야 한다.

---

## 한 가지 더: 어떻게 스키마를 확인하나

OTel Collector는 GitHub `opentelemetry-collector-contrib/processor/<타입>processor/README.md`에 각 processor의 config schema가 있음. 예:

- transform: `processor/transformprocessor/README.md`
- attributes: `processor/attributesprocessor/README.md`
- filter: `processor/filterprocessor/README.md`

OTTL 문법은 별도로 `pkg/ottl/README.md`. context별로 쓸 수 있는 함수가 다른 것도 여기 명시돼 있다.

---

## 요약

- `transform`, `attributes`, `batch` = **타입명**(예약어)
- `/saga_id`, `/baggage` = **별칭**(자유, 여러 인스턴스 구분용)
- `trace_statements`, `context`, `statements`, `actions`, `key`, `action`, `from_context` = **해당 타입의 스키마 필드**(예약어)
- 같은 개념이라도 타입이 다르면 키 이름이 다를 수 있음 (`resource.attributes` vs `attributes.actions`)