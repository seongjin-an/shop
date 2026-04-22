맞는 방향이긴 한데, 용어를 좀 정리하면 "Redis 로 처리"가 의미하는 게 사실 **세 가지나 갈립니다.** 그리고 "eventual consistency(최종 일관성)"가 뭘 뜻하는지도 지금 시나리오에 꽂아서 풀어야 합니다.

---

## 먼저 한 번 더 정리 — 낙관락이 "문제" 인가?

**아니요, 낙관락은 증상입니다.** 원인은 **"서로 다른 토픽에서 같은 productId row 를 동시 write"** 하는 것. 낙관락은 그 경합을 드러내서 "너네 지금 레이스 중이야" 라고 알려주는 감지 장치입니다. 낙관락을 뺀다고 경합이 사라지는 게 아니라, **경합이 조용히 덮어쓰기로 바뀌어서 오히려 더 나빠집니다.**

즉 해결은 둘 중 하나:

1. **경합 자체를 없앤다** (single-writer 를 진짜로 달성 — 옵션 A/D)
2. **경합을 빠르고 안전하게 직렬화한다** (비관적 락, **Redis 원자 연산**, 분산 락 등)

Redis 는 2번에 해당합니다.

---

## Redis 로 푼다 — 세 가지 방식

### 방식 1: Redis 분산 락 (Redlock / Redisson)

가장 먼저 떠오르는 것. "productId 별로 락 걸고 DB 업데이트":

```java
@RequiredArgsConstructor
public class StockService {
    private final RedissonClient redisson;
    private final StockRepository stockRepository;

    public void cancelReservationOne(UUID productId, int qty) {
        RLock lock = redisson.getLock("stock:lock:" + productId);
        try {
            // 최대 3초 대기, 10초 보유
            if (!lock.tryLock(3, 10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("lock timeout");
            }
            StockEntity stock = stockRepository.findByProductId(productId)
                    .orElseThrow();
            stock.cancelReservation(qty);
            // Hibernate flush 시점에 UPDATE — 이때 다른 스레드는 락 대기 중이라 OLE 안 남
        } finally {
            lock.unlock();
        }
    }
}
```

**장점:** Kafka 구조 안 바꿔도 OLE 소멸.  
**단점:**
- 락 획득 네트워크 왕복(2~3ms) 이 매 요청마다 붙음 → 스트레스 테스트 처리량 한계
- 락 만료(TTL) > 실제 작업 시간이어야 함 → 튜닝 필요
- Redis 장애 시 재고 연산 전체 중단
- **사실 DB 비관적 락(`SELECT FOR UPDATE`) 과 효과 비슷한데 인프라만 추가됨** — 이 프로젝트엔 오버엔지니어링에 가깝습니다.

### 방식 2: Redis 를 **실시간 재고 카운터** 로 쓴다 (진짜 패턴)

이게 보통 전자상거래에서 "Redis 로 재고 처리한다" 할 때의 의미입니다. **Redis 의 `DECR` 은 단일 스레드 + 원자 연산이라 락이 필요 없습니다.**

설계:
```
재고의 실시간 소스 = Redis
재고의 영구 기록 = MySQL (감사/복구용)

MySQL ↔ Redis 사이가 "eventual consistency"
```

Lua 스크립트로 "체크 + 차감" 을 원자적으로:

```java
// 예약: 재고가 충분하면 차감 + 예약 증가, 아니면 -1 반환
public long tryReserve(UUID productId, int qty) {
    String script = """
        local avail = tonumber(redis.call('HGET', KEYS[1], 'available'))
        if avail == nil or avail < tonumber(ARGV[1]) then
            return -1
        end
        redis.call('HINCRBY', KEYS[1], 'available', -ARGV[1])
        redis.call('HINCRBY', KEYS[1], 'reserved',   ARGV[1])
        return tonumber(redis.call('HGET', KEYS[1], 'available'))
        """;
    return redisTemplate.execute(
        new DefaultRedisScript<>(script, Long.class),
        List.of("stock:" + productId),
        String.valueOf(qty)
    );
}

// 확정: reserved 차감
public void confirm(UUID productId, int qty) {
    String script = """
        redis.call('HINCRBY', KEYS[1], 'reserved', -ARGV[1])
        """;
    redisTemplate.execute(new DefaultRedisScript<>(script, Long.class),
        List.of("stock:" + productId), String.valueOf(qty));
}

// 해제: available 복구, reserved 차감
public void release(UUID productId, int qty) {
    String script = """
        redis.call('HINCRBY', KEYS[1], 'reserved',  -ARGV[1])
        redis.call('HINCRBY', KEYS[1], 'available',  ARGV[1])
        """;
    redisTemplate.execute(new DefaultRedisScript<>(script, Long.class),
        List.of("stock:" + productId), String.valueOf(qty));
}
```

Lua 는 **Redis 서버에서 통째로 원자적으로 실행** 되기 때문에, 아무리 많은 스레드/노드가 동시에 호출해도 순서대로 처리됩니다. **락 자체가 불필요.**

그리고 MySQL 로의 반영은 **비동기** 로:
- Kafka outbox → Consumer 가 Redis 결과 보고 DB 에 반영
- 또는 주기 배치 (예: 5초마다 Redis → MySQL sync)
- 또는 Redis Streams 로 이벤트 큐 운영

**이게 "eventual consistency"의 실제 모습입니다.**

### 방식 3: Kafka 토픽 통합으로 아예 경합 제거 (이전 대화의 옵션 A)

