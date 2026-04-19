## 맞아요, 대부분 자동으로 들어가니까 **custom 라벨링은 거의 불필요**해요

OTel Java Agent + Logback appender 브리지 + Loki OTLP native 조합이면 기본으로 이만큼 자동 주입됩니다. 카테고리별로 정리하면:

### 1) Loki 라벨로 승격되는 것 (인덱스됨, 저카디널리티)

Loki 3.0+ OTLP endpoint 가 **resource attribute 중 일부만** 라벨로 자동 승격:

| OTLP 필드 | Loki 라벨 | 출처 |
|---|---|---|
| `service.name` | `service_name` | `OTEL_SERVICE_NAME` env |
| `service.namespace` | `service_namespace` | `OTEL_RESOURCE_ATTRIBUTES` |
| `service.instance.id` | `service_instance_id` | Agent 가 UUID 로 자동 생성 |
| `deployment.environment` | `deployment_environment` | `OTEL_RESOURCE_ATTRIBUTES` |
| `severity_text` (소문자) | `level` | LogRecord 레벨 |

예시:

```
Labels:
  service_name: shop-order
  service_namespace: shop
  service_instance_id: 4a7b9e12-8c3d-4f1a-9e8b-2d5c7f1a3b4d
  deployment_environment: local
  level: info
```

**단 5가지만 라벨** 로 가고, 나머지는 structured metadata. 카디널리티 폭발 방지 설계.

### 2) LogRecord 최상위 필드 → structured metadata 자동

OTLP 프로토콜 정의상 모든 로그에 들어가는 것들:

```
Structured Metadata:
  trace_id:          cb0e484f76f27e5b19cba2bac4fc8b34
  span_id:           faa7ca856a7e237b
  trace_flags:       01                       # sampled 여부
  observed_timestamp: 2026-04-19T17:22:08.395Z  # agent 가 관찰한 시각
  severity_number:   9                        # OTel 정수 레벨 (INFO=9, ERROR=17)
```

### 3) OTel Java Agent 가 **기본으로** 붙이는 log record attributes

아무 플래그 안 줘도 Agent 가 logback event 에서 뽑아 넣는 것들:

| 필드 | 예시 값 | 의미 |
|---|---|---|
| `thread.id` | `38` | 스레드 ID |
| `thread.name` | `http-nio-8082-exec-1` | 스레드 이름 |
| `logger.name` | `c.a.s.u.CompensateStockUseCase` | 로거 FQCN |

### 4) 예외 발생 시 **자동 주입** (log.error 에 throwable 있을 때)

```java
log.error("payment failed", exception);
```

→

| 필드 | 예시 값 |
|---|---|
| `exception.type` | `java.lang.IllegalStateException` |
| `exception.message` | `Row was updated or deleted by another transaction` |
| `exception.stacktrace` | 전체 스택트레이스 |
| `exception.escaped` | `false` |

이게 특히 유용. 지금까지 Promtail 때는 stacktrace 가 여러 줄로 끊겨서 로그 분석이 지저분했는데, OTLP 경로에서는 **한 log record 안에 stacktrace 가 통째로** 들어가요. Loki UI 에서 한 줄 클릭하면 exception.stacktrace 필드가 접힌 채로 들어있고 펼쳐서 볼 수 있음.

### 5) 플래그로 켠 옵션들 (당신이 이미 켠 상태)

**`CAPTURE_CODE_ATTRIBUTES=true` 효과:**

| 필드 | 예시 값 |
|---|---|
| `code.filepath` | `CompensateStockUseCase.java` |
| `code.function` | `compensate` |
| `code.lineno` | `48` |
| `code.namespace` | `com.ansj.shopstock.usecase.CompensateStockUseCase` |

**`CAPTURE_MDC_ATTRIBUTES=*` 효과:**

당신이 코드에서 `MDC.put("sagaId", ...)`, `MDC.put("userId", ...)` 해둔 모든 것이 `mdc.<key>` 로 자동 승격:

| 필드 | 예시 값 |
|---|---|
| `mdc.sagaId` | `019da4d2-dfaa-7772-8f52-bc29a443d7bc` |
| `mdc.userId` | `user-123` (만약 넣었다면) |

