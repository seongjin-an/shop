package com.ansj.shoporder.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Saga 비즈니스 메트릭 집계기.
 *
 * <p>Prometheus 지표:
 * <ul>
 *   <li>{@code saga_started_total} — 주문 생성 건수 (Counter)</li>
 *   <li>{@code saga_terminated_total{status}} — 터미널 상태 도달 건수 (Counter). status ∈ {COMPLETED, STOCK_FAILED, CANCELLED}</li>
 *   <li>{@code saga_duration_seconds{status}} — 주문 생성 → 터미널 상태 wall-clock 소요 시간 (Timer). histogram 버킷 포함</li>
 *   <li>{@code saga_state_transition_total{from, to}} — 세부 상태 전이 건수 (Counter)</li>
 * </ul>
 *
 * <p>Grafana 에서 활용 예:
 * <pre>
 * 성공률:   sum(rate(saga_terminated_total{status="COMPLETED"}[5m]))
 *         / sum(rate(saga_terminated_total[5m]))
 *
 * p95 완료시간: histogram_quantile(0.95, sum(rate(saga_duration_seconds_bucket{status="COMPLETED"}[5m])) by (le))
 *
 * 실패 비율: sum(rate(saga_terminated_total{status=~"STOCK_FAILED|CANCELLED"}[5m]))
 *         / sum(rate(saga_terminated_total[5m]))
 * </pre>
 */
@Component
public class SagaMetrics {

    private static final String STARTED = "saga_started_total";
    private static final String TERMINATED = "saga_terminated_total";
    private static final String DURATION = "saga_duration_seconds";
    private static final String TRANSITION = "saga_state_transition_total";

    private final MeterRegistry registry;
    private final Counter startedCounter;

    public SagaMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.startedCounter = Counter.builder(STARTED)
                .description("Saga 시작 건수 (주문 생성)")
                .register(registry);
    }

    /** 주문 생성 직후 호출. */
    public void recordStarted() {
        startedCounter.increment();
    }

    /**
     * 상태 전이 기록. 모든 상태 변경 시점에 호출.
     *
     * @param from 이전 상태 (예: "PENDING")
     * @param to   다음 상태 (예: "STOCK_RESERVED")
     */
    public void recordTransition(String from, String to) {
        Counter.builder(TRANSITION)
                .description("Saga 상태 전이 건수")
                .tag("from", from)
                .tag("to", to)
                .register(registry)
                .increment();
    }

    /**
     * 터미널 상태 도달 기록 + duration 측정.
     *
     * @param status    터미널 상태 (COMPLETED / STOCK_FAILED / CANCELLED)
     * @param createdAt 주문이 최초 생성된 시각 (OrderEntity.createdAt)
     */
    public void recordTerminated(String status, LocalDateTime createdAt) {
        Counter.builder(TERMINATED)
                .description("Saga 터미널 상태 도달 건수")
                .tag("status", status)
                .register(registry)
                .increment();

        if (createdAt != null) {
            Duration elapsed = Duration.between(
                    createdAt.atZone(ZoneId.systemDefault()).toInstant(),
                    java.time.Instant.now()
            );
            Timer.builder(DURATION)
                    .description("Saga 생성 → 터미널 상태 도달 wall-clock 소요 시간")
                    .tag("status", status)
                    .publishPercentileHistogram(true)
                    .register(registry)
                    .record(elapsed);
        }
    }
}
