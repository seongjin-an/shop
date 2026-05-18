`MySQL`을 도커 컴포즈로 띄울 때 아래 옵션들은 보통 **Replication** 이나 `Debezium` 같은 CDC(Change Data Capture) 도구를 위해 설정하는 경우가 많아.

```yaml
command:
  --log-bin=mysql-bin
  --binlog-format=ROW
  --binlog-row-image=FULL
  --server-id=1
```

하나씩 보면:

---

## 1. `--log-bin=mysql-bin`

### 의미

바이너리 로그(Binary Log, binlog)를 활성화함.

MySQL 은 데이터 변경 작업을 binlog 파일에 기록할 수 있는데:

* INSERT
* UPDATE
* DELETE
* DDL(CREATE TABLE 등)

같은 작업들이 저장됨.

`mysql-bin` 은 로그 파일 prefix 이름이야.

실제로는 이런 파일들이 생김:

```text
mysql-bin.000001
mysql-bin.000002
```

### 왜 필요한가?

CDC 나 replication 은 이 binlog 를 읽어서 동작함.

예를 들어 Debezium 은:

```text
MySQL binlog 읽음
→ 변경 이벤트 추출
→ Kafka 로 전송
```

이런 방식이라서 binlog 가 반드시 필요함.

---

## 2. `--binlog-format=ROW`

### 의미

binlog 에 변경 내용을 어떤 방식으로 저장할지 지정.

대표적으로 3가지가 있음:

| 형식        | 설명            |
| --------- | ------------- |
| STATEMENT | 실행된 SQL 자체 저장 |
| ROW       | 실제 변경된 row 저장 |
| MIXED     | 상황 따라 혼합      |

---

### ROW 예시

예를 들어:

```sql
UPDATE member
SET age = 20
WHERE id = 1;
```

#### STATEMENT 방식

그 SQL 문 자체를 저장:

```sql
UPDATE member SET age=20 WHERE id=1;
```

#### ROW 방식

실제 row 변경값 저장:

```text
id=1, age: 19 -> 20
```

---

### 왜 ROW 를 쓰나?

CDC 도구들은 대부분 ROW 기반을 요구함.

왜냐면:

* 실제 데이터 변경을 정확히 알 수 있음
* 비결정적 함수 문제 없음
* replication 안정성 높음

Debezium 도 거의 필수로 `ROW` 를 요구해.

---

## 3. `--binlog-row-image=FULL`

### 의미

ROW 포맷일 때, row 의 어느 정도 정보를 binlog 에 남길지 결정.

옵션은:

| 값       | 의미           |
| ------- | ------------ |
| FULL    | 모든 컬럼 저장     |
| MINIMAL | 변경된 컬럼만 저장   |
| NOBLOB  | blob/text 제외 |

---

### FULL 예시

테이블:

```sql
member(id, name, age)
```

UPDATE:

```sql
UPDATE member
SET age = 20
WHERE id = 1;
```

### FULL

binlog 에:

```text
before:
id=1, name=kim, age=19

after:
id=1, name=kim, age=20
```

전체 row 저장.

---

### MINIMAL

변경된 값 위주:

```text
age: 19 -> 20
```

---

### 왜 FULL 을 쓰나?

Debezium 같은 CDC 는 전체 row 정보가 필요할 때가 많음.

특히:

* before image
* after image
* delete 이벤트 복원
* Kafka consumer 단 처리

등에서 안정적임.

그래서 보통:

```text
ROW + FULL
```

조합을 많이 사용함.

---

## 4. `--server-id=1`

### 의미

MySQL replication 서버 식별자.

Replication topology 에서 각 MySQL 인스턴스는 고유한 server-id 가 필요함.

예:

| 서버       | server-id |
| -------- | --------- |
| master   | 1         |
| replica1 | 2         |
| replica2 | 3         |

---

### 왜 단일 컨테이너에서도 필요한가?

Debezium 이 MySQL replication protocol 을 사용해서 binlog 를 읽기 때문.

즉 Debezium 입장에서는:

```text
"나 replication slave 처럼 붙어서 binlog 읽을게"
```

이런 방식이라 server-id 가 필요함.

---

# 정리

이 설정은 거의:

* Debezium
* Maxwell
* Canal
* MySQL Replication
* CDC 시스템

용 기본 세팅이라고 보면 됨.

```yaml
command:
  --log-bin=mysql-bin          # binlog 활성화
  --binlog-format=ROW          # row 기반 변경 기록
  --binlog-row-image=FULL      # 전체 row 저장
  --server-id=1                # replication 식별자
```

특히 Debezium 사용 시 사실상 표준 조합에 가까워.
