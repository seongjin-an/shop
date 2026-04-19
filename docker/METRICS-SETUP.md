# Metrics Setup Guide (Phase 2: Prometheus + Grafana Dashboard)

## 완료된 구성

```
Spring Boot 앱 (5개)
  ├─ /actuator/prometheus 노출 (Micrometer)
  └─ 포트: 8080(user), 8081(product), 8082(order), 8083(stock mgmt), 8085(payment mgmt)

Prometheus (localhost:19090)
  ├─ scrape: 5개 서비스 + kafka-exporter + tempo + otel-collector
  └─ remote_write 수신 활성 (Tempo metrics_generator 가 push)

Kafka Exporter (localhost:19308)
  └─ consumer lag, topic offset, broker info

Tempo metrics_generator
  ├─ service_graphs: 서비스 간 호출 그래프 메트릭
  └─ span_metrics: trace 기반 RED (Rate/Error/Duration)

Grafana (localhost:13000)
  └─ 프로비저닝된 대시보드: "Shop — Saga Overview"
```

## 기동 순서

```bash
cd docker
docker compose up -d

# 기동 대기 (prometheus, kafka-exporter 포함)
docker compose ps

# Spring Boot 서비스는 호스트에서 실행
cd ..
./start-services.sh            # 또는 각 서비스에서 ./gradlew bootRun
```

## 검증 체크리스트

### 1. Actuator 엔드포인트 확인

각 서비스가 `/actuator/prometheus` 노출하는지:

```bash
curl -s http://localhost:8080/actuator/prometheus | head -5   # shop-user
curl -s http://localhost:8081/actuator/prometheus | head -5   # shop-product
curl -s http://localhost:8082/actuator/prometheus | head -5   # shop-order
curl -s http://localhost:8083/actuator/prometheus | head -5   # shop-stock (mgmt)
curl -s http://localhost:8085/actuator/prometheus | head -5   # shop-payment (mgmt)
```

각 응답의 첫 줄이 `# HELP ...` 로 시작하면 OK.

### 2. Prometheus scrape 타겟 확인

http://localhost:19090/targets → 모든 job 이 **UP** 이어야 함:

| Job | Targets | 상태 |
|---|---|---|
| prometheus | 1 | UP |
| shop-services | 5 | 모두 UP |
| kafka-exporter | 1 | UP |
| otel-collector | 1 | UP |
| tempo | 1 | UP |

### 3. 비즈니스 메트릭 시드

스트레스 테스트로 saga 를 몇 번 흘려보낸 후:

```promql
# Prometheus UI (http://localhost:19090/graph) 에서 실행
saga_started_total
saga_terminated_total
saga_duration_seconds_count
```

값이 찍히면 SagaMetrics 정상 작동.

### 4. Grafana 대시보드 확인

http://localhost:13000 → Dashboards → Shop 폴더 → **Shop — Saga Overview**

- 상단: 성공률, STOCK_FAILED 비율, CANCELLED 비율, 평균 완료 시간 (stat 패널 4개)
- 중단: Saga 종료 상태 분포, Saga Duration p50/p95/p99
- 서비스별 HTTP RED
- Kafka Consumer Lag
- JVM Heap / HikariCP 활성 커넥션

### 5. Tempo Service Graph

Grafana → Explore → Tempo → Service Graph 탭 → shop 서비스 간 호출 그래프 그려지는지.
(처음 10분 정도 trace 쌓이면 나타남)

## 주요 메트릭 참조

### 비즈니스

| 메트릭 | 설명 |
|---|---|
| `saga_started_total` | 주문 생성 누적 |
| `saga_terminated_total{status}` | 터미널 상태별 누적 (COMPLETED/STOCK_FAILED/CANCELLED) |
| `saga_duration_seconds` | 생성 → 터미널 wall-clock (histogram) |
| `saga_state_transition_total{from, to}` | 전체 상태 전이 감사 |

### HTTP (Micrometer 자동)

| 메트릭 | 설명 |
|---|---|
| `http_server_requests_seconds_count` | 요청 수 |
| `http_server_requests_seconds_bucket` | latency histogram (p50/p95/p99 계산용) |
| `http_server_requests_seconds_sum` | 누적 처리 시간 |

라벨: `application`, `uri`, `method`, `status`, `outcome`

### Kafka Consumer Lag (kafka-exporter)

| 메트릭 | 설명 |
|---|---|
| `kafka_consumergroup_lag` | group × topic × partition 단위 lag |
| `kafka_consumergroup_current_offset` | consumer 현재 offset |
| `kafka_topic_partition_current_offset` | partition 의 최신 offset (LEO) |
| `kafka_topic_partition_oldest_offset` | 가장 오래된 offset |

### JVM / DB (Micrometer 자동)

| 메트릭 | 설명 |
|---|---|
| `jvm_memory_used_bytes{area, id}` | heap / non-heap 별 사용량 |
| `jvm_gc_pause_seconds` | GC pause 시간 |
| `hikaricp_connections_active` | Hikari 활성 커넥션 |
| `hikaricp_connections_pending` | 커넥션 대기 스레드 |

## 자주 쓰는 PromQL

```promql
# 서비스별 p99 latency
histogram_quantile(0.99,
  sum by (application, le) (
    rate(http_server_requests_seconds_bucket[5m])
  )
)

# Saga 성공률
sum(rate(saga_terminated_total{status="COMPLETED"}[5m]))
  / sum(rate(saga_terminated_total[5m]))

# Kafka consumer lag 합계 (토픽별)
sum by (topic) (kafka_consumergroup_lag)

# 서비스별 에러율 (5xx)
sum by (application) (rate(http_server_requests_seconds_count{status=~"5.."}[5m]))
  / sum by (application) (rate(http_server_requests_seconds_count[5m]))

# 현재 실행 중인 saga 추정 (시작 - 종료)
sum(saga_started_total) - sum(saga_terminated_total)
```

## 확장 아이디어

- Alertmanager 추가해서 "consumer lag > 1000 10분 지속" 같은 알림
- Mimir 로 이관 (Prometheus 는 HA/장기 저장 약함)
- OpenTelemetry Collector 에 `prometheus` receiver 넣어 Actuator 도 OTel 경유로 통합
- 서비스별 주요 메서드에 `@Timed` / `@Counted` 어노테이션으로 커스텀 지표 추가
