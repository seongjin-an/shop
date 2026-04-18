# OpenTelemetry 관측성 스택 (v2 Phase 1)

> Step 1. OTel Java Agent + LGTM 스택 + 트레이스-로그 상관관계
> Step 2. `sagaId` span 속성 자동 부여 (Kafka key 기반)

## 구성 요소

| 컴포넌트 | 포트 | 역할 |
|---|---|---|
| Grafana | `http://localhost:13000` | 시각화 (Loki + Tempo 통합) |
| Tempo | `http://localhost:13200` | 분산 트레이스 저장소 |
| Loki | `http://localhost:13100` | 로그 저장소 (기존) |
| Promtail | — | 각 서비스 `logs/app.log` → Loki (기존) |
| OTel Collector | gRPC `localhost:24317`, HTTP `localhost:24318` | 트레이스 수신 → Tempo 전달 |
| OTel Java Agent | JVM `-javaagent:` | Spring / JDBC / Kafka 자동 계측 |

## 데이터 흐름

```
Spring Boot 서비스 (javaagent)
    └─ OTLP(gRPC) ──→ OTel Collector ──→ Tempo
                                   └──→ (선택) Loki OTLP 경로
                                   └──→ Transform: messaging.kafka.message.key → saga.id

파일 로그 ──→ Promtail ──→ Loki ──→ Grafana
                                     └─ derivedFields: "trace_id=(\w+)" → Tempo 로 점프
```

## 최초 1회 세팅

```bash
# 1) OTel Java Agent 다운로드 (docker/agent/opentelemetry-javaagent.jar)
cd docker
./download-otel-agent.sh

# 2) 관측성 스택 기동 (Tempo / OTel Collector / Grafana / Loki / Promtail)
docker compose up -d tempo otel-collector grafana loki promtail

# 3) 각 서비스 재기동 — build.gradle 의 bootRun 이 agent 를 자동으로 붙여준다
cd ../shop-order && ./gradlew bootRun
cd ../shop-stock && ./gradlew bootRun
cd ../shop-payment && ./gradlew bootRun
cd ../shop-product && ./gradlew bootRun
cd ../shop-user && ./gradlew bootRun
```

## 동작 확인

### 1. 주문 생성 후 Grafana Tempo 에서 trace 찾기

```bash
# 상품 등록
curl -X POST localhost:8081/api/products \
  -H 'Content-Type: application/json' \
  -d '{"name":"테스트상품","price":1000,"stock":100}'

# 주문 생성 (sagaId = 반환되는 orderId 기반)
curl -X POST localhost:8082/orders \
  -H 'Content-Type: application/json' \
  -d '{"userId":"u1","items":[{"productId":"<product-id>","quantity":1}]}'
```

Grafana → `Explore` → `Tempo` → `Search` 탭 → `Service Name = shop-order` → `Run query`.
주문 하나의 trace 를 클릭하면 다음 flow 가 **한 폭포수**에 보인다:

```
POST /orders (shop-order)
 └─ orders.createOrder (transaction)
     └─ Hibernate: INSERT orders
     └─ Hibernate: INSERT order_items
     └─ kafka.send order-created  ← span 에 saga.id 자동 부착
         └─ kafka.consume order-created (shop-stock) ← 다른 서비스, 같은 trace
             └─ StockEntity.reserve
             └─ kafka.send stock-reserved
                 └─ kafka.consume stock-reserved (shop-order)
                     └─ kafka.send payment-requested
                         └─ kafka.consume payment-requested (shop-payment)
                             └─ ProcessPaymentUseCase.process
                             └─ kafka.send payment-success
                                 └─ (shop-order, shop-stock 두 소비자로 fan-out)
```

### 2. saga.id 로 검색

Tempo Search 에서 **TraceQL** 탭 사용:

```
{ .saga.id = "b3a7c1a2-..." }
```

이 쿼리 하나로 **해당 saga 에 연루된 모든 서비스의 모든 span** 을 하나의 리스트로 보여준다.
보상 트랜잭션이 포함된 flow 디버깅에 이게 특히 유용하다.

### 3. 로그 → 트레이스 pivot

Grafana → Explore → Loki → `{service="shop-order"}` 쿼리 → 결과 중 하나 펼치기.
로그 라인에 `trace_id=abc123 span_id=def456` 이 포함되어 있고, Grafana 가
`derivedFields` 설정에 따라 **"TraceID" 버튼**을 자동 생성한다.
버튼 클릭 → 바로 해당 Tempo trace 로 이동.

### 4. 트레이스 → 로그 pivot

Tempo 트레이스 뷰의 각 span 에 **"Logs for this span"** 버튼.
`tracesToLogsV2` 설정에 의해 `{service_name="shop-order"} |= "abc123"` 쿼리가 자동 실행된다.

