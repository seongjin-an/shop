# OTel Native Logs Pipeline — Promtail 제거 마이그레이션

**적용일**: 2026-04-19
**목적**: Promtail 경로를 폐기하고 OTel Agent → Collector → Loki(OTLP) 단일 경로로 통합

## 변경 사항 요약

| 대상 | 변경 |
|---|---|
| 5개 서비스 `build.gradle` | `OTEL_LOGS_EXPORTER`: `none` → `otlp` |
| 5개 서비스 `build.gradle` | Logback appender 브리지 플래그 5종 추가 |
| `docker/loki-config.yml` | `allow_structured_metadata: true`, `volume_enabled: true` 추가 |
| `docker/otel-collector-config.yml` | logs 파이프라인에 `transform/logs` 추가 (severity_text 소문자 정규화) |
| `docker/compose.yaml` | Promtail 서비스 주석 처리 (이전에 이미 완료) |

## 데이터 플로우

```
shop-order (Spring Boot)
  └── logback.xml 의 appender 실행
       └── OTel Java Agent 의 logback-mdc 브리지가 LogRecord 생성
            ├── severity_text: "INFO"
            ├── body: "order created: 12345"
            ├── trace_id: 4bf92f3577b34da6a3ce929d0e0e4736
            ├── span_id:  00f067aa0ba902b7
            ├── resource.service.name: shop-order
            ├── resource.service.namespace: shop
            ├── resource.deployment.environment: local
            └── attributes.code.function: createOrder, code.lineno: 42, thread.name: ...
  │
  │ OTLP gRPC (localhost:24317)
  ▼
OTel Collector
  ├── receivers.otlp (4317)
  ├── processors: batch → resource → transform/logs (severity 소문자화)
  └── exporters.otlphttp/loki (http://loki:3100/otlp)
  │
  ▼
Loki (OTLP native endpoint /otlp)
  ├── Loki 라벨 (인덱싱됨, 저카디널리티)
  │    ├── service_name="shop-order"
  │    ├── service_namespace="shop"
  │    ├── deployment_environment="local"
  │    └── level="info"
  ├── Structured Metadata (비인덱싱, 조회 가능)
  │    ├── trace_id, span_id
  │    ├── code.function, code.lineno, code.filepath
  │    └── thread.name, logger.name
  └── Log body (원문)
```

## 적용 절차

### 1) 인프라 재기동

```bash
cd docker
docker compose down otel-collector loki grafana
docker compose up -d otel-collector loki grafana
```

**왜 grafana 도 재기동?** Grafana 자체는 설정 변경이 없지만, Loki 가 올라온 직후 datasource health check 를 다시 타게 하기 위함. 생략해도 Grafana 가 자동 복구.

### 2) Spring Boot 앱 전부 재기동

```bash
./stop-services.sh   # 기존 앱 종료
./start-services.sh  # 새 환경변수로 재기동
```

핵심은 **JVM 이 재시작되어야 새 환경변수가 Agent 에 적용**된다는 점. 런타임 reload 불가.

### 3) 검증 — OTel Collector 가 로그를 받고 있는지

```bash
# Collector 자체 메트릭 포트
curl -s http://localhost:28888/metrics | grep otelcol_receiver_accepted_log_records
```

정상이면 `otelcol_receiver_accepted_log_records_total{receiver="otlp",transport="grpc"} 123` 같은 값이 **0 이상으로 증가**.

0 이면:
- 앱이 재기동됐는지 확인
- `./gradlew bootRun` 로그에 `[otel.javaagent] ...OtlpLogRecordExporter` 로그가 보이는지 확인
- `build.gradle` 의 `OTEL_LOGS_EXPORTER=otlp` 가 실제 반영됐는지 확인 (`./gradlew bootRun --info | grep OTEL_LOGS`)

### 4) 검증 — Loki 가 로그를 받아 저장하는지

```bash
# Loki ingestion 통계
curl -s http://localhost:13100/metrics | grep loki_distributor_bytes_received_total

# 라벨 목록 조회 — service_name 이 나와야 함
curl -s 'http://localhost:13100/loki/api/v1/label/service_name/values'
# 기대: {"status":"success","data":["shop-order","shop-product","shop-stock","shop-payment","shop-user"]}
```

