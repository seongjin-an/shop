이 스크립트는 `Debezium MySQL Connector` 를 `Kafka Connect` 에 REST API 로 등록하는 자동화 스크립트야.

즉 흐름은:

```text
docker compose up
→ Kafka / Connect / MySQL 기동
→ 이 스크립트 실행
→ Debezium Connector 생성
→ MySQL binlog CDC 시작
```

이런 구조.

아래부터 순서대로 설명할게.

---

# 전체 목적

이 커넥터는:

```text
MySQL outbox 테이블 감시
→ 변경 감지
→ Kafka 토픽으로 이벤트 발행
```

하는 역할.

특히 Outbox Pattern 용 설정이 들어가 있음.

---

# 1. shebang

```bash
#!/usr/bin/env bash
```

현재 환경의 bash 로 실행.

---

# 2. 주석

```bash
# Debezium MySQL Outbox 커넥터 등록 스크립트
# docker compose up -d 후 1회 실행
```

컨테이너 다 띄운 뒤 한번 실행하라는 의미.

---

# 3. 에러 발생 시 즉시 종료

```bash
set -e
```

명령 하나라도 실패하면 스크립트 중단.

예:

```bash
curl 실패
```

→ 이후 진행 안 함.

운영에서 매우 흔한 패턴.

---

# 4. 변수 선언

```bash
CONNECT_URL="http://localhost:28083"
CONNECTOR_NAME="shop-outbox-connector"
```

### CONNECT_URL

Kafka Connect REST API 주소.

보통 docker-compose 에서:

```yaml
ports:
  - "28083:8083"
```

이렇게 매핑해둔 경우 많음.

---

### CONNECTOR_NAME

등록할 커넥터 이름.

Kafka Connect 내부에서 식별자로 사용됨.

---

# 5. Kafka Connect 준비 대기

```bash
echo "Kafka Connect 준비 대기 중..."
until curl -sf "${CONNECT_URL}/connectors" > /dev/null; do
  sleep 2
done
```

## 의미

Kafka Connect 서버가 완전히 뜰 때까지 polling.

---

### 왜 필요?

docker compose up 직후에는:

```text
컨테이너는 올라왔지만
Kafka Connect 내부 초기화는 아직 안 끝남
```

상태가 자주 있음.

그 상태에서 connector 등록하면 실패.

그래서:

```bash
/connectors
```

API 가 성공 응답할 때까지 반복.

---

### curl 옵션

```bash
-s  silent
-f  HTTP 에러 시 실패 처리
```

---

# 6. 기존 커넥터 삭제

```bash
if curl -sf "${CONNECT_URL}/connectors/${CONNECTOR_NAME}" > /dev/null 2>&1; then
```

기존 connector 존재 여부 확인.

---

있으면:

```bash
curl -X DELETE ...
```

삭제 후 재등록.

---

### 왜 이렇게 하나?

Kafka Connect 는:

```text
동일 이름 connector 중복 생성 불가
```

라서 보통:

```text
삭제 → 재생성
```

패턴 사용.

---

# 7. Connector 생성

핵심 부분.

```bash
curl -X POST "${CONNECT_URL}/connectors"
```

Kafka Connect REST API 로 connector 생성 요청.

---

# JSON 내부 설명

---

## 7-1. connector.class

```json
"connector.class": "io.debezium.connector.mysql.MySqlConnector"
```

Debezium MySQL Connector 사용.

즉:

```text
MySQL binlog 읽는 connector
```

라는 의미.

---

# 7-2. MySQL 접속 정보

```json
"database.hostname": "mysql",
"database.port": "3306",
"database.user": "root",
"database.password": "1234",
```

Debezium 이 MySQL 에 접속하기 위한 정보.

여기서 `"mysql"` 은 docker-compose service name 일 가능성이 큼.

---

# 7-3. database.server.id

```json
"database.server.id": "223344"
```

매우 중요.

Debezium 은 MySQL replication slave 처럼 동작함.

그래서 replication client 식별자 필요.

MySQL replication topology 내에서 unique 해야 함.

---

# 7-4. topic.prefix

```json
"topic.prefix": "dbz"
```

Debezium 기본 topic prefix.

원래 Debezium 기본 topic 은:

```text
dbz.database.table
```

형태로 생성됨.

예:

```text
dbz.shop.order_outbox_event
```

---

하지만 여기선 Outbox SMT(EventRouter)를 쓰므로 실제 최종 토픽은 바뀜.

---

# 7-5. database.include.list

```json
"database.include.list": "shop"
```

shop DB 만 CDC 대상으로 감시.

---

