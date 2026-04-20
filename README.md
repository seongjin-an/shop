# Shop — Event-Driven Microservices

Choreography-based Saga 패턴과 **관측성(Observability) 스택**을 실습하는 이커머스 플랫폼입니다.

> **v1** — 동작하는 MSA: Choreography Saga, Inbox/Outbox, Kafka
> **v2** — 견고한 MSA: OpenTelemetry + LGTM + Prometheus, 병목 분석, DB 인덱스 튜닝

---

## 목차

1. [서비스 구성](#서비스-구성)
2. [기술 스택](#기술-스택)
3. [빠른 시작](#빠른-시작)
4. [Saga 흐름](#saga-흐름)
5. [Kafka 토픽](#kafka-토픽)
6. [주요 패턴](#주요-패턴)
7. [관측성 스택 (v2)](#관측성-스택-v2)
8. [성능 튜닝 기록 (v2)](#성능-튜닝-기록-v2)
9. [부하 테스트 (Locust)](#부하-테스트-locust)
10. [API 예시](#api-예시)
11. [로드맵](#로드맵)

---

## 서비스 구성

| 서비스 | 포트 | 관측/관리 포트 | 역할 |
|--------|------|--------------|------|
| shop-user | 8080 | 8080 | 회원가입 / 로그인 (Spring Security, Redis 세션) |
| shop-product | 8081 | 8081 | 상품 등록 / 조회, `product-created` 발행 |
| shop-order | 8082 | 8082 | 주문 생성 / Saga 조율 |
| shop-stock | — | 8083 | 재고 관리 (Inbox 패턴, 낙관적 락) |
| shop-payment | — | 8085 | 결제 처리 (Fake PG, Headless Kafka consumer) |
| shop-frontend | 3000 | — | Next.js UI |

> `shop-stock`, `shop-payment` 는 Kafka consumer 전용이지만 `/actuator/prometheus` 노출을 위해 management 포트만 따로 올라가 있습니다.

---

## 기술 스택

- **Backend** Java 21, Spring Boot 3.5, Spring Data JPA, Spring Kafka, Spring Security
- **Frontend** Next.js 15, React 19, Tailwind CSS v4, Zustand
- **Infra** MySQL 8, Redis, Apache Kafka (KRaft)
- **Observability** OpenTelemetry Java Agent, Loki, Tempo, Prometheus, Grafana, Promtail, Kafka Exporter
- **Load test** Locust

---

## 빠른 시작

```bash
# 1. 인프라 + 관측성 스택 실행
cd docker && docker compose up -d

# 2. Kafka 토픽 생성 (최초 1회)
./create-topics.sh

# 3. OTel Java Agent 다운로드 (최초 1회)
./download-otel-agent.sh

# 4. 백엔드 서비스 전체 기동
cd .. && ./start-services.sh

# 5. 프론트엔드
cd shop-frontend && npm run dev
```

### 접속 포인트

| 대상 | 주소 |
|------|------|
| 프론트엔드 | `http://localhost:3000` |
| Grafana | `http://localhost:13000` (admin/admin) |
| Prometheus | `http://localhost:19090` |
| Kafka UI | `http://localhost:18090` |
| RedisInsight | `http://localhost:15540` |
| MySQL | `localhost:23306` (db: `shop`, user: `dev_user`, pass: `dev_password`) |
| Redis | `localhost:16379` |
| Kafka | `localhost:9094` |

---

## Saga 흐름

```
Client
  │
  ▼ POST /orders
shop-order ──order-created──────────────────► shop-stock
                                                  │
                                     stock-reserved / stock-reserve-failed
                                                  │
shop-order ◄──────────────────────────────────────┘
  │
  ├─ STOCK_FAILED (재고 부족, 종료)
  │
  └─ payment-requested ──► shop-payment
                                │
                     payment-success / payment-failed
                                │
         ┌──────────────────────┴──────────────────────┐
         ▼                                             ▼
     shop-order                                   shop-stock
  COMPLETED (종료)                          confirmReservation()
         │
         └─ (실패 시) order-canceled ──► shop-stock
                                           cancelReservation()
                                           (재고 복구)
```

### OrderEntity 상태 전이

```
PENDING
  ├─ stock-reserved       → STOCK_RESERVED
  │    ├─ payment-success → COMPLETED        ← terminal
  │    └─ payment-failed  → PAYMENT_FAILED
  │         └─ (즉시)     → CANCELLED        ← terminal
  └─ stock-reserve-failed → STOCK_FAILED     ← terminal
```

---

## Kafka 토픽

| 토픽 | 발행 서비스 | 소비 서비스 |
|------|------------|------------|
| `product-created` | shop-product | shop-stock |
| `order-created` | shop-order | shop-stock |
| `stock-reserved` | shop-stock | shop-order |
| `stock-reserve-failed` | shop-stock | shop-order |
| `payment-requested` | shop-order | shop-payment |
| `payment-success` | shop-payment | shop-order, shop-stock |
| `payment-failed` | shop-payment | shop-order |
| `order-canceled` | shop-order | shop-stock |

모든 이벤트는 `kafkaTemplate.send(topic, sagaId, json)` 형태로 **Kafka message key 를 sagaId** 로 씁니다.
이 결정이 v2 관측성 단계에서 **애플리케이션 코드 0줄 수정**으로 saga 추적을 가능하게 만들었습니다 (아래 관측성 섹션 참고).

---

## 주요 패턴

### Inbox 패턴

`shop-stock`, `shop-payment` 에서 중복 이벤트 소비를 방지합니다.
`InboxEventEntity.eventId` 에 unique 제약을 걸어 멱등성을 보장합니다.

shop-stock 의 inbox 는 **보상 트랜잭션** 시에도 활용됩니다.
`order-canceled` 수신 시 `sagaId` 로 원본 `order-created` 페이로드를 조회해 아이템 목록을 복원합니다.

### 낙관적 락 + Retry

`StockEntity` 에 `@Version` 을 적용하고 `StockService.reserve()` 에 `@Retryable` 을 설정하여 동시 주문 충돌을 자동 재시도합니다.

예시: 같은 상품에 동시 주문 2건이 들어올 때

```
Tx1: version=5 → 6  (성공)
Tx2: version=5 → ?  (실패, OptimisticLockException)
     └─ @Retryable 이 재시도 → version=6 읽어와서 version=7 로 성공
```

### 장애 격리 레이어

```
[1차] @Retryable × 5            — JVM 내 즉시 재시도 (낙관적 락 충돌)
[2차] DefaultErrorHandler × 3   — Kafka 레벨 재시도 (2초 간격)
[3차] DLT 컨슈머                — 부하 감소 후 재처리 (몇 분 지연)
[4차] 알람 + 수동 개입           — 진짜 비정상 케이스 (DB 이상, 버그 등)
```

### Fake PG (shop-payment)

실제 PG사 연동 없이 결제 흐름을 시뮬레이션합니다.
- 90% 확률 성공, 10% 확률 실패
- `pgTransactionId`, `pgAuthCode`, `maskedCardNumber` 등 실제 PG 응답 필드를 생성하여 저장
- HTTP 서버 없이 Kafka consumer 만 동작 (`web-application-type: none`)

### Outbox 패턴

구조는 잡혀 있으나 미활성. 현재는 `KafkaTemplate.send()` 직접 호출. (로드맵에서 Debezium 으로 전환 예정)

### 패키지 구조 (공통)

```
com.ansj.<service>/
  common/      # BaseEvent, EventId, SagaId, AggregateId, JsonUtil
  config/      # KafkaConfig, DataSourceConfig, ObjectMapperConfig
  box/         # InboxEventEntity/Service (shop-stock, shop-payment)
  <domain>/
    entity/
    service/   # persistence 레이어
    repository/
    event/
      inbound/   # 수신 이벤트 DTO (@JsonCreator)
      outbound/  # 발행 이벤트 DTO (@Builder)
  kafka/       # @KafkaListener (manual ack)
  usecase/     # 비즈니스 흐름 + Kafka 발행
```

---

## 관측성 스택 (v2)

> **원칙: 애플리케이션 코드 0줄 수정.** 모든 계측은 Java Agent, Logback 패턴, Collector 설정만으로 달성했습니다.

### 전체 구성도 (v2.1 — Collector Gateway)

**모든 관측 신호가 OTel Collector 한 곳을 경유**합니다. k8s(EKS) 이주 시 그대로 유효한 Gateway 패턴.

```
                                    ┌───────────────────────────────┐
Spring Boot (x5) ── OTel Agent ─────┤ OTel Collector (Gateway)      │
  • /actuator/prometheus ◄──scrape──│   receivers: otlp, prometheus │
                                    │   processors:                 │
                                    │     • saga.id derivation      │
                                    │     • resource enrichment     │
                                    │   exporters:                  │──► Tempo   (traces)
                                    │     • otlp/tempo              │──► Loki    (logs)
                                    │     • otlphttp/loki           │──► Prom    (metrics, remote_write)
Kafka Exporter ────────◄──scrape────│     • prometheusremotewrite   │
Tempo metrics_generator ─remote_write→  Prometheus (receiver only)  │
                                    └───────────────────────────────┘
                                                                   │
                                                                   ▼
                              Grafana ── Tempo + Loki + Prometheus (Traces↔Logs↔Metrics pivot)
```

**왜 Collector Gateway 인가 (k8s 실무 베스트 프랙티스)**

| 기존 직접 scrape 방식 | Collector Gateway |
|---|---|
| Prometheus 가 pod 주소를 다 알아야 함 (ServiceMonitor, 라벨 셀렉터 관리) | 앱은 Collector 하나만 알면 됨 (Service DNS) |
| TSDB 교체(→Mimir/Thanos/AMP) 시 앱/Prometheus 양쪽 수정 | Collector exporter 한 줄만 수정 |
| traces / metrics / logs 각각 다른 파이프 → enrichment 중복 구현 | Collector 한 곳에서 공통 processor 체인 (resource, saga.id) |
| 방화벽/NAT 뒤의 앱 scrape 불가 | 앱이 push → 방화벽 이슈 없음 (DaemonSet 패턴) |

**현재 구현은 "Hybrid Pull via Collector"**

앱은 여전히 `/actuator/prometheus` 로 Micrometer 메트릭을 노출하고, Collector 의 `prometheus` receiver 가 이걸 scrape 해서 backend 로 push 합니다. 이 선택의 이유:

- Micrometer 의 풍부한 Spring 네이티브 메트릭 이름(`http_server_requests_seconds_*`, `hikaricp_connections_active`, `jvm_gc_pause_seconds`)이 보존 → 기존 Grafana 대시보드/PromQL/alert 규칙 무수정
- 앱이 직접 OTLP push 로 가고 싶으면 `OTEL_METRICS_EXPORTER=otlp` 한 줄만 바꾸면 됨 (Collector 의 `metrics` 파이프라인이 이미 `[otlp, prometheus]` 두 receiver 모두 등록)

### 구성 요소

| 컴포넌트 | 포트 | 역할 |
|---|---|---|
| Grafana | `:13000` | 시각화, 데이터소스 통합 |
| Tempo | `:13200` | 분산 트레이스 저장소 |
| Loki | `:13100` | 로그 저장소 (OTLP 네이티브 수신) |
| Prometheus | `:19090` | 메트릭 저장소 (receiver-only, `--web.enable-remote-write-receiver`) |
| **OTel Collector** | gRPC `:24317`, HTTP `:24318`, self `:28888` | **Gateway**: OTLP 수신 + Actuator/Exporter scrape + saga.id derivation + 3 backend 분기 |
| Kafka Exporter | `:19308` | Consumer lag, topic offset (Collector 가 scrape) |
| OTel Java Agent | JVM `-javaagent:` | Spring MVC / JDBC / Kafka 자동 계측 (traces + logs) |

### 계측이 자동으로 잡아주는 것

- Spring MVC 엔드포인트 (`POST /orders` 등)
- JDBC — Hibernate 쿼리 **한 건 한 건 별도 span** 으로
- Kafka Producer / Consumer — 메시지 key, topic, partition, offset
- HTTP Client

### 로그 ↔ 트레이스 상관관계

`logback-spring.xml` 패턴에 한 줄 추가:

```
%d{...} %-5level [trace_id=%X{trace_id:-} span_id=%X{span_id:-}] ...
```

Grafana 에서:
- **트레이스 → 로그**: Tempo span 클릭 → "Logs for this span" 버튼 → Loki 에서 `{service_name="..."} |= "<trace_id>"` 자동 실행
- **로그 → 트레이스**: Loki 로그 확장 → `trace_id=abc123` 에 Grafana 가 자동으로 "TraceID" 버튼 생성 → Tempo 로 점프

### `saga.id` 전파 — Collector-side Derivation

v2 의 가장 영리한 포인트. 원래 Baggage API 를 쓰려 했지만, 이 프로젝트가 이미 `kafkaTemplate.send(topic, sagaId, json)` 형태로 **Kafka message key 를 sagaId 로** 쓰고 있었습니다.

OTel Kafka 계측이 `OTEL_INSTRUMENTATION_KAFKA_EXPERIMENTAL_SPAN_ATTRIBUTES=true` 에서 자동으로 `messaging.kafka.message.key` 속성을 span 에 박아주므로, **Collector 에서 attribute 이름만 바꿔주면** 됩니다:

```yaml
# docker/otel-collector-config.yml
transform/saga_id:
  trace_statements:
    - context: span
      statements:
        - set(attributes["saga.id"], attributes["messaging.kafka.message.key"])
            where attributes["messaging.kafka.message.key"] != nil
```

이제 Grafana Tempo 에서 TraceQL 한 줄로 **해당 saga 에 연루된 5개 서비스의 모든 span** 을 한 화면에 볼 수 있습니다:

```traceql
{ .saga.id = "b3a7c1a2-..." }
```

특히 보상 트랜잭션 디버깅에 강력합니다. 예: "주문이 CANCELLED 로 끝났는데, stock 복구까지 얼마나 걸렸는가?" 를 한 화면으로 추적.

### 용어 정리

| 용어 | 범위 | 예시 |
|------|------|------|
| `trace_id` | 하나의 분산 요청 전체 (서비스 간 공유) | 32 hex, `a1b2c3...` |
| `span_id` | trace 내 개별 작업 | 16 hex, `d4e5f6...` |
| `saga_id` | 비즈니스 키 (우리가 발급) | UUID, `OrderEntity.sagaId` |

`trace_id` 는 Kafka 비동기 경계에서 새 trace 로 갈라질 수 있지만, `saga_id` 는 한 주문을 끝까지 따라갑니다.

### 비즈니스 메트릭 (`SagaMetrics`)

Micrometer 기반으로 shop-order 에 커스텀 메트릭을 주입했습니다.

| 메트릭 | 타입 | 설명 |
|---|---|---|
| `saga_started_total` | counter | 주문 생성 누적 |
| `saga_terminated_total{status}` | counter | 상태별 종료 (COMPLETED/STOCK_FAILED/CANCELLED) |
| `saga_duration_seconds` | histogram | 생성 → terminal wall-clock |
| `saga_state_transition_total{from,to}` | counter | 전체 상태 전이 감사 |

### 자주 쓰는 PromQL

```promql
# Saga 성공률
sum(rate(saga_terminated_total{status="COMPLETED"}[5m]))
  / sum(rate(saga_terminated_total[5m]))

# 서비스별 p99 latency
histogram_quantile(0.99,
  sum by (application, le) (
    rate(http_server_requests_seconds_bucket[5m])))

# Kafka consumer lag (토픽별 합계)
sum by (topic) (kafka_consumergroup_lag)

# 현재 실행 중인 saga 추정
sum(saga_started_total) - sum(saga_terminated_total)
```

### Grafana 대시보드

`docker/grafana/provisioning/` 에 프로비저닝된 **"Shop — Saga Overview"** 대시보드:
- 상단 stat: 성공률, STOCK_FAILED / CANCELLED 비율, 평균 완료 시간
- Saga duration p50/p95/p99 히스토그램
- 서비스별 HTTP RED (Rate/Error/Duration)
- Kafka Consumer Lag
- JVM Heap / HikariCP 활성 커넥션

> 상세 가이드: [`docker/OTEL-SETUP.md`](docker/OTEL-SETUP.md), [`docker/METRICS-SETUP.md`](docker/METRICS-SETUP.md)

---

## 성능 튜닝 기록 (v2)

### 1차 병목: `stock_inbox_event` 풀스캔

스트레스 테스트 중 Tempo trace 에서 발견:

```
POST /orders                            8.66ms
└─ order-created publish
   └─ [shop-stock] onOrderCreated       29ms ⚠️
       └─ InboxEventRepository.existsByEventId           1ms
       └─ InboxEventRepository.findBySagaIdAndEventType  28ms 🚨
```

`EXPLAIN` 결과:

```
type  : ALL          ← 풀스캔
key   : NULL
rows  : 63,439
```

### 해결: 복합 인덱스 추가

`InboxEventEntity` 에 `(saga_id, event_type)` 복합 인덱스를 선언:

```java
@Table(
    name = "stock_inbox_event",
    indexes = {
        @Index(name = "idx_inbox_saga_event_type",
               columnList = "saga_id, event_type")
    }
)
```

컬럼 순서는 **카디널리티가 높은 쪽을 앞**에. `saga_id`(UUID, unique에 가까움) → `event_type`(8종) 순서로 두면 Left-prefix rule 에 의해 `saga_id` 단독 쿼리도 인덱스를 탑니다.

결과: `rows 63,439 → 1` (**약 30,000배 감소**), 28ms → 1ms.

### 응답 시간 vs 처리량의 구분

Tempo 가 보여준 "전체 saga 1m 33s" 는 **사용자 대기 시간이 아닙니다**.
`POST /orders` 는 8.66ms 만에 PENDING 으로 응답하고, 그 뒤는 모두 비동기입니다. 1m 33s 는 **시스템 처리량 (throughput)** 의 지표입니다.

이 구분이 중요한 이유: SLA 설계 시 "UX 지표(p95 latency)" 와 "capacity 지표(saga completion time under load)" 를 섞으면 안 됩니다.

---

## 부하 테스트 (Locust)

```bash
cd locust
source .venv/bin/activate

# Web UI (http://localhost:8089)
locust -f stress.py

# Headless
locust -f stress.py --host http://localhost:8082 \
       --users 50 --spawn-rate 5 --run-time 60s --headless
```

부하를 돌리는 동안 Grafana Tempo 에서 느린 trace 를 찾아 병목 span 을 뒤지면, 위의 인덱스 이슈 같은 것들이 바로 보입니다.

---

## API 예시

### 상품 등록

```http
POST http://localhost:8081/api/products
Content-Type: application/json

{
  "productName": "VitaminA",
  "quantity": 1000000,
  "productDesc": "z",
  "productPrice": 30000
}
```

### 주문 생성

```http
POST http://localhost:8082/orders
Content-Type: application/json

{
  "userId": 1,
  "deliveryAddress": "z",
  "items": [
    {
      "productId": "019d5cdc-da58-7101-8b23-c86a7e118858",
      "quantity": 1,
      "unitPrice": 50000,
      "productName": "비타민A"
    }
  ]
}
```

### Frontend

| 페이지 | 설명 |
|--------|------|
| `/` | 상품 목록, 주문 모달 |
| `/products/create` | 상품 등록 |
| `/orders/[orderId]` | 주문 상태 추적 (3초 폴링, 4단계 Saga 시각화) |

Next.js rewrites:
- `/api/*` → `localhost:8080` (shop-user)
- `/product-api/*` → `localhost:8081/api/*` (shop-product)
- `/order-api/*` → `localhost:8082/*` (shop-order)

---

## DB 초기화

```sql
truncate payment_inbox_event;
truncate payments;
truncate product_inbox_event;
truncate product_outbox_event;
truncate stock;
truncate stock_outbox_event;
truncate product;
truncate order_item;
truncate orders;
```

---

## 로드맵

### 완료

- [x] v1: Choreography Saga (PENDING → COMPLETED/STOCK_FAILED/CANCELLED)
- [x] v1: Inbox 패턴 (멱등성 + 보상 트랜잭션 페이로드 복원)
- [x] v1: 낙관적 락 + `@Retryable`
- [x] v1: Fake PG (90% 성공, Headless Kafka consumer)
- [x] v2: OpenTelemetry Java Agent — 코드 0줄 수정으로 분산 트레이스
- [x] v2: Loki + Promtail — `trace_id` 포함 로그
- [x] v2: OTel Collector 에서 `saga.id` derivation
- [x] v2: Prometheus + Kafka Exporter + Micrometer — JVM / HTTP / Kafka lag 지표
- [x] v2: `SagaMetrics` 비즈니스 지표 (성공률, duration histogram)
- [x] v2: Grafana 통합 대시보드 "Shop — Saga Overview"
- [x] v2: 1차 병목 해소 (Inbox 복합 인덱스, 28ms → 1ms)
- [x] **v2.1: Collector Gateway 패턴** — 메트릭 파이프라인을 Collector 경유로 통합 (k8s-ready)
  - Prometheus 는 receiver-only (`--web.enable-remote-write-receiver`) + Collector self-scrape 만
  - Collector `prometheus` receiver 가 Actuator / kafka-exporter / Tempo scrape → `prometheusremotewrite` 로 push
  - Micrometer 메트릭 이름 보존 → 기존 대시보드/PromQL 무수정

### 다음 (이 README 의 뒷이야기는 대화에 이어서)

- [ ] API Gateway (Spring Cloud Gateway) — traceId 발급 지점 통일 + 인증 gateway 화
- [ ] Outbox + Debezium — KafkaTemplate 직접 호출 → CDC 기반 신뢰성 확보
- [ ] AWS EKS 배포 — Helm / ArgoCD / Karpenter
- [ ] Alertmanager — consumer lag SLO, saga 실패율 임계 알림
- [ ] Frontend RUM (`@opentelemetry/sdk-trace-web`) — 브라우저 → Gateway → 백엔드 full trace
