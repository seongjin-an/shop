# Shop v2 진행 상황 정리

> 작성일: 2026-04-18
> 대상: MSA 기반 이커머스(주문/결제/재고) 프로젝트의 v2 진화 로그

---

## 0. v2 목표

v1은 Choreography Saga + Inbox/Outbox 구조로 "동작하는" MSA를 만든 단계였다.
v2는 실무에 가까운 **견고함**(관측성, 성능, 장애 내성)을 붙이는 단계.

최종 로드맵(우선순위):

1. 관측성 스택 (OpenTelemetry + LGTM) ← **Step 1 완료**
2. Baggage로 `sagaId` 전파 ← **Step 2 완료 (Collector에서 derivation)**
3. 병목 분석 & 튜닝 ← **진행 중 (DB 인덱스까지 적용)**
4. API Gateway 도입 (traceId 발급 지점 통일)
5. Outbox + Debezium (현재 KafkaTemplate 직접 호출 → Change Data Capture 기반)
6. AWS EKS 배포

---

## 1. Step 1: OpenTelemetry + LGTM 스택

### 구성

```
Spring Boot App (OTel Java Agent) → OTel Collector → Tempo (trace)
                                                  └→ Loki (log, 이미 있던 것)
                                                  └→ Mimir/Prometheus (metric, 예정)
Grafana에서 한 화면으로 연결 (TracesToLogs)
```

### 핵심 설계 결정

**애플리케이션 코드 변경 0줄.**
OpenTelemetry Java Agent v2.6.0을 `-javaagent:` 로만 붙였다.
각 서비스 `build.gradle`의 `bootRun` 태스크에만 다음을 추가:

```groovy
tasks.named('bootRun') {
    def otelAgentJar = file("${projectDir}/../docker/agent/opentelemetry-javaagent.jar")
    if (otelAgentJar.exists()) {
        jvmArgs "-javaagent:${otelAgentJar.absolutePath}"
        environment 'OTEL_SERVICE_NAME', project.name
        environment 'OTEL_EXPORTER_OTLP_ENDPOINT', 'http://localhost:24317'
        environment 'OTEL_EXPORTER_OTLP_PROTOCOL', 'grpc'
        environment 'OTEL_TRACES_EXPORTER', 'otlp'
        environment 'OTEL_METRICS_EXPORTER', 'none'
        environment 'OTEL_LOGS_EXPORTER', 'none'
        environment 'OTEL_PROPAGATORS', 'tracecontext,baggage'
        environment 'OTEL_INSTRUMENTATION_KAFKA_EXPERIMENTAL_SPAN_ATTRIBUTES', 'true'
        environment 'OTEL_INSTRUMENTATION_KAFKA_PRODUCER_PROPAGATION_ENABLED', 'true'
    }
}
```

**자동 계측되는 것들**: Spring MVC 엔드포인트, JDBC(Hibernate 쿼리 단위로 span), Kafka Producer/Consumer, HTTP Client.

### 로그 상관관계

모든 서비스의 `logback-spring.xml` 패턴에 추가:

```
[trace_id=%X{trace_id:-} span_id=%X{span_id:-}]
```

이제 Grafana에서 Tempo의 span을 클릭 → TracesToLogsV2가 Loki에 같은 `trace_id`를 가진 로그를 자동으로 필터링해서 보여줌.

---

## 2. Step 2: sagaId 전파 — Baggage 대신 Collector-side Derivation

### 원래 아이디어

사용자 요청: "sagaId를 Baggage로 넣어서 span attribute로 박자."

### 실제로 택한 방법 (더 영리한 쪽)

`CreateOrderUseCase` 코드를 다시 보니 이미 이렇게 보내고 있었다:

```java
kafkaTemplate.send(topic, sagaId.toString(), json);
                 // ^^^^^^^^^^^^^^^^^^^ Kafka message key 가 sagaId
```

OTel의 Kafka 계측이 message key를 `messaging.kafka.message.key` 속성으로 span에 자동으로 박아준다. 그래서 **애플리케이션 코드에 Baggage 관련 한 줄도 안 넣고** OTel Collector에서 attribute rename 하나만 해주면 끝났다:

```yaml
# docker/otel-collector-config.yml
transform/saga_id:
  trace_statements:
    - context: span
      statements:
        - set(attributes["saga.id"], attributes["messaging.kafka.message.key"])
            where attributes["messaging.kafka.message.key"] != nil
```

