

---

## 수정 내용 & 이유

### Fix 1: `CompensateStockUseCase` — try-catch 제거

```java
// Before: 예외를 삼킴
try {
    stockService.confirmReservations(items);
    inboxEventService.createInboxEvent(event);
} catch (Exception e) {
    log.error(...); // 로그만 찍고 끝
}

// After: 예외를 그대로 전파
stockService.confirmReservations(items);
inboxEventService.createInboxEvent(event);
```

**이유**: 예외가 catch 안에서 소멸되면 리스너는 "정상 처리됨"으로 인식하고 ack를 날렸어요. 예외가 리스너까지 올라가야 ack를 막을 수 있어요.

---

### Fix 2: `StockKafkaConsumer` — 보상 흐름만 조건부 ack

```java
// Before: 성공/실패 관계없이 finally에서 무조건 ack
} finally {
    acknowledgment.acknowledge(); // 항상 실행됨
}

// After: 성공 시만 ack, 실패 시 throw → DefaultErrorHandler 위임
acknowledgment.acknowledge(); // try 블록 내 (성공 시만 도달)
} catch (Exception e) {
    log.error(...);
    throw e; // 다시 던져서 ErrorHandler가 처리하게 함
}
```

**이유**: `finally`는 예외 발생 여부와 무관하게 항상 실행돼요. 실패해도 ack가 커밋되면 Kafka는 "이 메시지 처리 완료"로 기록하고 재전달을 안 해요. `onOrderCreated`/`onProductCreated`는 유지 — 실패를 이미 이벤트(stock-reserve-failed)로 핸들링하는 흐름이라 현재 방식이 맞아요.

---

### Fix 3: `StockService` — `maxAttempts` 3 → 5, backoff 조정

```java
// Before
@Retryable(maxAttempts = 3, backoff = @Backoff(delay = 100, multiplier = 2))
// 총 대기: 100ms + 200ms = 300ms (3회 시도)

// After
@Retryable(maxAttempts = 5, backoff = @Backoff(delay = 50, multiplier = 2, maxDelay = 500))
// 총 대기: 50ms + 100ms + 200ms + 400ms = 750ms (5회 시도)
```

**이유**: 100 RPS에서 200명이 단일 StockEntity를 동시에 때리면 낙관적 락 충돌이 매우 잦아요. 3번 시도로는 충돌 경쟁에서 이기지 못하는 경우가 많았어요. 또한 초기 delay를 100ms → 50ms로 줄여 더 빠르게 재시도하되, `maxDelay=500ms`로 최대 대기시간을 캡핑했어요.

---

### Fix 4: `KafkaConfig` — `DefaultErrorHandler` 추가

```java
DefaultErrorHandler errorHandler = new DefaultErrorHandler(
    (record, exception) -> log.error("[ErrorHandler] 최종 처리 실패 — 건너뜁니다. ..."),
    new FixedBackOff(2_000L, 2L) // 2초 간격, 최대 2회 재시도
);
containerFactory.setCommonErrorHandler(errorHandler);
```

**이유**: `@Retryable` 5회가 전부 소진된 뒤에도 실패하면 예외가 Kafka 컨테이너에 도달해요. 여기서 ErrorHandler 없이 예외가 계속 나오면 **무한 루프** 가능성이 있어요 (재폴링 → 같은 메시지 → 또 실패). `DefaultErrorHandler`가 추가로 2초 간격 2회 재시도(총 3번) 후에 오프셋을 강제 커밋하고 에러 로그를 남겨서 무한 루프를 차단해요.

---

## 전체 재시도 흐름 (수정 후)

```
메시지 도착
  ↓
@Retryable: 최대 5회 시도 (50ms → 100ms → 200ms → 400ms)
  ↓ 5회 모두 실패 시
리스너 catch → throw (ack 하지 않음)
  ↓
DefaultErrorHandler: 2초 간격으로 2회 추가 재시도
  ↓ 최종 실패 시
에러 로그 + 오프셋 커밋 (무한 루프 방지)
```