Redis 없이 **stock-command** 단일 토픽으로 합쳐서 진짜 single-writer 달성. 이건 인프라 추가 안 하고 아키텍처만 고침.

---

## "Eventually sync" — 최종 일관성이란?

정확한 용어는 **eventual consistency** 입니다. 뜻:

> **"지금 이 순간은 두 저장소의 숫자가 다를 수 있다. 하지만 유한한 시간이 지나면 같아진다."**

스트롱 컨시스턴시(지금 당장 동기화됨) 의 반대 개념입니다.

**예시로 보는 시간축:**

```
시각  Redis.avail   MySQL.quantity   상태
t=0       100             100         동기화됨
t=1        98             100         Redis 에서 2개 예약. MySQL 은 아직.
t=2        98             100         아직 불일치. "괜찮아?" → 괜찮음, 이게 최종 일관성.
t=3        98              98         sync job 이 MySQL 반영. 동기화됨.
```

**t=1 ~ t=2 구간에서 불일치가 있어도 비즈니스 관점에선 OK** 하다는 게 이 패턴의 전제입니다.

### 왜 쇼핑몰에서 이게 통하나?

1. **재고 수는 사용자가 실시간 DB 조회할 필요 없음** — Redis 만 정확하면 충분 (예약/차감은 Redis 기준).
2. **MySQL 은 감사 로그/복구용** — 문제 생겼을 때 "몇 시에 뭐가 팔렸나" 재구성용.
3. **성능 폭발** — Redis 는 수십만 TPS, MySQL 에서 같은 걸 하면 수천 TPS 가 한계.

### 단, 대가가 있습니다

- **Redis 데이터가 유실되면** 직전 수 초의 예약이 사라짐. AOF fsync 전략 타이트하게 잡아야 함 (`appendfsync everysec` 정도는 기본).
- **Redis 와 MySQL 이 서로 드리프트** 하면 정기적으로 reconcile(대사) 해야 함.
- **트랜잭션 경계가 깨짐** — "예약 + 주문 생성" 을 하나의 트랜잭션으로 못 묶음. 분산 트랜잭션 아니라 **"Saga + 보상"** 으로 풀어야 함.
    - 근데 이 프로젝트는 **이미 Saga 쓰고 있음** → 이 대가는 이미 지불된 상태. Redis 도입 비용이 상대적으로 적습니다.

---

## 이 프로젝트에 구체적으로 적용한다면

### 현재 흐름
```
stock-reservation-requested → Thread-A → MySQL UPDATE stock (OLE 위험) → stock-reserved
```

### Redis 카운터 도입 후
```
stock-reservation-requested → Thread-A:
  1. Redis Lua 로 tryReserve(productId, qty)
       → -1 이면 실패, stock-reserve-failed 발행
       → 양수면 성공
  2. outbox 에 이벤트 + delta(-qty) 저장 (MySQL)
  3. stock-reserved 발행
  
별도 Outbox Publisher:
  outbox → MySQL stock 테이블 UPDATE (배치)
```

**핵심 포인트:**
- 핫 경로(hot path) 에는 MySQL 이 안 끼니까 OLE 자체가 사라짐
- MySQL 은 느긋하게 따라옴
- 장애 시 복구는 Redis AOF + MySQL 최종 스냅샷으로 재생

---

## 그래서 뭘 추천하냐

솔직히 **이 프로젝트의 현재 규모 + 목적(학습·실험용)** 을 고려하면:

**단기(오늘 당장)**: 이전에 말한 **옵션 B(비관적 락 `SELECT FOR UPDATE`)** — 코드 5줄 수정, 인프라 변경 0, OLE 즉시 사라짐.

**중기(아키텍처 학습 의도면)**: **옵션 A (토픽 통합으로 진짜 single-writer)** — Kafka 의 partition key 보장 범위를 정확히 이해하고 구조로 푸는 공부가 됨. 추가 인프라 없음.

**장기 또는 고부하 시뮬레이션 목적이면**: **Redis 카운터 + eventual consistency** — 실제 e커머스에서 쓰는 패턴이라 학습 가치 높음. 단 AOF/sync job/대사 로직까지 만들어야 완성됨 (학습 비용 큼).

---

## 종합 비교표

| 접근 | OLE 해소 | 처리량 | 구현 비용 | 인프라 추가 | 학습 가치 |
|------|----------|--------|-----------|-------------|-----------|
| @Retryable 강화 | △ 임시방편 | ↓ | 매우 낮음 | 없음 | 낮음 |
| DB 비관적 락 | ✅ | 중간 | 낮음 | 없음 | 중간 |
| Redis 분산 락 | ✅ | 중간 | 중간 | Redis 이미 있음 | 중간 |
| 토픽 통합(single-writer) | ✅ | 높음 | 중간 | 없음 | **높음** (Kafka 개념) |
| **Redis 카운터 + eventual** | ✅ | **매우 높음** | **높음** | Redis + 배치 | **매우 높음** (분산 시스템) |

---

**한 줄 요약**: 낙관락이 증상이고, Redis 카운터 + eventual consistency 는 진짜 실무 패턴이 맞습니다. 다만 "Redis 락을 거는 것" 과 "Redis 를 실시간 카운터로 쓰는 것" 은 완전히 다른 얘기고, 쇼핑몰에서 효과 크다는 건 **후자** 입니다. 지금 시점에선 먼저 비관적 락이나 토픽 통합으로 구조 문제를 정리한 다음, 학습 차원에서 Redis 카운터로 업그레이드하는 순서를 추천합니다.