**결과**: Grafana Tempo에서 `{ saga.id = "..." }`로 특정 주문의 모든 span(5개 서비스, 10+ Kafka 메시지 홉)을 한번에 조회 가능.

### 용어 정리 (사용자 질문 답변)

| 용어     | 범위                                | 예시                                   |
|---------|-------------------------------------|---------------------------------------|
| trace_id | 하나의 분산 요청 전체 (서비스 간 공유) | `a1b2c3...` (32 hex)                  |
| span_id  | trace 내 개별 작업 하나              | `d4e5f6...` (16 hex)                  |
| saga_id  | 비즈니스 키 (우리가 직접 발급)       | `UUID` (OrderEntity.sagaId와 동일)    |

trace_id는 HTTP 요청 하나당 1개지만, Kafka 비동기 경계를 넘으면 자동 계측이 새 trace를 만들기도 한다. `sagaId`는 그런 상황에서도 "동일 주문"을 묶어줄 비즈니스 관점의 ID다.

---

## 3. 삽질 기록

### 3-1. `start-services.sh` 가 Java 8로 실행됨

원인: macOS에서 sdkman은 `.zshrc`에 있는데 스크립트가 `bash -l`로 돌아서 `.bash_profile`을 읽음 → Java 8 fallback.

해결 (사용자 요청대로 **PATH 건드리지 않음**):
```bash
# 현재 셸의 JAVA_HOME을 캡쳐해서 서브셸에 env 로 명시 전달
env JAVA_HOME="$JAVA_HOME" bash -c "./gradlew bootRun" > logs/startup.log 2>&1 &
```

### 3-2. Loki 컨테이너가 안 뜸

에러:
```
cannot unmarshal !!str `256kb` into bool
```

원인: 신버전 Loki에서 `max_line_size_truncate`가 bool 타입으로 바뀌었다(옛날엔 size).

```yaml
# Before (깨짐)
max_line_size_truncate: 256kb

# After
max_line_size: 256kb              # size
max_line_size_truncate: true      # bool
```

---

## 4. 스트레스 테스트 & 트레이스 해석

스트레스 테스트 후 Grafana Tempo에서 관측된 것:

```
POST /orders                            8.66ms   ← 사용자가 받는 응답
└─ order-created publish                 ...
   ├─ [shop-stock] onOrderCreated       29ms    ⚠️ 여기서 튐
   │   └─ InboxEventRepository.existsByEventId  (정상, 1ms)
   │   └─ InboxEventRepository.findBySagaIdAndEventType  28ms 🚨
   │   └─ StockEntity.reserve            ...
   ...
전체 saga 완료까지                       1m 33s   ← wall-clock (시스템 처리량 지표)
```

**사용자의 중요한 교정**: "POST /orders는 8.66ms만에 PENDING으로 응답한다. 1m 33s는 사용자 대기시간이 아니라 비동기 saga 완료까지의 벽시계 시간이고, 이건 **시스템 처리량**의 지표지 UX 지표는 아니다."

맞는 지적이었고, 이후 분석은 "처리량 관점의 병목"으로 프레임을 다시 잡음.

---

## 5. 1차 병목: `stock_inbox_event` 풀스캔

### EXPLAIN 결과 (Before)

```
SELECT * FROM stock_inbox_event
 WHERE saga_id = '...' AND event_type = 'ORDER_CREATED';

type          : ALL      ← 풀스캔
possible_keys : NULL
key           : NULL
rows          : 63,439
```

Inbox에 이벤트가 6만 건 쌓였는데 인덱스가 없어서 보상 트랜잭션(`CompensateStockUseCase.onPaymentSuccess`) 경로에서 매번 풀스캔.

### 수정

`shop-stock/box/entity/InboxEventEntity.java`:

```java
@Table(
    name = "stock_inbox_event",
    indexes = {
        @Index(name = "idx_inbox_saga_event_type",
               columnList = "saga_id, event_type")
    }
)
```

Hibernate `ddl-auto=update`가 기존 테이블에는 인덱스를 새로 달아주지 않는 경우도 있어서 fallback용 DDL 파일도 작성:

```sql
-- docker/migrations/2026-04-18_add_inbox_composite_index.sql
CREATE INDEX idx_inbox_saga_event_type
  ON stock_inbox_event (saga_id, event_type);
```

