# Metrics Setup Guide (v2.1: Collector Gateway 패턴)

## 진화 연혁

| 버전 | 메트릭 흐름 | 상태 |
|---|---|---|
| v2.0 | Prometheus 가 Actuator / kafka-exporter 를 **직접 scrape** | 🗄️ 과거 |
| **v2.1** | **OTel Collector 가 Gateway 로 scrape → remote_write → Prometheus** | ✅ 현재 |
| v3.0 (예정, EKS) | Collector (DaemonSet) → Mimir/AMP | 📝 |

## 왜 Collector 를 경유하는가 (k8s 실무 관점)

```
[v2.0 Before]                           [v2.1 After]
Spring Boot /actuator/prometheus        Spring Boot /actuator/prometheus
            ▲                                       ▲
            │ scrape                                │ scrape (Collector 가 흡수)
            │                                       │
      Prometheus                            OTel Collector (Gateway)
                                                    │ remote_write
                                                    ▼
                                              Prometheus (receiver-only)
```

EKS 이주 시:
- v2.0 방식: Prometheus Operator + ServiceMonitor/PodMonitor 설정 전부 필요 → 앱 배포할 때마다 label selector 관리
- v2.1 방식: Collector 를 DaemonSet 으로 띄우면 끝. 앱은 localhost Collector 로만 pushscrape. TSDB 를 Mimir/AMP 로 바꾸면 exporter 한 줄 교체

## 완료된 구성

```
Spring Boot 앱 (5개)
  ├─ /actuator/prometheus 노출 (Micrometer)
  └─ 포트: 8080(user), 8081(product), 8082(order), 8083(stock mgmt), 8085(payment mgmt)

OTel Collector (localhost:24317 / :24318 / :28888)
  ├─ receivers:
  │   ├─ otlp                — traces + logs (+ 미래 OTLP metrics push)
  │   └─ prometheus (scrape) — shop-services(5), kafka-exporter, tempo
  ├─ processors: batch, resource, transform/saga_id, attributes/baggage, transform/logs
  └─ exporters:
      ├─ otlp/tempo               — 트레이스
      ├─ otlphttp/loki            — 로그 (Loki 3.0+ 네이티브 OTLP)
      └─ prometheusremotewrite    — 메트릭

Prometheus (localhost:19090)
  ├─ receiver-only (--web.enable-remote-write-receiver)
  ├─ 유일한 scrape 대상: otel-collector:8888 (self-observability)
  └─ remote_write 수신:
      ├─ Collector (앱/kafka-exporter/tempo 메트릭 relay)
      └─ Tempo metrics_generator (service_graphs, span_metrics)

Kafka Exporter (kafka-exporter:9308)  ← Collector 가 scrape
Tempo metrics_generator               ← service graph, RED 메트릭 push (remote_write)

Grafana (localhost:13000)
  └─ 프로비저닝된 대시보드: "Shop — Saga Overview"
```

## 기동 순서

```bash
cd docker
docker compose up -d

# 기동 대기
docker compose ps

# Spring Boot 서비스는 호스트에서 실행
cd ..
./start-services.sh            # 또는 각 서비스에서 ./gradlew bootRun
```

## 검증 체크리스트

### 1. Actuator 엔드포인트 (앱 스스로 노출)

```bash
curl -s http://localhost:8080/actuator/prometheus | head -5   # shop-user
curl -s http://localhost:8081/actuator/prometheus | head -5   # shop-product
curl -s http://localhost:8082/actuator/prometheus | head -5   # shop-order
curl -s http://localhost:8083/actuator/prometheus | head -5   # shop-stock  (mgmt)
curl -s http://localhost:8085/actuator/prometheus | head -5   # shop-payment(mgmt)
```

응답 첫 줄이 `# HELP ...` 이면 OK.

### 2. OTel Collector 가 scrape 하고 있는지

Collector 자기 메트릭에 scrape 지표가 나옵니다:

```bash
curl -s http://localhost:28888/metrics | grep otelcol_receiver_accepted_metric_points
```

Job 별로 숫자가 올라가면 정상.

### 3. Prometheus 에 remote_write 가 들어오는지

http://localhost:19090/targets → **`otel-collector` 한 개만** UP 이면 됨.
(v2.0 에서는 `shop-services`, `kafka-exporter` 등이 여기 나왔지만 v2.1 부터는 Collector 가 대신 scrape 함)

```promql
# Prometheus UI 에서 쿼리: v2.0 과 동일한 메트릭 이름이 그대로 조회 가능
http_server_requests_seconds_count
hikaricp_connections_active
kafka_consumergroup_lag
```

값이 나오면 Collector → remote_write 성공.

### 4. 비즈니스 메트릭 시드

스트레스 테스트로 saga 를 몇 번 흘려보낸 후:

```promql
saga_started_total
saga_terminated_total
saga_duration_seconds_count
```

### 5. Grafana 대시보드

