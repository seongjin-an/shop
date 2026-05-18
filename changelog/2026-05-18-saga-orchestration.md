# Saga 패턴: Choreography vs Orchestration

## 1. 두 방식 한눈에 비교

### Choreography (이전 구조)

서비스들이 이벤트를 통해 서로 대화한다. 누가 전체 흐름을 알고 있는 것이 아니라, 각자 "내 차례가 왔네" 하고 반응하는 방식이다.

```
┌─────────────┐         ┌─────────────┐         ┌─────────────┐
│  shop-order │         │  shop-stock │         │shop-payment │
└──────┬──────┘         └──────┬──────┘         └──────┬──────┘
       │                       │                       │
       │ stock-reservation-    │                       │
       │ requested ───────────→│                       │
       │                       │ (재고 예약)            │
       │                       │                       │
       │←── stock-reserved ────│                       │
       │                       │                       │
       │  "allReserved?"        │                       │
       │  → 내가 판단           │                       │
       │                       │                       │
       │─────────── payment-requested ────────────────→│
       │                       │                       │ (결제 처리)
       │                       │                       │
       │←──────────────── payment-success ─────────────│
       │                       │                       │
       │  "성공? confirm 발행"  │                       │
       │  → 내가 판단           │                       │
       │                       │                       │
       │─── stock-confirm-requested ──────────────────→│(stock이 수신)
       │                       │←─────────────────────
```

**흐름 판단 로직이 여러 usecase에 분산되어 있다.**

- `CreateOrderUseCase` → 재고 예약 명령 발행 결정
- `StockReserveResultUseCase` → allItemsReserved 판단, 결제 요청 결정
- `PaymentResultUseCase` → confirm/release 결정

---

### Orchestration (현재 구조)

Orchestrator가 전체 흐름을 알고 있다. 각 서비스는 명령을 받아 실행하고 결과만 돌려준다.

```
                    ┌───────────────────────────────────┐
                    │       OrderSagaOrchestrator        │
                    │                                   │
                    │  START                            │
                    │   → 재고 예약 명령 fan-out         │
                    │                                   │
                    │  onStockReserved()                │
                    │   → 전체 예약 완료? → 결제 요청   │
                    │                                   │
                    │  onPaymentSuccess()               │
                    │   → 재고 확정 명령 fan-out         │
                    │                                   │
                    │  onStockReserveFailed()           │
                    │   → 기예약 아이템 해제             │
                    │                                   │
                    │  onPaymentFailed()                │
                    │   → 보상: 재고 해제 fan-out        │
                    └───────────┬───────────────────────┘
                                │
              ┌─────────────────┼──────────────────┐
              │                 │                  │
              ↓ 명령             ↑ 결과             ↓ 명령
       ┌──────────────┐                    ┌──────────────┐
       │  shop-stock  │                    │ shop-payment │
       │  (실행만 함) │                    │  (실행만 함) │
       └──────────────┘                    └──────────────┘
```

**모든 흐름 판단이 `OrderSagaOrchestrator` 한 곳에 집중된다.**

---

## 2. Saga 상태 전이 흐름

```
                       [주문 생성]
                           │
                           ▼
                       [PENDING]
                           │
              재고 예약 명령 fan-out (아이템 수만큼)
                           │
          ┌────────────────┴───────────────┐
          │ stock-reserved (all items)     │ stock-reserve-failed
          ▼                               ▼
   [STOCK_RESERVED]                  [STOCK_FAILED] ✗
          │                          (기예약 아이템 해제)
    결제 요청 명령
          │
   ┌──────┴──────┐
   │ payment-    │ payment-
   │ success     │ failed
   ▼             ▼
[COMPLETED] ✓  [PAYMENT_FAILED]
                    │
              재고 해제 fan-out
                    │
              [CANCELLED] ✗
```

---

## 3. Orchestrator는 모든 도메인 지식이 필요한가?

이것이 Orchestration의 핵심 트레이드오프다.

### 두 가지 종류의 지식

| 종류 | 담당 | 예시 |
|------|------|------|
| **흐름 지식** | Orchestrator | 재고 예약 후 → 결제 요청 |
| **비즈니스 로직** | 각 서비스 | 재고를 어떻게 예약하는가, 결제 PG 호출 방법 |

Orchestrator는 **"무엇을 언제"** 만 알고, **"어떻게"** 는 모른다.

```java
// Orchestrator가 아는 것 (흐름)
case STOCK_RESERVED → payment-requested 발행

// Orchestrator가 모르는 것 (비즈니스 로직)
// 결제가 내부적으로 PG사 호출을 어떻게 하는지
// 재고 예약이 낙관락인지 비관락인지
```

### 결합도의 본질

Choreography와 Orchestration의 결합도 차이는 **양**이 아니라 **위치**다.

```
Choreography → 분산된 결합도 (이벤트 계약이 암묵적으로 공유됨)
Orchestration → 집중된 결합도 (Orchestrator에 명시적으로 드러남)
```

"결합도가 숨어있다 ≠ 결합도가 없다"

---

## 4. Orchestrator 장애 대응

Orchestrator가 죽었을 때 Saga가 멈추지 않도록 다음 레이어가 보호한다.

