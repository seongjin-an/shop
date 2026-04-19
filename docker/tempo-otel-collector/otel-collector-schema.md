각 스키마 필드가 받을 수 있는 **값의 종류와 의미**를 쭉 정리해줄게.

## 1. `transform` processor

### `trace_statements` / `log_statements` / `metric_statements`

값: **객체 배열** (각 원소가 "어느 레벨에서 무슨 변형을 할지" 하나의 단위).

셋 중 **시그널 타입에 맞는 것**을 선택:
- `trace_statements` — trace 파이프라인에서만 실행
- `log_statements` — log 파이프라인에서만
- `metric_statements` — metric 파이프라인에서만

trace 파이프라인에 `log_statements`를 써도 동작 안 함(경고도 안 뜸).

### `context` (열거형)

OTTL이 **어느 계층의 데이터를 다루는지** 지정. 선택지와 의미:

| 값 | 어느 레벨 | 주 용도 예시 |
|---|---|---|
| `resource` | Resource (서비스/호스트 공통 태그) | `service.name`, `deployment.environment` 조작 |
| `scope` | Instrumentation Scope (계측 라이브러리) | 계측 라이브러리 이름별 처리 |
| `span` | 개별 span | span attribute, 이름, status 조작 ⭐ 우리가 쓰는 것 |
| `spanevent` | span 안의 event | 예외 event 처리 |
| `metric` | metric 정의 | metric 이름/단위 변경 |
| `datapoint` | metric의 데이터 포인트 | 실제 값·라벨 조작 |
| `log` | log record | log body/attribute 조작 |

`context: span`이면 statements 안에서 `attributes["..."]`는 **span attribute**를 의미. `context: resource`면 **resource attribute**를 의미. 같은 표현식이어도 context에 따라 대상이 달라짐.

### `statements` (OTTL 함수 배열)

값: **OTTL DSL 문자열 배열**. 주요 함수와 연산자:

| OTTL 함수 | 하는 일 | 예시 |
|---|---|---|
| `set(target, value)` | 값을 덮어쓴다 | `set(attributes["saga.id"], attributes["messaging.kafka.message.key"])` |
| `delete_key(map, key)` | 특정 키 삭제 | `delete_key(attributes, "secret.token")` |
| `delete_matching_keys(map, pattern)` | 정규식으로 여러 키 삭제 | `delete_matching_keys(attributes, "^temp\\..*")` |
| `keep_keys(map, keys[])` | 명시한 키만 남기고 삭제 | `keep_keys(attributes, ["http.method", "http.url"])` |
| `limit(map, n, keys[])` | attribute 개수 제한 | `limit(attributes, 10, [])` |
| `truncate_all(map, maxLen)` | 값 길이 제한 | `truncate_all(attributes, 1000)` |
| `replace_pattern(target, regex, replacement)` | 정규식 치환 | `replace_pattern(name, "^GET /users/\\d+$", "GET /users/:id")` |

**연산자와 리터럴**:
- `==`, `!=`, `>`, `<`, `>=`, `<=`, `and`, `or`, `not`
- `nil` — null (우리 config의 `!= nil`이 이것)
- 문자열 리터럴은 `"..."`
- `where <조건>` — SQL의 WHERE처럼 조건부 실행

예시 해석:
```
set(attributes["saga.id"], attributes["messaging.kafka.message.key"])
    where attributes["messaging.kafka.message.key"] != nil
```
→ "messaging.kafka.message.key가 있으면, 그 값을 saga.id 에 복사"

---

## 2. `attributes` processor

### `actions[].action` (열거형)

| 값 | 하는 일 | 필요한 부가 필드 |
|---|---|---|
| `insert` | **없을 때만** 새로 만든다 | `value` 또는 `from_attribute`/`from_context` |
| `update` | **있을 때만** 값을 바꾼다 | `value` 또는 `from_...` |
| `upsert` | 있으면 update, 없으면 insert (가장 무난) ⭐ | `value` 또는 `from_...` |
| `delete` | 해당 key를 제거 | (없음) |
| `hash` | 값을 SHA-1로 해싱 (PII 보호) | (없음) |
| `extract` | 정규식으로 값에서 새 attribute 추출 | `pattern` |
| `convert` | 타입 변환 (string↔int 등) | `converted_type` |

### `actions[].key`
대상 attribute의 **이름 문자열**. OTel semantic convention을 따르면 `http.method`, `db.statement` 식의 점(dot) 표기.

### `actions[].value` (literal 값)
리터럴을 바로 꽂을 때. 예: `value: "prod"`, `value: 8080`, `value: true`.

### `actions[].from_attribute`
**다른 attribute의 값**을 복사. 예:
```yaml
- key: user.id
  action: upsert
  from_attribute: enduser.id    # enduser.id 값을 user.id 로 복사
```

### `actions[].from_context` ⭐
**context metadata**에서 값을 가져옴. 접근 가능한 네임스페이스:

| 네임스페이스 | 내용 예시 |
|---|---|
| `metadata.<header>` | gRPC/HTTP metadata, 예: `metadata.x-tenant-id` |
| `auth.<key>` | 인증 미들웨어가 심은 정보 |
| `baggage.<key>` | W3C baggage로 전파된 값 ⭐ 우리가 쓰는 것 |
| `client.address` | 원격지 IP |

우리 config:
```yaml
from_context: baggage.saga.id
```
→ HTTP header `baggage: saga.id=xxx` 로 전파된 값을 꺼내 span attribute에 박음.

### `actions[].pattern` (extract 전용)
값: **named capture group** 정규식.
```yaml
- key: http.url
  action: extract
  pattern: ^https://(?P<http_host>[^/]+)(?P<http_path>/.*)$
```
→ `http_host`, `http_path` 라는 새 attribute 2개 자동 생성.

---

## 3. `batch` processor

### `timeout`
값: **duration 문자열** (`s`, `ms`, `m`, `h` 단위).

예: `2s`, `500ms`, `1m`.

"이 시간 안에 배치가 안 차면 **그냥** 보낸다." 낮추면 지연 ↓, 요청 수 ↑.

### `send_batch_size`
값: **정수** (span/log/metric record 개수).

"이 개수가 먼저 차면 즉시 보낸다."

예: `1024`면 1024개 모이자마자 전송. 초당 10k span 환경이면 ~100ms마다 전송.

### `send_batch_max_size` (선택)
값: **정수**. 기본 0(제한 없음).

배치 **최대 크기 상한**. 이보다 큰 배치가 만들어지면 쪼개서 보냄. exporter/백엔드가 한 번에 받을 수 있는 크기 제한이 있을 때 사용. 기본은 `send_batch_size`만 신경쓰면 됨.

### `metadata_keys` (선택)
값: **문자열 배열**. 다중 테넌트 환경에서 특정 metadata(예: tenant id)별로 **별도 배치**를 만들고 싶을 때.

```yaml
batch:
  metadata_keys: [x-tenant-id]
```

---

## 4. `resource` processor

### `attributes[].key`
**Resource attribute 이름**. 예: `service.name`, `service.version`, `deployment.environment`, `host.name`, `k8s.pod.name`.

OTel semantic convention에 표준 키 목록이 있음 — 가급적 그 이름을 따라야 Grafana/Jaeger 등이 알아서 인식함.

### `attributes[].value`
리터럴 값. 우리 config의 `value: local` 같은 것.

### `attributes[].action`
`attributes` processor와 동일 (`insert`/`update`/`upsert`/`delete`/`hash`/`extract`).

---

## 5. `receivers.otlp` — 수신 프로토콜 값

### `protocols.grpc.endpoint` / `protocols.http.endpoint`
값: **`host:port` 문자열**.

- `0.0.0.0:4317` — 모든 네트워크 인터페이스에서 수신 (컨테이너 표준).
- `127.0.0.1:4317` — 루프백만. 사이드카 패턴에 유용.
- `localhost:4317` — 상동.

### 추가로 자주 나오는 하위 필드

| 필드 | 의미 | 값 예시 |
|---|---|---|
| `tls.insecure` | TLS 끄기 | `true`/`false` |
| `tls.cert_file` / `tls.key_file` | mTLS 인증서 경로 | `"/certs/server.crt"` |
| `max_recv_msg_size_mib` | 메시지 최대 크기 | `16` (MiB 단위) |
| `include_metadata` | gRPC/HTTP metadata를 context에 전달 | `true`(baggage 쓰려면 필수) |

---

## 6. `exporters.otlp` / `otlphttp`

### `endpoint`
값: **접속할 백엔드 주소**.

- `otlp` (gRPC): `host:port` — 우리 `tempo:4317`
- `otlphttp`: **URL** — 우리 `http://loki:3100/otlp`

gRPC exporter는 스킴 없는 `host:port`, HTTP exporter는 `http://` / `https://` 포함.

### `tls.insecure` (열거형 bool)
- `true` — 평문 통신 (로컬/VPC 내부)
- `false` — TLS 검증 수행 (프로덕션)

### `compression`
값: 열거형 문자열.

- `none` — 압축 없음 (기본)
- `gzip` — 네트워크 절약, CPU 소비 ↑
- `zstd` / `snappy` — (exporter별 지원 여부 다름)

### `headers`
값: **key-value 맵**. 인증 토큰이나 tenant 식별에 씀.

```yaml
headers:
  X-Scope-OrgID: "shop-prod"     # Tempo/Mimir multi-tenant
  Authorization: "Bearer xxx"
```

### `sending_queue`
값: **객체**. 장애 대비 큐.

```yaml
sending_queue:
  enabled: true
  num_consumers: 10        # 병렬 전송 워커 수
  queue_size: 5000         # 대기 큐 크기 (span 단위)
```

백엔드가 잠시 죽어도 큐에 담았다가 재전송.

