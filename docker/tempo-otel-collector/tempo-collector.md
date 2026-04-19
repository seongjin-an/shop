좋은 질문이야. 각 포트와 설정 속성을 하나씩 뜯어볼게. (이 설정들은 분석만 하고, 수정/보강은 하지 않을게.)

## 1. 포트 정리

### Tempo 포트

| 컨테이너 내부 포트 | 호스트 매핑 | 역할 |
|---|---|---|
| **3200** (HTTP) | 13200 | Tempo의 **쿼리 API + UI**. Grafana가 여기로 붙어서 trace를 읽어간다. TraceQL 쿼리(`{ saga.id = "..." }`), trace ID 조회, service graph API 전부 이 포트. |
| **4317** (gRPC) | 14317 | OTLP **수신** 포트. Tempo도 OTLP를 직접 받을 수 있다. 근데 지금 구성에선 **사용 안 함** — 서비스는 Collector로 보내고, Collector가 docker network 내부로 `tempo:4317`에 넣어줌. `14317` 외부 매핑은 남겨뒀지만 실제론 안 쓰는 상태. |
| (4318 HTTP) | 매핑 안함 | OTLP HTTP 수신 포트. tempo-config에 선언은 돼있지만 외부 매핑 없음. |

즉, **실제 트래픽 흐름**:
```
Spring Boot 앱 → localhost:24317 (Collector)  →  tempo:4317 (docker net)  →  Grafana ← localhost:13200 (Tempo 읽기)
```

### OTel Collector 포트

| 컨테이너 내부 | 호스트 매핑 | 역할 |
|---|---|---|
| **4317** (gRPC) | 24317 | OTLP **gRPC 수신** — 서비스들이 여기로 trace를 보냄. 네 `build.gradle`의 `OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:24317` 가 이걸 가리킴. |
| **4318** (HTTP) | 24318 | OTLP **HTTP 수신** — gRPC를 못 쓰는 환경(브라우저, 일부 프록시 뒤)에서 쓰는 대안. shop-frontend에서 FE telemetry를 붙일 거면 여기 쓸 자리. |
| **8888** | 28888 | **Collector 자기 자신의** Prometheus 메트릭. Collector가 초당 span 몇 개 받았는지, 몇 개 drop 했는지, export 실패 몇 번인지 등을 노출. |

8888 포트에 접속해보면 이런 게 나옴 (Collector 건강 상태 감시용):
```
otelcol_receiver_accepted_spans{receiver="otlp", ...} 12345
otelcol_exporter_sent_spans{exporter="otlp/tempo"} 12344
otelcol_exporter_send_failed_spans{exporter="otlp/tempo"} 1   ← 이게 늘어나면 tempo 장애
```
나중에 Prometheus 붙이면 이걸 스크랩해서 "내 관측성 스택 자체의 상태"를 대시보드로 만들 수 있다.

### 한 장으로 정리하면

```
[Spring Boot App(5개)]
       │ (OTLP gRPC)
       ▼  :24317 (host) → :4317 (container)
┌──────────────────────┐
│ OTel Collector       │  :8888 ← Prometheus가 긁는 Collector 자체 메트릭
│   (4317 / 4318)      │  :4318 ← HTTP OTLP (예비)
└────┬────────────┬────┘
     │ tempo:4317 │ loki:3100/otlp
     ▼            ▼
 ┌────────┐   ┌──────┐
 │ Tempo  │   │ Loki │
 │ :3200  │   │ :3100│
 └────┬───┘   └──┬───┘
      │          │
      └────┬─────┘
           ▼
      ┌─────────┐
      │ Grafana │ :13000
      └─────────┘
```

---

## 2. `tempo-config.yml` 속성별 의미

### `server.http_listen_port: 3200`
Tempo HTTP API/UI 포트. Grafana 데이터소스 URL이 `http://tempo:3200`을 바라봐야 함.

### `distributor.receivers.otlp`
Tempo의 수신기 설정. **distributor**는 Tempo 아키텍처의 맨 앞단(수신·라우팅 담당). 여기서 OTLP를 열어두면 Collector 없이도 앱에서 직접 Tempo로 보낼 수 있다. 우리는 Collector를 경유하므로 이 엔드포인트는 Collector가 `tempo:4317`로 접속할 때 쓰임.

- `grpc: 0.0.0.0:4317` — Collector의 `exporters.otlp/tempo.endpoint: tempo:4317`가 여기로 접속.
- `http: 0.0.0.0:4318` — 예비 HTTP 경로.

### `ingester`
**ingester**는 받은 span을 메모리에 쌓다가 block 파일로 flush하는 컴포넌트.

- `max_block_duration: 5m` — 5분 동안 받은 trace들을 하나의 block으로 묶어 디스크에 씀. 프로덕션은 보통 30m~1h. 로컬은 빨리 보이게 짧게.
- `trace_idle_period: 10s` — 한 trace에 **10초간 새 span이 안 들어오면** 그 trace를 "종료됐다"고 판단하고 block에 flush. 너무 길면 "왜 Grafana에 안 보이지?" 하고 기다려야 함.

