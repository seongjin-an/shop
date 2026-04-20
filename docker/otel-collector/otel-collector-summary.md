네 정확합니다! **세 가지 신호(traces / metrics / logs) 가 전부 Collector 한 곳을 지나가는 구조**를 완성한 거예요. 이게 OpenTelemetry 생태계가 지향하는 "single pane of observability" 의 전형입니다.

## 지금 완성된 Three Pillars Pipeline

```
                    ┌──────────────────────────────────────────────┐
                    │            OTel Collector (Gateway)          │
                    │                                              │
 Spring Boot ──────►│ ┌─ OTLP receiver (gRPC:4317) ──► traces ────►│──► Tempo
  (OTel Agent)      │ │                             └► logs ──────►│──► Loki
                    │ │                                            │
                    │ └─ prometheus receiver (scrape) ──► metrics ►│──► Prometheus
                    │                                              │
 kafka-exporter ───►│    (위 scrape 대상에 포함)                   │
                    └──────────────────────────────────────────────┘
```

## 각 신호가 어떻게 흘러가는지 — 주문 하나로 따라가기

`POST /orders` 한 번 쏘면 세 신호가 각자 이렇게 이동합니다.

**① Trace (OTLP push)**

```
shop-order (OTel Agent 자동 계측)
  └─ span "POST /orders" 생성
     └─ gRPC OTLP push ──► Collector :4317
                             └─ transform/saga_id 적용
                                (messaging.kafka.message.key → saga.id)
                             └─ otlp/tempo exporter ──► Tempo
```

**② Log (OTLP push)**

```
shop-order: logger.info("Order created: {}", orderId)
  └─ Logback
  └─ OTel Logback appender bridge (build.gradle 의 OTEL_INSTRUMENTATION_LOGBACK_APPENDER_ENABLED=true)
     └─ gRPC OTLP push ──► Collector :4317
                             └─ transform/logs 적용 (severity_text 소문자 정규화)
                             └─ otlphttp/loki exporter ──► Loki :3100/otlp
```

**③ Metric (pull via Collector)**

```
shop-order Micrometer: saga_started_total++
  └─ /actuator/prometheus 에 노출만 함

Collector 가 10초마다:
  ──GET /actuator/prometheus──► shop-order:8082
  ◄── "saga_started_total 1524" ──
     └─ prometheusremotewrite exporter
        ──POST /api/v1/write──► Prometheus :9090
```

## 설정상 우아한 점 — 앱 코드 0줄

`shop-order/build.gradle` 에서 Agent 환경변수만 봐도 뭐가 어디로 가는지 **한눈에 읽힘**:

```groovy
environment 'OTEL_TRACES_EXPORTER',  'otlp'   // ← 트레이스: Collector 로 push
environment 'OTEL_METRICS_EXPORTER', 'none'   // ← 메트릭: 앱은 가만히, Collector 가 scrape
environment 'OTEL_LOGS_EXPORTER',    'otlp'   // ← 로그: Collector 로 push
```

이 세 줄이 이 프로젝트 관측성의 **제어판**입니다. 나머지는 전부 Collector 설정에서 결정.

## Collector 에서 얻는 이득 — 세 신호가 한 곳을 지나기 때문에 가능한 것들

이게 Three Pillars 를 Collector 에 몰아넣는 진짜 보상입니다.

**상호 상관관계 (Correlation)**

Collector 의 `resource` processor 가 세 신호 모두에 `service.name=shop-order`, `deployment.environment=local` 같은 **동일한 리소스 라벨**을 박아줍니다. Grafana 에서:

```
Tempo 에서 span 클릭 → trace_id 추출
  → Loki 쿼리 자동: {service_name="shop-order"} |= "<trace_id>"
  → Prometheus 쿼리 자동: histogram_quantile({service="shop-order"}) 로 같은 서비스 latency 비교
```

이 pivot 이 바로 Grafana 의 TracesToLogs / TracesToMetrics 기능인데, 결국 **Collector 가 리소스 라벨을 일관되게 박아주기 때문에** 가능한 일입니다.

**공유 가능한 processor 체인**

`saga.id` 를 예로 들면, 지금은 trace 에만 적용되어 있지만 나중에 log 에도 적용하려면 Collector 설정 한 줄 추가로 끝나요:

```yaml
# logs 파이프라인에도 같은 transform 재사용
logs:
  receivers: [otlp]
  processors: [batch, resource, transform/logs, transform/saga_id]  # ← 추가
  exporters: [otlphttp/loki]
```

**Backend 갈아끼우기가 자유**

| 신호 | 현재 backend | 갈아끼울 때 수정할 곳 |
|---|---|---|
| Traces | Tempo | `exporters.otlp/tempo.endpoint` 한 줄 (→ Jaeger, Datadog, Honeycomb) |
| Logs | Loki | `exporters.otlphttp/loki.endpoint` 한 줄 (→ Elasticsearch, Splunk, CloudWatch) |
| Metrics | Prometheus | `exporters.prometheusremotewrite.endpoint` 한 줄 (→ Mimir, Thanos, AMP, Datadog) |

앱 코드, Grafana 대시보드 전부 그대로. 이게 OpenTelemetry 의 vendor-neutral 약속이 실제로 실현되는 순간입니다.

## 시각적으로 다시

v1 → v2 → v2.1 을 한 장으로 보면:

```
[v1]                     [v2 초기]                 [v2.1 현재] ✅
각 신호 파편화            신호별 독립 파이프          Collector Gateway
                                                   (Three Pillars 통합)

logs → Promtail→Loki     traces → Collector→Tempo   ┌─────────────┐
metrics → Actuator→Prom  logs → Promtail→Loki       │ Collector   │
traces 없음              metrics → Actuator→Prom    │  ├ traces → Tempo
                                                    │  ├ logs   → Loki
                                                    │  └ metrics→ Prom
                                                    └─────────────┘
```

그래서 지금 다음 단계로 뭘 하든 (Debezium / API Gateway / EKS) **관측성 기반이 일관되게 따라옵니다**. Debezium connector 를 띄우면 그것도 Collector 로 OTLP 내보내게 설정하면 되고, API Gateway 도 마찬가지, EKS 에서는 Collector 를 DaemonSet 으로만 바꾸면 끝. 이 파이프라인이 지금 흙이 잘 다져진 논밭 같은 상태예요.

덧붙이자면 실무에서도 이 단계까지 도달한 프로젝트는 꽤 드뭅니다. 보통 trace 는 Datadog, log 는 ELK, metric 은 CloudWatch 처럼 **벤더별로 파편화** 되어 있고 상관관계가 수동인 경우가 많아요. 이력서에 쓰면 "OpenTelemetry Collector 중심의 통합 관측성 파이프라인 설계/운영" 으로 한 줄 쓸 수 있는 레벨입니다.