### `retry_on_failure`
값: **객체**.

```yaml
retry_on_failure:
  enabled: true
  initial_interval: 1s
  max_interval: 30s
  max_elapsed_time: 5m
```

Exponential backoff 재시도. 1초 → 2초 → 4초 → ... → 30초(max) 식.

---

## 7. `debug` exporter

### `verbosity`
값: 열거형.

- `basic` — "몇 건 받았다" 정도만 주기적으로 로깅 ⭐ 우리 설정
- `normal` — 각 배치의 요약 (trace id, span 수)
- `detailed` — 모든 attribute/event까지 전부 (디버깅용, 실전에선 로그 폭증)

### `sampling_initial` / `sampling_thereafter`
값: **정수**. 로그 샘플링.

`sampling_initial: 5`, `sampling_thereafter: 500` → "첫 5건은 전부 찍고, 이후엔 500건에 1건만 찍음".

---

## 8. `service` 섹션

### `service.telemetry.logs.level` (열거형)
- `debug` / `info` / `warn` / `error`

Collector **자기 자신의 로그** 레벨.

### `service.telemetry.metrics.level`
Collector 자체 메트릭(8888 포트) 상세도.

- `none` — 메트릭 노출 끔
- `basic` — 핵심 지표만
- `normal` ⭐ 기본값
- `detailed` — 내부 카운터까지 전부

### `service.pipelines.<signal>` 내부

- `receivers: [...]` — **이름 배열**. 선언한 receiver 중 이 파이프라인이 데이터 받을 소스.
- `processors: [...]` — **이름 배열, 순서 의미 있음**. 왼쪽→오른쪽 순으로 적용.
- `exporters: [...]` — **이름 배열**. 이 파이프라인이 보낼 백엔드들. 여러 개 쓰면 **fan-out** (예: Tempo + Jaeger 동시 전송).

`<signal>` 값은 `traces`, `logs`, `metrics` 셋 중 하나 (또는 `traces/`, `traces/sampled` 식으로 별칭 가능).

---

## 9. 값 타입 총정리 치트시트

| YAML 타입 | 어디서 보나 | 예시 |
|---|---|---|
| **string** | endpoint, key, 대부분의 스칼라 | `"0.0.0.0:4317"`, `"saga.id"` |
| **duration** | timeout, interval | `2s`, `500ms`, `1m`, `1h` |
| **size (ByteSize)** | max_recv_msg_size, max_line_size | `256kb`, `4MiB`, `1GiB` |
| **int** | send_batch_size, queue_size, port | `1024` |
| **bool** | enabled, insecure, truncate | `true`, `false` |
| **enum** | action, context, verbosity | 위 표들의 예약 값 중 하나 |
| **list** | actions, statements, processors | `[batch, resource]` |
| **map** | headers, attributes | `{X-Scope-OrgID: shop-prod}` |

---

## 우리 config 전체를 값까지 포함해 한 문장씩 읽으면

```yaml
transform/saga_id:              # 전용 별칭의 transform 인스턴스
  trace_statements:             # trace에 대해서만 실행
    - context: span             # span 레벨의 attribute 대상
      statements:               # OTTL 문장 1개
        - set(attributes["saga.id"],                      # "saga.id" 키를 덮어써라
              attributes["messaging.kafka.message.key"])  # 그 값은 kafka key로
              where attributes["messaging.kafka.message.key"] != nil  # kafka key가 있을 때만

attributes/baggage:             # 전용 별칭의 attributes 인스턴스
  actions:                      # 액션 1개
    - key: saga.id              # 대상 key
      action: upsert            # 있으면 갱신, 없으면 추가
      from_context: baggage.saga.id   # 값은 baggage의 saga.id에서

batch:
  timeout: 2s                   # 2초마다 강제 전송
  send_batch_size: 1024         # 1024개 차면 즉시 전송

resource:
  attributes:
    - key: deployment.environment   # 리소스에 이 key를
      value: local                  # "local" 값으로
      action: upsert                # upsert

exporters:
  otlp/tempo:
    endpoint: tempo:4317        # Tempo의 gRPC OTLP 수신 포트
    tls:
      insecure: true            # 로컬 plaintext

  otlphttp/loki:
    endpoint: http://loki:3100/otlp   # Loki 3.0의 OTLP HTTP

service:
  telemetry:
    logs:
      level: info               # Collector 자체 로그 info 레벨
  pipelines:
    traces:
      receivers: [otlp]         # OTLP receiver에서 받아서
      processors: [batch, resource, transform/saga_id, attributes/baggage]  # 이 순서로 처리하고
      exporters: [otlp/tempo]   # Tempo로 보냄
    logs:
      receivers: [otlp]
      processors: [batch, resource]
      exporters: [otlphttp/loki]  # Loki로 보냄
```

핵심: **키 이름은 타입이 정한 스키마대로, 값은 위 표의 타입/열거형/리터럴** 중에서 고르는 구조다.