예시: 주문 saga가 1분간 진행되는 경우, 마지막 span 이후 10초 더 기다린 뒤 block에 기록 → Grafana에서 보이려면 block flush까지 추가로 기다려야 함.

### `compactor.compaction.block_retention: 24h`
디스크에 쌓인 block을 24시간 후 삭제. 로컬 개발 기준. 프로덕션은 보통 7~30일, S3 object lifecycle로 관리.

### `storage.trace`
- `backend: local` — 로컬 디스크 저장 (개발용). 프로덕션은 `s3`, `gcs`, `azure` 씀.
- `wal.path: /var/tempo/wal` — **Write-Ahead Log**. ingester가 메모리에 쌓는 동안 크래시 시 복구용 저널. MySQL의 redo log와 같은 역할.
- `local.path: /var/tempo/blocks` — flush 완료된 block들의 실제 저장소.

compose에서 `./.data/tempo:/var/tempo`로 호스트 마운트 → 컨테이너 재시작해도 trace 살아남음.

### `metrics_generator`
Tempo의 **킬러 피처**. span들을 보고 두 가지를 Prometheus metric으로 자동 생성:

- `service_graphs` — "shop-order → shop-stock으로 요청 몇 건, 평균 latency 얼마, 에러율 얼마" 같은 서비스 간 호출 그래프. Grafana의 Service Graph 뷰가 이걸로 그려짐.
- `span_metrics` — RED(Rate, Error, Duration) 메트릭을 span으로부터 자동 생성. 예: `traces_spanmetrics_latency_bucket{service="shop-order", operation="POST /orders"}`

### `overrides.defaults.metrics_generator.processors: []`
**현재 비어있음.** `service_graphs`/`span_metrics` 처리기를 "선언"은 했지만 **테넌트 레벨에서 활성화 안 함**. Prometheus/Mimir 붙일 때 `[service-graphs, span-metrics]`로 바꾸면 그때부터 실제로 메트릭 생성.

주석(`# prometheus 추가 시 [service-graphs, span-metrics]`)이 이 의미.

---

## 3. `otel-collector-config.yml` 속성별 의미

Collector는 **Receiver → Processor → Exporter** 파이프라인 구조.

### `receivers.otlp`
위 포트 섹션 설명과 동일. gRPC/HTTP 두 문을 열어둠.

### `processors.batch`
span을 하나씩 내보내면 비효율 → **배치**로 묶어서 내보냄.

- `timeout: 2s` — 최대 2초 기다렸다가 전송.
- `send_batch_size: 1024` — 1024개 모이면 즉시 전송.

예시: 초당 500 span이 들어오면 2초도 안 돼서 1024개 채워지므로 ~2초마다 전송. 초당 50 span이면 timeout에 걸려 2초마다 전송.

**Best practice**: `batch`는 모든 pipeline에 반드시 넣기. 안 넣으면 1 span당 gRPC 호출 1번 → Tempo 부하 폭증.

### `processors.resource`
**리소스 속성**(span/log 공통)에 태깅.

```yaml
- key: deployment.environment
  value: local
  action: upsert
```

→ 모든 span에 `deployment.environment=local` 태그 추가. 나중에 staging/prod 배포하면 env별 값만 바꾸면 Grafana에서 `{ resource.deployment.environment = "prod" }` 로 필터링 가능.

`action`:
- `insert`: 없을 때만 추가
- `update`: 있을 때만 갱신
- `upsert`: 있으면 갱신, 없으면 추가 (가장 안전)
- `delete`: 삭제

### `processors.transform/saga_id` ⭐

네 프로젝트의 핵심 로직.

```yaml
- set(attributes["saga.id"], attributes["messaging.kafka.message.key"])
    where attributes["messaging.kafka.message.key"] != nil
```

**OTTL**(OpenTelemetry Transformation Language) 문법. SQL의 UPDATE ... WHERE 느낌.

동작:
1. OTel Java Agent가 Kafka producer/consumer span에 `messaging.kafka.message.key` 를 자동으로 붙임 (네 코드에서 `kafkaTemplate.send(topic, sagaId.toString(), json)` 했으므로 key = sagaId).
2. Collector가 이 값을 읽어서 `saga.id` attribute로 **복사**.
3. 결과: Grafana Tempo에서 `{ saga.id = "8f3c..." }` 쿼리로 전 서비스 span 조회 가능.

`context: span` — span attribute 대상으로 작동 (다른 선택지: `resource`, `spanevent`).

### `processors.attributes/baggage`

```yaml
- key: saga.id
  action: upsert
  from_context: baggage.saga.id
```

Baggage는 W3C propagation 표준으로, HTTP header `baggage: saga.id=...` 에 실려 서비스 간 전달되는 key-value. OTel Agent가 이걸 자동으로 context에 실어주면 Collector는 `from_context: baggage.saga.id`로 꺼내 span attribute에 박을 수 있다.

**transform/saga_id와의 차이**:
- `transform/saga_id`: Kafka span 전용 (messaging key → saga.id)
- `attributes/baggage`: HTTP/DB/모든 span 대상. baggage로 전파되면 Kafka를 안 거치는 경로에서도 `saga.id` 부여 가능.