## Step 2 상세: saga.id 부착 방식

**질문**: 왜 Java 코드를 한 줄도 안 건드렸는데 saga.id 가 span 에 찍혔는가?

**답**: 이 프로젝트는 `kafkaTemplate.send(topic, sagaId, json)` 형태로
**Kafka message key 자체를 sagaId 로 사용**하고 있다. OTel Java Agent 의
Kafka instrumentation 은 `OTEL_INSTRUMENTATION_KAFKA_EXPERIMENTAL_SPAN_ATTRIBUTES=true`
설정 하에 producer/consumer span 에 `messaging.kafka.message.key` 속성을 자동으로 넣는다.

OTel Collector 의 `transform/saga_id` 프로세서가 이 값을 `saga.id` 로 복사한다:

```yaml
transform/saga_id:
  trace_statements:
    - context: span
      statements:
        - set(attributes["saga.id"], attributes["messaging.kafka.message.key"])
            where attributes["messaging.kafka.message.key"] != nil
```

## 한계 및 다음 개선 포인트

1. **HTTP / DB span 에는 saga.id 가 없다.**
   현재는 Kafka span 에만 붙는다. REST 엔드포인트 진입 시점부터 saga.id 를
   전파하려면 `CreateOrderUseCase` 에서 OTel Baggage API 사용 필요:

   ```java
   // build.gradle:
   //   implementation 'io.opentelemetry:opentelemetry-api:1.38.0'

   import io.opentelemetry.api.baggage.Baggage;
   import io.opentelemetry.context.Scope;

   try (Scope ignored = Baggage.current().toBuilder()
           .put("saga.id", order.getSagaId().toString())
           .build().makeCurrent()) {
       kafkaTemplate.send(orderCreatedTopic, order.getSagaId().toString(), json);
   }
   ```

   Baggage 는 propagator `baggage` (이미 `OTEL_PROPAGATORS=tracecontext,baggage` 로
   활성) 를 통해 downstream Kafka consumer 로 자동 전파되고, Collector 의
   `attributes/baggage` 프로세서가 span attribute 로 승격한다.

2. **메트릭 수집 미활성.**
   현재 `OTEL_METRICS_EXPORTER=none`. Prometheus / Mimir 추가 후 `otlp` 로 변경 시
   JVM / HTTP / Kafka lag / DB pool 메트릭이 자동 수집된다.

3. **Service Graph / RED 메트릭 미활성.**
   Tempo `metrics_generator.processor.service_graphs` 를 활성하려면 Prometheus 필요.

4. **Frontend RUM 미연결.**
   Next.js 에 `@opentelemetry/sdk-trace-web` + `@opentelemetry/exporter-trace-otlp-http`
   를 붙이면 브라우저 → Gateway → 백엔드 전체를 하나의 trace 로 이을 수 있다.
   이건 Gateway(Phase 3) 도입 후 작업.

## 트러블슈팅

| 증상 | 원인/해결 |
|---|---|
| `docker/agent/opentelemetry-javaagent.jar` not found | `docker/download-otel-agent.sh` 실행 |
| Tempo 에 span 이 하나도 안 뜸 | Collector 로그 확인: `docker compose logs otel-collector`. 서비스가 `localhost:24317` 로 접근 가능한지 (방화벽) |
| `trace_id=` 가 로그에 찍히지 않음 | `logback-spring.xml` 패턴이 업데이트 됐는지 확인. bootRun 재시작 |
| Kafka 이벤트가 trace 로 연결 안 됨 | `OTEL_PROPAGATORS=tracecontext,baggage` 확인. Consumer 측 agent 도 활성화됐는지 |
| 특정 서비스만 span 없음 | 해당 서비스 `build.gradle` 의 `bootRun` 블록에 agent 설정이 있는지 확인 |

## 변경 파일 요약

**신규 파일**
- `docker/tempo-config.yml`
- `docker/otel-collector-config.yml`
- `docker/download-otel-agent.sh`
- `docker/OTEL-SETUP.md` (이 문서)

**수정 파일**
- `docker/compose.yaml` — Tempo, OTel Collector 서비스 추가, Grafana depends_on 확장
- `docker/grafana/provisioning/datasources/loki.yml` — Tempo datasource 및 상호 pivot 설정 추가
- `shop-order/build.gradle` — bootRun 에 OTel agent JVM 옵션 추가
- `shop-stock/build.gradle` — 상동
- `shop-payment/build.gradle` — 상동
- `shop-product/build.gradle` — 상동 (+bootRun 블록 신규)
- `shop-user/build.gradle` — 상동 (+bootRun 블록 신규)
- `shop-{order,stock,payment,product,user}/src/main/resources/logback-spring.xml` — 로그 패턴에 `trace_id`/`span_id` 추가