**`CAPTURE_MARKER_ATTRIBUTE=true` 효과:**

```java
Marker audit = MarkerFactory.getMarker("AUDIT");
log.info(audit, "order created");
```

→ `logback.marker: "AUDIT"`

**`CAPTURE_KEY_VALUE_PAIR_ATTRIBUTES=true` 효과:**

SLF4J 2.x fluent API 사용 시:

```java
log.atInfo()
   .addKeyValue("orderId", 12345)
   .addKeyValue("amount", 9900)
   .log("order created");
```

→ 각 kv 가 log record attribute 로:

| 필드 | 값 |
|---|---|
| `orderId` | `12345` |
| `amount` | `9900` |

### 6) 상황 따라 자동 붙는 것들

OTel Agent 의 다른 인스트루멘테이션이 활성화된 스레드에서 로그를 찍으면 context 에 따라 추가로 붙음:

**HTTP 요청 처리 중 로그:**
```
(없음 — HTTP 속성은 HTTP span 에만 붙고 log record 엔 기본적으로 안 붙음)
```

**Kafka consumer 처리 중 로그:**
```
(마찬가지 — Kafka 속성은 consumer span 에 붙고 log record 엔 안 붙음)
```

즉 HTTP/Kafka 속성은 log record 에 **자동으론 안 옴**. 꼭 필요하면 MDC 를 써야 함:

```java
@Component
public class KafkaMdcInterceptor implements ConsumerInterceptor<...> {
  public ConsumerRecords<..> onConsume(ConsumerRecords<..> records) {
    records.forEach(r -> MDC.put("kafka.topic", r.topic()));
    return records;
  }
}
```

이러면 `mdc.kafka_topic` 으로 조회 가능.

### 정리 — 지금 당신 프로젝트에서 자동 확보된 필드 전체 목록

```
[Labels - 인덱스]
  service_name, service_namespace, service_instance_id,
  deployment_environment, level

[Structured Metadata - 비인덱스]
  trace_id, span_id, trace_flags, observed_timestamp, severity_number

[Log Record Attributes - 비인덱스, 필터 가능]
  thread.id, thread.name, logger.name
  code.filepath, code.function, code.lineno, code.namespace
  exception.type, exception.message, exception.stacktrace, exception.escaped  (에러 시)
  mdc.sagaId  (MDC.put 한 모든 것)
```

### 실무 실전 체크리스트 — 추가 커스텀 필요한 것만

커스텀 MDC 로 보강할 가치가 있는 것들 (상황별):

| MDC 키 | 언제 넣을까 | 쿼리 예시 |
|---|---|---|
| `sagaId` | 이미 넣었음 ✓ | `\| mdc_sagaId="..."` |
| `orderId` | 주문 ID 로 역추적 필요 시 | `\| mdc_orderId="12345"` |
| `userId` | 유저별 행동 분석 필요 시 | `\| mdc_userId="user-123"` |
| `tenantId` | 멀티테넌트 SaaS | `\| mdc_tenantId="t-1"` |
| `requestId` | API Gateway 가 찍은 별도 trace 필요 | `\| mdc_requestId="..."` |

당신 프로젝트는 **sagaId 만 MDC 에 있으면 충분**. 나머지는 오버엔지니어링.

### 확인 방법 (현재 실제로 들어가고 있는 필드 보기)

Grafana Explore → 임의의 로그 한 줄 클릭:

```
▼ Fields
  code_filepath: ...          ← 있으면 CAPTURE_CODE_ATTRIBUTES OK
  code_function: ...
  mdc_sagaId: ...             ← 있으면 CAPTURE_MDC_ATTRIBUTES OK
  thread_name: ...
  logger_name: ...

▼ Structured Metadata
  trace_id: ...               ← 있으면 파이프라인 OK
  span_id: ...

▼ Labels
  service_name: shop-order
  level: info
```

이 중 **없는 게 있으면** 어떤 플래그가 안 먹었는지 역산 가능. 확인해보고 누락된 게 있으면 알려주세요.