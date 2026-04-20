네 맞습니다. 정확히 그 구조예요. 두 가지 역할을 동시에 수행합니다:

## 1. 수집 단계 — Collector 가 scrape (pull)

Collector 가 주기적으로 HTTP GET 요청을 보내서 메트릭을 긁어옵니다.

```
OTel Collector  ──GET /actuator/prometheus──►  shop-order:8082
                ◄──── 텍스트 응답 ─────────────

응답 예:
# HELP http_server_requests_seconds_count
# TYPE http_server_requests_seconds_count counter
http_server_requests_seconds_count{uri="/orders",method="POST",status="200"} 1523
hikaricp_connections_active{pool="HikariPool-1"} 3
saga_started_total 1523
...
```

설정 근거 (`docker/otel-collector-config.yml`):

```yaml
receivers:
  prometheus:                         ← "Collector 안의 미니 Prometheus"
    config:
      scrape_configs:
        - job_name: 'shop-services'
          scrape_interval: 10s
          metrics_path: /actuator/prometheus
          static_configs:
            - targets: ['host.docker.internal:8082']   # shop-order
              labels: { service: shop-order }
            - targets: ['host.docker.internal:8083']   # shop-stock
            - ...
        - job_name: 'kafka-exporter'
          static_configs:
            - targets: ['kafka-exporter:9308']
```

이 `prometheus` receiver 는 이름처럼 **Prometheus 와 동일한 scrape 엔진**을 Collector 내부에 박아둔 것입니다. 사실상 Collector 가 mini Prometheus 를 하나 들고 있는 셈.

## 2. 저장 단계 — Collector 가 Prometheus 로 write (push, remote_write)

긁어온 메트릭을 Prometheus 의 remote_write API 로 밀어넣습니다.

```
OTel Collector  ──POST /api/v1/write (snappy-compressed protobuf)──►  Prometheus:9090
```

설정 근거:

```yaml
exporters:
  prometheusremotewrite:
    endpoint: http://prometheus:9090/api/v1/write
    resource_to_telemetry_conversion:
      enabled: true
```

Prometheus 쪽에서 받을 준비 (`docker/compose.yaml`):

```yaml
prometheus:
  command:
    - --web.enable-remote-write-receiver   # 이게 있어야 POST /api/v1/write 가 열림
```

## 전체 한 사이클 예시

`shop-order` 가 주문 하나 처리했을 때 메트릭이 흘러가는 과정:

```
[T+0s]    shop-order 내부: saga_started_total++
                          Micrometer 가 /actuator/prometheus 텍스트 응답에 반영

[T+3s]    Collector: "10초 타이머 만료"
             └─ GET http://host.docker.internal:8082/actuator/prometheus
             └─ "saga_started_total 1524" 받음
             └─ 내부 buffer 에 저장 (batch processor)

[T+5s]    Collector: "batch 2초 또는 1024건 만료"
             └─ POST http://prometheus:9090/api/v1/write
                  body: protobuf(샘플들)

[T+5s]    Prometheus: body 파싱 → TSDB 에 저장

[T+5s]    Grafana: PromQL `saga_started_total` 쿼리 → 1524 반환
```

## 말씀하신 문장을 다시 정확히

> "OTel Collector 는 직접 scrape 을 하고 Prometheus 로 쓰기를 한다"

네 정확합니다. 다만 단어를 명시적으로 매칭해서 정리하면:

| 단계 | 방향 | Collector 역할 | 상대 역할 |
|---|---|---|---|
| 수집 | Collector → App `/actuator/prometheus` | **HTTP GET (scrape, pull)** | 텍스트 응답 (passive) |
| 저장 | Collector → Prometheus `/api/v1/write` | **HTTP POST (remote_write, push)** | receiver (passive) |

**핵심 포인트**: Collector 가 두 방향 모두에서 **능동적 주체** 입니다. 한쪽은 pull 로 긁고, 한쪽은 push 로 보냅니다. App 과 Prometheus 는 각각 수동적으로 응답/수신만 해요. 이게 Gateway 패턴의 핵심 — 중앙 한 곳(Collector)에서 모든 라우팅과 흐름 제어를 독점하는 구조입니다.