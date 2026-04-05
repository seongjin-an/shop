# Shop — Event-Driven Microservices

Choreography-based Saga 패턴을 실습하는 이커머스 플랫폼입니다.

## 서비스 구성

| 서비스 | 포트 | 역할 |
|--------|------|------|
| shop-user | 8080 | 회원가입 / 로그인 (Spring Security, Redis 세션) |
| shop-product | 8081 | 상품 등록 / 조회 |
| shop-stock | 8083 | 재고 관리 (Inbox 패턴, 낙관적 락) |
| shop-order | 8082 | 주문 생성 / Saga 조율 |
| shop-payment | 8084 | 결제 처리 (Fake PG, Headless) |
| shop-frontend | 3000 | Next.js UI |

## 기술 스택

- **Backend** Java 21, Spring Boot 3.5, Spring Data JPA, Spring Kafka
- **Frontend** Next.js 15, React 19, Tailwind CSS v4, Zustand
- **Infra** MySQL, Redis, Apache Kafka

## 빠른 시작

```bash
# 1. 인프라 실행
cd docker && docker compose up -d

# 2. Kafka 토픽 생성 (최초 1회)
./docker/create-topics.sh

# 3. 각 서비스 실행 (별도 터미널)
cd shop-user     && ./gradlew bootRun
cd shop-product  && ./gradlew bootRun
cd shop-stock    && ./gradlew bootRun
cd shop-order    && ./gradlew bootRun
cd shop-payment  && ./gradlew bootRun
cd shop-frontend && npm run dev
```

| 인프라 | 주소 |
|--------|------|
| MySQL | `localhost:23306` (db: `shop`, user: `dev_user`, pass: `dev_password`) |
| Redis | `localhost:16379` |
| Kafka | `localhost:9094` |
| Kafka UI | `http://localhost:18090` |
| RedisInsight | `http://localhost:15540` |

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

## 주요 패턴

### Inbox 패턴
`shop-stock`, `shop-payment`에서 중복 이벤트 소비를 방지합니다.
`InboxEventEntity.eventId`에 unique 제약을 걸어 멱등성을 보장합니다.

shop-stock의 inbox는 보상 트랜잭션 시에도 활용됩니다.
`order-canceled` 수신 시 `sagaId`로 원본 `order-created` 페이로드를 조회하여 아이템 목록을 복원합니다.

### 낙관적 락 + Retry
`StockEntity`에 `@Version`을 적용하고 `StockService.reserve()`에 `@Retryable`을 설정하여 동시 주문 충돌을 자동 재시도합니다.

### Fake PG (shop-payment)
실제 PG사 연동 없이 결제 흐름을 시뮬레이션합니다.
- 90% 확률 성공, 10% 확률 실패
- `pgTransactionId`, `pgAuthCode`, `maskedCardNumber` 등 실제 PG 응답 필드를 생성하여 저장
- HTTP 서버 없이 Kafka consumer만 동작 (`web-application-type: none`)

### Outbox 패턴
구조는 잡혀 있으나 미활성. 현재는 `KafkaTemplate.send()` 직접 호출.

## 패키지 구조

각 백엔드 서비스의 공통 구조:

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

### 이벤트 구조
- 모든 이벤트는 `BaseEvent` 상속: `eventType`, `eventId`, `sagaId`, `aggregateId`, `aggregateType`, `occurredAt`
- `@JsonSubTypes`는 `BaseEvent`에 등록 (소비하는 서비스 기준으로 등록)
- `@JsonCreator`는 반드시 `from(String)` 메서드에 붙여야 함 (JSON 역직렬화 시 string 값 수신)

## Frontend

| 페이지 | 설명 |
|--------|------|
| `/` | 상품 목록, 주문 모달 |
| `/products/create` | 상품 등록 |
| `/orders/[orderId]` | 주문 상태 추적 (3초 폴링, 4단계 Saga 시각화) |

Next.js rewrites:
- `/api/*` → `localhost:8080` (shop-user)
- `/product-api/*` → `localhost:8081/api/*` (shop-product)
- `/order-api/*` → `localhost:8082/*` (shop-order)