두 개가 **상호보완적**으로 작동해서 비동기든 동기든 전부 커버.

### `exporters`

- `otlp/tempo` — Tempo로 trace 전송. `tls.insecure: true`는 로컬 plain text (프로덕션은 mTLS).
- `otlphttp/loki` — Loki의 OTLP endpoint (`/otlp`)로 log 전송. Loki 3.0부터 OTLP 로그를 네이티브로 받음. 이 경로로 들어온 로그는 자동으로 structured metadata가 매핑돼서 `trace_id` 같은 게 그대로 검색 가능.
- `debug` — Collector 자기 로그로 span 샘플을 뱉음. `verbosity: basic`은 건수만, `detailed`는 전체 내용. 파이프라인 디버깅용.

### `service.pipelines`

**핵심**: Receiver/Processor/Exporter는 선언만으로 동작하지 않는다. `pipelines`에 엮어야 쓰임.

```yaml
traces:
  receivers: [otlp]
  processors: [batch, resource, transform/saga_id, attributes/baggage]
  exporters: [otlp/tempo]
```

**processor 순서가 중요**:
1. `batch` — 먼저 묶어서 이후 단계 오버헤드 감소.
2. `resource` — 공통 태그 부여.
3. `transform/saga_id` — Kafka span에 saga.id 추가.
4. `attributes/baggage` — 3에서 못 붙인 span에 baggage 기반으로 saga.id 추가.
5. `otlp/tempo` exporter로 전송.

`logs` 파이프라인은 transform/baggage 없이 `batch, resource`만 — 로그는 MDC 덕에 이미 trace_id가 박혀있어서 별도 가공이 불필요.

### `service.telemetry.logs.level: info`
Collector **자기 자신**의 로그 레벨. `debug`로 올리면 trace/processor 디버그 정보가 풍부. 문제 생겼을 때 잠깐 켰다 끄는 용도.

---

## 4. 부수적으로 알면 좋은 것들

### Grafana feature toggle (compose에서 본 것)

```
GF_FEATURE_TOGGLES_ENABLE=traceqlEditor traceToMetrics
```

- `traceqlEditor` — Grafana Explore에서 TraceQL 쿼리 편집 UI 활성화.
- `traceToMetrics` — Tempo span metrics(위 metrics_generator)를 Grafana에서 자동 링크. Prometheus 붙이면 활성화 효과.

### Collector contrib vs core

이미지: `otel/opentelemetry-collector-contrib:0.103.0`
"contrib"이 붙은 이유는 **community-maintained receiver/processor/exporter 포함**. `transform`, `attributes`, `otlphttp/loki` 등 여기서 쓰는 것들 상당수가 contrib-only. Core 이미지에는 없어서 `exporter not found` 에러 나면 대부분 이미지 선택 실수.

### tempo의 4317 포트 매핑을 굳이 왜 열어뒀나

compose에 `14317:4317` 매핑이 있지만 주석에 "미사용, collector 경유"라고 돼있음. 디버깅용으로 남겨둔 듯. **앱이 Collector를 건너뛰고 Tempo로 직접 보내야 할 때**(예: Collector가 죽었을 때 임시 우회) 유용. 프로덕션은 이 포트 닫는 게 원칙(보안).

### Collector를 두는 이유 (앱 → Tempo 직결 vs Collector 경유)

| 항목 | 직결 | Collector 경유 ⭐ |
|---|---|---|
| 엔드포인트 변경 시 | 앱 N개 재배포 | Collector config만 수정 |
| 여러 백엔드 전송 (Tempo + Jaeger + 관리형) | 앱마다 다중 설정 | Collector에서 fan-out |
| 샘플링/필터링/attribute 가공 | 앱마다 코드 수정 | Collector processor 한 곳 |
| Kafka key → saga.id 같은 후처리 | 불가 (앱이 매번 직접) | ✅ 우리가 하고 있는 것 |

지금 `transform/saga_id`가 정확히 이 이점을 뽑아먹는 케이스.

### "saga.id" 네이밍의 OTel 컨벤션

OTel semantic convention은 **점(dot) 표기**를 씀 (`http.method`, `messaging.kafka.message.key`). 그래서 네 비즈니스 key도 `sagaId`가 아닌 `saga.id`로 둔 것. Tempo의 TraceQL은 점을 지원하므로 `{ saga.id = "..." }` 가능.

---

## 요약 한 줄씩

- Tempo **3200**: 읽기용 (Grafana가 query)
- Tempo **4317**: OTLP 쓰기 (Collector가 push, 지금은 docker net 내부로만)
- Collector **4317/4318**: 앱이 trace/log 보내는 문
- Collector **8888**: Collector 자기 건강 상태 (Prometheus용)
- Tempo config 핵심: ingester flush 주기, 저장 backend, metrics_generator (아직 비활성)
- Collector config 핵심: `transform/saga_id` 로 Kafka key → saga.id 승격 (우리 프로젝트의 킬러 로직)