# 7-6. table.include.list

```json
"table.include.list":
"shop.order_outbox_event,
 shop.stock_outbox_event,
 ..."
```

감시할 테이블 제한.

즉:

```text
Outbox 테이블만 CDC 수행
```

하는 구조.

실무에서 아주 흔함.

---

# 7-7. snapshot.mode

```json
"snapshot.mode": "schema_only"
```

중요한 옵션.

---

## Debezium 기본 동작

처음 연결되면:

```text
현재 테이블 전체 읽기(snapshot)
→ 이후 binlog 추적
```

함.

---

## schema_only 의미

```text
데이터 snapshot 안 함
스키마만 읽음
이후부터 발생하는 변경만 추적
```

즉:

```text
기존 데이터 무시
새 이벤트만 Kafka 발행
```

Outbox 에 매우 잘 맞음.

---

# 7-8. schema history

```json
"schema.history.internal.kafka.bootstrap.servers": "kafka:9092",
"schema.history.internal.kafka.topic": "schema-changes.shop"
```

Debezium 은 테이블 schema 변경 이력도 Kafka 에 저장함.

예:

```text
ALTER TABLE ...
```

같은 것 추적.

---

# 8. transforms = outbox

여기가 핵심.

```json
"transforms": "outbox"
```

SMT(Single Message Transform) 활성화.

---

# 8-1. EventRouter 사용

```json
"transforms.outbox.type":
"io.debezium.transforms.outbox.EventRouter"
```

Debezium 의 Outbox Pattern 전용 SMT.

---

원래 Debezium 이벤트:

```json
{
  "before": ...,
  "after": ...,
  "source": ...
}
```

같이 매우 복잡함.

---

EventRouter 는 이를:

```json
payload 그대로 Kafka 이벤트로 변환
```

해줌.

즉:

```text
Outbox row
→ Kafka 메시지
```

로 routing.

---

# 8-2. route.by.field

```json
"transforms.outbox.route.by.field": "destination_topic"
```

Outbox 테이블 컬럼값으로 Kafka topic 결정.

예:

| destination_topic |
| ----------------- |
| order.created     |
| payment.completed |

---

그러면 Kafka 에:

```text
order.created
payment.completed
```

토픽으로 자동 발행.

엄청 편리함.

---

# 8-3. topic replacement

```json
"transforms.outbox.route.topic.replacement":
"${routedByValue}"
```

최종 토픽명을:

```text
destination_topic 컬럼값 그대로 사용
```

하겠다는 의미.

---

# 8-4. event.key

```json
"transforms.outbox.table.field.event.key":
"partition_key"
```

Kafka 메시지 key 로 사용할 컬럼.

매우 중요.

---

왜 중요?

Kafka partition ordering 때문.

예:

```text
same orderId
→ same partition
→ 순서 보장
```

가능.

---

# 8-5. event.id

```json
"transforms.outbox.table.field.event.id":
"event_id"
```

이벤트 고유 ID.

중복 처리나 idempotency 에 활용 가능.

---

# 8-6. payload

```json
"transforms.outbox.table.field.event.payload":
"payload"
```

Kafka 메시지 value 로 사용할 컬럼.

보통 JSON 저장함.

예:

```json
{
  "orderId": 1,
  "status": "CREATED"
}
```

---

# 8-7. invalid behavior

```json
"transforms.outbox.table.op.invalid.behavior":
"warn"
```

예상 못한 operation 발생 시 warning 만 출력.

---

# 9. 상태 확인

```bash
curl -s "${CONNECT_URL}/connectors/${CONNECTOR_NAME}/status"
```

커넥터 상태 조회.

정상이라면:

```json
{
  "state": "RUNNING"
}
```

비슷하게 나옴.

---

# 이 구조의 핵심 아키텍처

전체 흐름은:

```text
Application
  ↓
Outbox Table INSERT
  ↓
MySQL binlog
  ↓
Debezium CDC
  ↓
EventRouter SMT
  ↓
Kafka Topic
```

---

# 실무적으로 이 방식 장점

## 1. 트랜잭션 일관성

```text
비즈니스 데이터 + 이벤트
동일 DB 트랜잭션 처리 가능
```

---

## 2. Kafka 장애여도 DB 트랜잭션 가능

앱이 Kafka 직접 publish 안 함.

---

## 3. Exactly-once 에 가까운 안정성

Outbox 패턴 핵심 장점.

---

이 스크립트는 꽤 실무 스타일로 잘 구성된 편이야. 특히:

* include.list 제한
* schema_only
* EventRouter
* partition_key 사용

이런 부분이 잘 들어가 있음.
