## Project Overview

Microservices-based e-commerce platform practicing Event-Driven Architecture and Choreography-based Saga pattern.

| 서비스 | 포트 | 역할 |
|--------|------|------|
| shop-user | 8080 | 회원가입/로그인, Spring Security, Redis 세션 |
| shop-product | 8081 | 상품 관리, Kafka producer (`product-created`, `order-created` 소비) |
| shop-stock | 8083 | 재고 관리, Kafka consumer/producer |
| shop-order | 8082 | 주문 관리, Saga 조율 |
| shop-frontend | 3000 | Next.js (React 19, Tailwind v4, Zustand) |

## Infrastructure Setup

```bash
cd docker && docker compose up -d
./docker/create-topics.sh   # Kafka 토픽 생성 (최초 1회)
```

| 인프라 | 주소 |
|--------|------|
| MySQL | `localhost:23306` (db: `shop`, user: `dev_user`, pass: `dev_password`) |
| Redis | `localhost:16379` |
| Kafka | `localhost:9094` |
| Kafka UI | `http://localhost:18090` |
| RedisInsight | `http://localhost:15540` |

## Build & Run

```bash
# 각 서비스 루트에서
./gradlew bootRun
./gradlew test
./gradlew test --tests "com.ansj.shoporder.SomeTest"

# 프론트엔드
cd shop-frontend && npm run dev
```

Java 21, Spring Boot 3.5.x, Gradle.

## Saga 흐름 (현재 구현 상태)

```
[Client] POST /orders  (shop-order:8082)
    → OrderEntity(PENDING) 저장
    → order-created 발행 (key=sagaId)

[shop-stock] order-created 수신
    → inbox dedup (InboxEventEntity)
    → StockEntity.reserve() — 낙관적 락(@Version) + @Retryable
    → 성공: stock-reserved 발행
    → 실패: stock-reserve-failed 발행

[shop-order] stock-reserved 수신
    → sagaId로 OrderEntity 조회
    → order.stockReserved() → STOCK_RESERVED
    → payment-requested 발행

[shop-order] stock-reserve-failed 수신
    → order.stockFailed() → STOCK_FAILED (종료)

-- payment service 미구현 (payment-requested 이후 미완성) --
```

## Kafka 토픽 목록

| 토픽 | 발행 | 소비 |
|------|------|------|
| `product-created` | shop-product | shop-stock |
| `order-created` | shop-order | shop-stock |
| `stock-reserved` | shop-stock | shop-order |
| `stock-reserve-failed` | shop-stock | shop-order |
| `payment-requested` | shop-order | payment (미구현) |
| `payment-success` | payment (미구현) | shop-order, shop-stock |
| `payment-failed` | payment (미구현) | shop-order |
| `order-canceled` | shop-order | shop-stock (보상, 미구현) |

## 아키텍처 패턴

### 패키지 구조 (공통)
```
com.ansj.<service>/
  common/       # BaseEvent, AggregateId, SagaId, EventId, JsonUtil
  config/       # KafkaConfig, DataSourceConfig, ObjectMapperConfig
  <domain>/
    controller/
    service/    # 순수 persistence 담당
    repository/
    entity/
    model/      # 도메인 모델 (entity.toModel() 또는 Orders.from())
    dto/
    event/
      inbound/  # 수신 이벤트 DTO
      outbound/ # 발행 이벤트 DTO
  kafka/        # @KafkaListener
  usecase/      # 서비스 간 오케스트레이션 + Kafka 발행
  box/          # InboxEventEntity, OutboxEventEntity (shop-stock, shop-product)
```

### 이벤트 구조
- 모든 이벤트는 `BaseEvent` 상속: `eventType`, `eventId`, `sagaId`, `aggregateId`, `aggregateType`, `occurredAt`
- `@JsonValue` + `@JsonCreator` 쌍으로 구성된 value record: `EventId`, `SagaId`, `AggregateId`
    - **`@JsonCreator`는 반드시 `from(String)` 메서드에 붙여야 함** (JSON 역직렬화 시 string 값 수신)
- `@JsonSubTypes`는 `BaseEvent`에 등록 (소비하는 서비스 기준으로 등록)

### Inbox 패턴 (shop-stock)
`InboxEventService.existsByEventId()`로 중복 소비 방지. `eventId` unique 제약.

### Outbox 패턴
구조는 잡혀 있으나 아직 미활성. 현재는 `KafkaTemplate.send()` 직접 호출.

### OrderEntity 상태 전이
`PENDING → STOCK_RESERVED → COMPLETED`
`PENDING → STOCK_FAILED` (terminal)
`STOCK_RESERVED → PAYMENT_FAILED → CANCELLED` (보상 완료)

`OrderEntity.addItem()`으로 양방향 관계 설정 필수 (`@ManyToOne` 관계).

### Next.js rewrites (next.config.ts)
- `/api/*` → `localhost:8080` (shop-user)
- `/product-api/*` → `localhost:8081/api/*` (shop-product)
- `/order-api/*` → `localhost:8082/*` (shop-order)

### Frontend 페이지
- `/` — 상품 목록, 주문 모달
- `/products/create` — 상품 등록
- `/orders/[orderId]` — 주문 상태 추적 (3초 폴링, Saga 단계 시각화)