```
장애 대응 레이어
─────────────────────────────────────────────────────────────

1. Outbox 패턴
   DB 상태 업데이트 + 명령 발행을 같은 트랜잭션으로 묶음
   → 명령 유실 원천 차단

   @Transactional
   orchestrator.onPaymentSuccess() {
       order.paymentCompleted()       ← DB
       outboxService.save(confirm)    ← 같은 트랜잭션
   }
   → Orchestrator가 죽어도 Debezium이 명령 발행

2. Kafka offset 미커밋 (MANUAL_IMMEDIATE)
   처리 완료 전에 죽으면 재시작 후 같은 메시지 재수신
   → 중복 처리 가능하므로 멱등성(Inbox 패턴) 필요

3. Inbox 패턴 (이미 구현됨)
   eventId unique 제약으로 중복 메시지 처리 방지
   → 멱등성 보장

4. Saga Watchdog (선택적 추가)
   중간 상태에서 일정 시간 멈춘 Saga 감지

   @Scheduled(fixedDelay = 60_000)
   void detectStaleSagas() {
       // WAITING_PAYMENT 상태로 10분 이상 → 타임아웃 처리
   }

5. 멀티 인스턴스 + sagaId 파티셔닝
   인스턴스 장애 시 Kafka 리밸런싱으로 자동 인계
   동일 sagaId → 동일 파티션 → 동일 인스턴스 (순서 보장)
```

> Choreography도 같은 문제를 갖는다. shop-order가 죽으면 payment-success를 수신하지 못해
> 주문이 STOCK_RESERVED에 멈춘다. 이 레이어들은 두 방식 모두에 필요하다.

---

## 5. 실무 선택 기준

### 규모별 경향

| 규모 | 경향 |
|------|------|
| 스타트업 / 소규모 | Choreography — 팀이 작고 빠르게 개발 |
| 중규모 (팀 3~5개) | Choreography로 시작 → CS 이슈 쌓이면서 Orchestration 도입 |
| 대규모 | 혼용 — 핵심 주문 흐름은 Orchestration, 알림/포인트는 Choreography |

### 실무에서 자주 쓰는 방법

**1. 워크플로우 엔진 (Temporal / Netflix Conductor)**
```java
// Temporal 예시: 재시도/타임아웃/보상을 프레임워크가 처리
@WorkflowImpl
public class OrderWorkflow {
    public void processOrder(OrderRequest req) {
        stockActivity.reserve(req);   // 실패 시 자동 재시도
        paymentActivity.charge(req);  // 타임아웃 설정 가능
        stockActivity.confirm(req);   // 실패 시 자동 보상
    }
}
```

**2. Choreography + 상태 추적 테이블**
이벤트 기반 흐름을 유지하되, saga_state 테이블에 현재 상태를 별도로 기록해 가시성 확보.

**3. 처음부터 Orchestration**
비즈니스적으로 중요하고 보상이 복잡한 주문 플로우에 적합.

### 흔한 실수

```
Choreography: 이벤트 10개 넘어가면서 "누가 뭘 발행하는지" 아무도 모르게 됨
              → 문서화 필수 (이 프로젝트의 CLAUDE.md Kafka 토픽 표가 그래서 중요)

Orchestration: Orchestrator에 비즈니스 로직 넣기 시작
              → 서비스들이 껍데기만 남고 Orchestrator가 3000줄 God Class가 됨
```

---

## 6. 이 프로젝트의 구현 변경 내역

### 변경 전후 파일 구조

```
삭제 (로직을 Orchestrator로 이관)
  usecase/StockReserveResultUseCase.java  — 152줄
  usecase/PaymentResultUseCase.java       — 133줄

신규
  saga/OrderSagaOrchestrator.java         — 전체 흐름 집중 관리

축소
  usecase/CreateOrderUseCase.java         orchestrator.start() 위임으로 단순화
  kafka/OrderKafkaConsumer.java           orchestrator.onXxx() 직접 호출
```

### 코드 비교

**이전 — 흐름 판단이 각 usecase에 분산**
```java
// StockReserveResultUseCase.java
public void onStockReserved(StockReservedEvent event) {
    item.markReserved();
    if (order.allItemsReserved() && ...) {
        order.stockReserved();
        kafkaPublisher.send(paymentRequestedTopic, ...);  // 여기서 결정
    }
}

// PaymentResultUseCase.java
public void onPaymentSuccess(PaymentSuccessEvent event) {
    order.paymentCompleted();
    for (item : order.getOrderItems()) {
        kafkaPublisher.send(stockConfirmRequestedTopic, ...);  // 여기서 결정
    }
}
```

**이후 — 모든 흐름 판단이 Orchestrator에 집중**
```java
// OrderSagaOrchestrator.java — Saga의 전체 여정을 한 파일에서 읽을 수 있다
public void start(Orders order, String traceId) { ... }           // 1단계
public void onStockReserved(StockReservedEvent event) { ... }     // 2단계
public void onStockReserveFailed(StockReserveFailedEvent e) { ... }
public void onPaymentSuccess(PaymentSuccessEvent event) { ... }   // 3단계
public void onPaymentFailed(PaymentFailedEvent event) { ... }
```

### Kafka Consumer 변화

```java
// 이전: 각 usecase로 분산
private final StockReserveResultUseCase stockReserveResultUseCase;
private final PaymentResultUseCase paymentResultUseCase;

// 이후: Orchestrator 하나로 통합
private final OrderSagaOrchestrator orchestrator;
```

---

## 7. 핵심 요약

> Choreography는 **"분산된 의사결정"**, Orchestration은 **"중앙화된 의사결정"** 이다.
>
> - Choreography: 흐름이 여러 서비스에 걸쳐 분산 → 파악하기 어렵지만 서비스 독립성 높음
> - Orchestration: 흐름이 Orchestrator에 집중 → 파악하기 쉽지만 Orchestrator가 많이 알아야 함
>
> 두 방식 모두 동일한 수준의 결합도를 가진다. 차이는 그 결합도가 **숨겨져 있느냐, 드러나 있느냐**다.
> 실무에서는 핵심 비즈니스 흐름에 Orchestration, 부가 기능에 Choreography를 혼용하는 경우가 많다.
