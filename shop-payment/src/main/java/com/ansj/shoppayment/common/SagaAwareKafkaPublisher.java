package com.ansj.shoppayment.common;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Scope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka 발행 공통 유틸.
 *
 * <p>핵심 역할: Kafka partition key 가 어떤 값이든 (sagaId, orderId 등)
 * OTel Collector 의 {@code attributes/baggage} processor 가 {@code baggage.saga.id}
 * 를 span attribute 로 자동 승격해주므로 Tempo 추적이 끊기지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SagaAwareKafkaPublisher {

    private static final String BAGGAGE_KEY_SAGA_ID  = "saga.id";
    private static final String BAGGAGE_KEY_TRACE_ID = "trace.id";

    private final KafkaTemplate<String, String> kafkaTemplate;

    public void send(String topic, String partitionKey, String sagaId, String traceId, String json) {
        var builder = Baggage.current().toBuilder().put(BAGGAGE_KEY_SAGA_ID, sagaId);
        if (traceId != null) {
            builder.put(BAGGAGE_KEY_TRACE_ID, traceId);
        }
        Baggage baggage = builder.build();

        try (Scope ignored = baggage.makeCurrent()) {
            kafkaTemplate.send(topic, partitionKey, json);
        } catch (Exception e) {
            log.error("Kafka 발행 실패. topic={}, partitionKey={}, sagaId={}, traceId={}, cause={}",
                    topic, partitionKey, sagaId, traceId, e.getMessage(), e);
            throw e;
        }
    }
}