### 5) 검증 — Grafana Explore

1. `http://localhost:13000` → Explore → Loki datasource
2. LogQL 쿼리: `{service_name="shop-order"} |= "Saga"`
3. 로그 한 건 클릭 → 상세 패널에서 확인할 것:
   - **Fields** 탭: `trace_id`, `span_id`, `code_function`, `code_lineno` 보여야 함
   - 로그 우측에 **🧭 View trace** 버튼 활성화 → 클릭하면 Tempo 로 점프

### 6) 최종 — Promtail 완전 삭제 (선택)

현재는 compose.yaml 에 주석 처리만 돼 있음. 확신이 서면 완전 삭제:

```bash
# docker/compose.yaml 에서 promtail 블록(라인 86~97)과 promtail-config.yml 삭제
rm docker/promtail-config.yml
```

그리고 각 서비스 logs/ 디렉토리는 그대로 유지해도 됨 (logback file appender 는 로컬 디버깅용으로 계속 유용).

## 주요 쿼리 예시

### LogQL — 서비스 단위 검색
```logql
{service_name="shop-order"}
{service_name="shop-order", level="error"}
{service_namespace="shop"} |= "PAYMENT_FAILED"
```

### LogQL — trace 기반 검색 (structured metadata)
```logql
{service_namespace="shop"} | trace_id="4bf92f3577b34da6a3ce929d0e0e4736"
```

### LogQL — Saga 추적
```logql
{service_namespace="shop"} | json | saga_id="01HV9..."
```

### Grafana Derived Fields
현재 `grafana/provisioning/datasources/loki.yml` 에 설정된 정규식 derived field 는 **로그 body 에서 trace_id 를 텍스트로 추출**하는 방식이라 구 Promtail 경로용. OTLP 경로에서는 **structured metadata 에 trace_id 가 네이티브로 붙기 때문에 Grafana 가 자동 인식**. 두 가지가 공존해도 문제 없음 (fallback 으로 둘 중 먼저 매치되는 쪽 사용).

## Troubleshooting

### ❌ Collector 는 받는데 Loki 에 안 보임
→ Loki 로그 확인: `docker logs loki | grep -i "structured metadata"`. 만약 `structured metadata is disabled` 에러가 보이면 `allow_structured_metadata: true` 가 안 들어간 것.

### ❌ 라벨이 `service_name` 이 아닌 `exporter` 이름으로 잡힘
→ Loki 3.0 미만 버전 사용 중일 가능성. `grafana/loki:3.0.0` 이상인지 compose 확인.

### ❌ trace_id 가 structured metadata 에 없음
→ `OTEL_INSTRUMENTATION_LOGBACK_APPENDER_ENABLED=true` 가 실제 JVM 에 적용됐는지 확인. `jps -lv | grep shop` 으로 프로세스에 flag 가 보여야 함.

### ❌ 로그가 너무 늦게 보임
→ Collector `batch` processor timeout 2s + Loki ingest latency. 개발 환경에서 더 빠르게 보려면 `batch.timeout: 500ms` 로 낮출 수 있음. 프로덕션에선 기본값 유지.

### ❌ `otelcol_exporter_send_failed_log_records_total` 증가
→ Loki 가 거부 중. 사유 확인: `docker logs otel-collector | grep -i loki`.
   - `rate limit exceeded` → `loki-config.yml` 의 `ingestion_rate_mb` 상향
   - `stream limit` → `max_streams_per_user` 상향
   - `400 Bad Request` → OTLP endpoint 경로 확인 (`/otlp` 여야 함, `/loki/api/v1/push` 아님)

## 롤백 절차

문제 생기면 30초 이내 원복 가능:

```bash
# 1. build.gradle 5개 에서 OTEL_LOGS_EXPORTER 를 다시 none 으로
# 2. compose.yaml 의 promtail 블록 주석 해제
# 3. 재기동
docker compose up -d promtail
./stop-services.sh && ./start-services.sh
```

Promtail 이 파일을 tail 하기 시작하면 로그 수집 재개.