http://localhost:13000 → Dashboards → Shop 폴더 → **Shop — Saga Overview**

대시보드 PromQL 은 v2.0 시절 그대로 유지됨 — Micrometer 메트릭 이름이 변하지 않았기 때문.

### 6. Tempo Service Graph

Grafana → Explore → Tempo → Service Graph 탭. (트레이스 10분 정도 쌓이면 노출)

## 주요 메트릭 참조

### 비즈니스

| 메트릭 | 설명 |
|---|---|
| `saga_started_total` | 주문 생성 누적 |
| `saga_terminated_total{status}` | 터미널 상태별 (COMPLETED/STOCK_FAILED/CANCELLED) |
| `saga_duration_seconds` | 생성 → 터미널 wall-clock (histogram) |
| `saga_state_transition_total{from, to}` | 전체 상태 전이 감사 |

### HTTP (Micrometer 자동)

| 메트릭 | 설명 |
|---|---|
| `http_server_requests_seconds_count` | 요청 수 |
| `http_server_requests_seconds_bucket` | latency histogram (p50/p95/p99 계산용) |

라벨: `application`, `uri`, `method`, `status`, `outcome`

> **참고**: 앱에서 `OTEL_METRICS_EXPORTER=otlp` 로 전환하면 메트릭 이름이 OTel semantic convention 으로 바뀝니다 (`http.server.request.duration` 등). 그때는 대시보드 PromQL 도 같이 수정 필요. 지금은 Micrometer 이름 유지가 훨씬 편하기 때문에 pull 방식 유지.

### Kafka Consumer Lag (kafka-exporter, Collector scrape)

| 메트릭 | 설명 |
|---|---|
| `kafka_consumergroup_lag` | group × topic × partition 단위 lag |
| `kafka_consumergroup_current_offset` | consumer 현재 offset |
| `kafka_topic_partition_current_offset` | partition LEO |

### JVM / DB (Micrometer 자동)

| 메트릭 | 설명 |
|---|---|
| `jvm_memory_used_bytes{area, id}` | heap / non-heap 사용량 |
| `jvm_gc_pause_seconds` | GC pause |
| `hikaricp_connections_active` | Hikari 활성 커넥션 |
| `hikaricp_connections_pending` | 커넥션 대기 스레드 |

## 자주 쓰는 PromQL

```promql
# 서비스별 p99 latency
histogram_quantile(0.99,
  sum by (service, le) (
    rate(http_server_requests_seconds_bucket[5m])
  )
)

# Saga 성공률
sum(rate(saga_terminated_total{status="COMPLETED"}[5m]))
  / sum(rate(saga_terminated_total[5m]))

# Kafka consumer lag 합계 (토픽별)
sum by (topic) (kafka_consumergroup_lag)

# 서비스별 에러율 (5xx)
sum by (service) (rate(http_server_requests_seconds_count{status=~"5.."}[5m]))
  / sum by (service) (rate(http_server_requests_seconds_count[5m]))

# 현재 실행 중인 saga 추정
sum(saga_started_total) - sum(saga_terminated_total)

# Collector 자체 처리율 (Collector 건강)
rate(otelcol_exporter_sent_metric_points[1m])
```

## 전환 가이드 (앱에서 직접 OTLP push 로 가고 싶을 때)

현재 Collector 의 `metrics` 파이프라인은 이미 `[otlp, prometheus]` 두 receiver 를 모두 등록해 둬서, 앱 측 한 줄만 바꾸면 됩니다.

```groovy
// shop-*/build.gradle
environment 'OTEL_METRICS_EXPORTER', 'otlp'              // none → otlp
environment 'OTEL_METRIC_EXPORT_INTERVAL', '15000'       // 15초마다 push
```

주의:
- Agent 가 내는 JVM / Kafka 메트릭이 Micrometer 것과 **이름은 다른데 의미는 겹침** → 둘 다 켜면 대시보드에서 노이즈
- 전환 시 현재 Collector `prometheus` receiver 의 `shop-services` job 을 주석 처리하는 게 깨끗함

## 확장 아이디어 (→ 로드맵)

- **Alertmanager**: "consumer lag > 1000 10분 지속", "saga 성공률 < 99%" 같은 SLO 기반 알림
- **Mimir 로 이관**: Prometheus 는 HA/장기 저장 약함. Collector `prometheusremotewrite` endpoint 만 바꿔주면 끝.
- **EKS 배포**: Collector 를 Deployment(Gateway) + DaemonSet(agent) 2-티어로. Amazon Managed Prometheus (AMP) 연동.
- **트레이스 exemplar**: span 하나가 메트릭 히스토그램의 어느 bucket 에 기여했는지 Grafana 에서 점프. 이미 Prometheus `--enable-feature=exemplar-storage` 활성.
- **커스텀 비즈니스 지표**: `@Timed` / `@Counted` 어노테이션으로 특정 UseCase 메서드 측정.