### 복합 인덱스 컬럼 순서 근거

`(saga_id, event_type)` 순서는 **카디널리티가 높은 쪽을 앞**에 두는 원칙.
- `saga_id`: UUID, 사실상 unique에 가까움 → 카디널리티 매우 높음
- `event_type`: 8개 토픽 중 하나 → 카디널리티 낮음

Left-prefix rule에 의해 `saga_id`만 쓰는 쿼리도 이 인덱스를 탄다.

### 기대값 (After)

```
type          : ref
key           : idx_inbox_saga_event_type
rows          : 1              ← 63,439 → 1, 약 30,000x 감소
```

---

## 6. 2차 관측: 낙관락 예외(ObjectOptimisticLockingFailureException)

스트레스 테스트 재실행 중 `shop-stock`에서 발생:

```
org.springframework.orm.ObjectOptimisticLockingFailureException:
  Row was updated or deleted by another transaction...
  at StockService.confirmReservations(...)
  at CompensateStockUseCase.onPaymentSuccess(CompensateStockUseCase.java:41)
  at StockKafkaConsumer.lambda$onPaymentSuccess$2(StockKafkaConsumer.java:79)
```

**결론: 예상대로의 정상 동작.**
CLAUDE.md에도 명시: "낙관적 락(@Version) + @Retryable".

예시: 같은 상품(예: `productId=42`)에 대해 동시에 여러 주문이 들어오면 여러 트랜잭션이 같은 `StockEntity`의 `reservedQuantity`를 감소시키려 함.
- Tx1: version=5 → 6 (성공)
- Tx2: version=5 → ? (실패, Tx1이 먼저 6으로 올려버림 → `OptimisticLockException`)

이후 `@Retryable`이 재시도하면 version=6을 읽어 다시 시도 → 성공.

확인 필요 사항(다음 TODO):
- 이 경로(`confirmReservations`)에 `@Retryable`이 **실제로** 붙어있는지
- 재시도 횟수 vs give-up 임계
- 최종적으로 DLT로 빠지는지, 재시도로 성공하는지 → Tempo trace로 확인 가능

---

## 7. 생성/수정한 파일 목록

### 신규
- `docker/tempo-config.yml`
- `docker/otel-collector-config.yml`
- `docker/download-otel-agent.sh`
- `docker/OTEL-SETUP.md`
- `docker/migrations/2026-04-18_add_inbox_composite_index.sql`

### 수정
- `docker/compose.yaml` — tempo, otel-collector 서비스 추가
- `docker/grafana/provisioning/datasources/loki.yml` — Tempo 데이터소스 + `tracesToLogsV2`
- `docker/loki-config.yml` — `max_line_size_truncate` 스키마 수정
- 5개 서비스의 `build.gradle` — `bootRun`에 OTel agent 설정
- 5개 서비스의 `logback-spring.xml` — `trace_id`/`span_id` 패턴 추가
- `start-services.sh` — JAVA_HOME 명시 전달 + 사용자가 health check 블록 주석 처리(의도적)
- `shop-stock/.../box/entity/InboxEventEntity.java` — 복합 인덱스 추가

---

## 8. 다음 할 일 (우선순위 순)

1. **[진행 중]** 인덱스 적용 검증
   - `shop-stock` 재기동
   - `SHOW INDEX FROM stock_inbox_event;` 로 `idx_inbox_saga_event_type` 확인
   - 없으면 수동 DDL 실행
   - After EXPLAIN 으로 `type=ref`, `rows=1` 확인
   - 스트레스 테스트 재실행 → Tempo에서 29ms → ~1ms 확인

2. **Prometheus + Kafka Consumer Lag 지표**
   - Kafka Exporter → Prometheus
   - Consumer group별 lag을 Grafana 패널로

3. **Grafana 통합 대시보드**
   - Saga 성공률 / 평균 완료 시간 / STOCK_FAILED·PAYMENT_FAILED 비율
   - 서비스별 p50/p95/p99 latency

4. **Consumer 병렬성 튜닝**
   - `concurrency` 설정 조정
   - partition 수 재검토

5. **낙관락 재시도 경로 검증**
   - `@Retryable` 적용 여부, 백오프 전략
   - DLT 구성

6. 이후: API Gateway, Outbox + Debezium, EKS
