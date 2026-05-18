# EKS 배포 준비: 환경변수화 + Dockerfile + K8s 매니페스트

## 1. 무엇을 했는가

로컬 docker-compose 기반으로만 동작하던 프로젝트를 **EKS(개발 환경)에 배포할 수 있는 상태**로 만들었다.
변경 범위는 세 가지다.

1. `application.yml` — 하드코딩된 localhost 주소를 환경변수로 외부화
2. `Dockerfile` — 각 서비스별 멀티스테이지 빌드 이미지 추가
3. `k8s/` — EKS에 올릴 전체 K8s 매니페스트

---

## 2. application.yml 환경변수화

**변경 전: 하드코딩**
```yaml
datasource:
  hikari:
    jdbc-url: jdbc:mysql://localhost:23306/shop
    username: dev_user
    password: dev_password
redis:
  host: localhost
  port: 16379
kafka:
  bootstrap-servers: localhost:9094
```

**변경 후: 환경변수 + 로컬 기본값 유지**
```yaml
datasource:
  hikari:
    jdbc-url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:23306}/shop
    username: ${DB_USER:dev_user}
    password: ${DB_PASSWORD:dev_password}
redis:
  host: ${REDIS_HOST:localhost}
  port: ${REDIS_PORT:16379}
kafka:
  bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9094}
```

`${VAR:default}` 형태이므로 **기존 docker-compose 환경은 설정 변경 없이 그대로 동작**한다.
K8s Deployment에서 env vars를 주입하면 K8s 서비스 DNS로 덮어쓰인다.

| 환경변수 | 로컬 기본값 | K8s 값 |
|---------|------------|--------|
| `DB_HOST` | `localhost` | `mysql` |
| `DB_PORT` | `23306` | `3306` |
| `REDIS_HOST` | `localhost` | `redis` |
| `REDIS_PORT` | `16379` | `6379` |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9094` | `kafka:9092` |
| `USER_SERVICE_URL` (gateway) | `http://localhost:8080` | `http://shop-user:8080` |
| `GATEWAY_URL` (frontend) | `http://localhost:8090` | `http://shop-gateway:8090` |

추가로 `shop-frontend/next.config.ts`에 `output: "standalone"` 옵션을 추가했다.
Next.js standalone 빌드는 독립 실행 가능한 `server.js`를 생성하므로 컨테이너 이미지를 경량화할 수 있다.

---

## 3. Dockerfile

서비스별 멀티스테이지 Dockerfile을 추가했다. **OTel Java agent는 포함하지 않는다.**

```dockerfile
# Java 서비스 공통 패턴
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /app
COPY gradlew gradle/ build.gradle settings.gradle ./
RUN chmod +x gradlew
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### OTel Agent를 Dockerfile에 넣지 않는 이유

```
Dockerfile에 agent 포함             OTel Operator 방식 (이번 선택)
─────────────────────────────       ──────────────────────────────────────
각 서비스 Dockerfile 수정 필요       Pod annotation 하나로 전체 적용
agent 버전 업그레이드 = 이미지 재빌드  Instrumentation CR 버전만 변경
                                    플랫폼팀이 앱팀과 독립적으로 관리
```

K8s 환경에서 OTel Operator는 `instrumentation.opentelemetry.io/inject-java: "true"` annotation이 붙은 Pod에 init container를 통해 agent jar를 자동 주입한다.

---

## 4. k8s/ 디렉토리 구조

```
k8s/
├── 00-namespace.yaml              Namespace: shop
├── 01-secrets.yaml                DB 자격증명(Secret) + Debezium 커넥터 설정(ConfigMap)
├── infra/
│   ├── mysql.yaml                 StatefulSet + PVC + binlog ConfigMap + Debezium 계정 init
│   ├── redis.yaml                 Deployment
│   └── kafka.yaml                 StatefulSet KRaft 단일 브로커 + Kafka UI
├── kafka-connect/
│   ├── deployment.yaml            Debezium Connect (quay.io/debezium/connect:3.1)
│   └── init-job.yaml              토픽 생성 Job + 커넥터 등록 Job (순차 실행)
├── observability/
│   ├── install.sh                 cert-manager + OTel Operator 설치 스크립트
│   ├── instrumentation.yaml       Instrumentation CR — Java agent 2.6.0 inject 설정
│   ├── otel-collector.yaml        기존 docker의 otel-collector-config.yml을 K8s용으로 포팅
│   ├── prometheus.yaml            remote_write receiver 모드 유지
│   ├── loki.yaml                  기존 loki-config.yml 그대로 이식
│   ├── tempo.yaml                 기존 tempo-config.yml 그대로 이식
│   └── grafana.yaml               datasource provisioning ConfigMap 포함
├── apps/
│   ├── shop-{user,product,order,stock,payment,gateway,frontend}.yaml
│   └── ingress.yaml               AWS ALB Ingress Controller
├── eksctl-cluster.yaml            EKS 클러스터 정의 (t3.xlarge × 3)
├── ecr-push.sh                    서비스별 ECR 빌드 & 푸시
└── deploy.sh                      전체 배포 순서 자동화
```

### 인프라 주요 설계 결정

**MySQL**
- Debezium CDC를 위해 binlog 활성화: `log-bin=mysql-bin`, `binlog-format=ROW`
- init SQL로 Debezium 전용 계정 생성: `REPLICATION SLAVE, REPLICATION CLIENT` 권한

**Kafka (KRaft)**
- Zookeeper 없는 단일 브로커 (개발 환경)
- advertised.listeners를 ClusterIP Service DNS로 설정:
  `PLAINTEXT://kafka.shop.svc.cluster.local:9092`
- 앱 서비스: `KAFKA_BOOTSTRAP_SERVERS=kafka:9092`

**OTel Collector**
- docker의 `otel-collector-config.yml`에서 `host.docker.internal:8080` → `shop-user:8080` 등 K8s 서비스 이름으로 교체
- 나머지 설정(transform/saga_id, attributes/baggage, pipeline 구성)은 변경 없음

---

## 5. 배포 순서

```bash
# 1. 클러스터 생성 (~15분)
eksctl create cluster -f k8s/eksctl-cluster.yaml

# 2. ALB Ingress Controller
helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system --set clusterName=shop-dev-cluster

# 3. OTel Operator (cert-manager 포함)
bash k8s/observability/install.sh

# 4. ECR 빌드 & 푸시
AWS_ACCOUNT_ID=<id> bash k8s/ecr-push.sh latest

# 5. 전체 배포 (인프라 → Kafka → 관측 → 앱 순서)
ECR_REGISTRY=<account>.dkr.ecr.ap-northeast-2.amazonaws.com \
IMAGE_TAG=latest bash k8s/deploy.sh
```

---

## 6. 핵심 요약

> 이번 작업의 핵심은 **"K8s를 위해 앱 코드를 바꾼 것이 아니라, 앱 코드가 환경을 몰라도 되도록 만든 것"**이다.
>
> - `${DB_HOST:localhost}` 패턴으로 로컬/K8s 환경을 같은 코드로 처리
> - OTel agent를 Dockerfile 밖으로 빼서 플랫폼 관심사와 앱 관심사를 분리
> - K8s 매니페스트는 docker-compose의 서비스 구성을 그대로 이식 — 새로운 아키텍처가 아니라 동일한 아키텍처의 다른 실행 환경
