package com.ansj.shoporder.common;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Scope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka 발행 공통 유틸.
 *
 * <p>핵심 역할: Kafka partition key 를 sagaId 이외의 값(예: productId)으로 바꾸더라도
 * OpenTelemetry Collector 의 {@code attributes/baggage} processor 가
 * {@code baggage.saga.id} 를 span attribute 로 자동 승격해주므로
 * Tempo 에서 {@code { saga.id = "..." }} 쿼리가 계속 동작한다.
 *
 * <p>기존 코드는 {@code kafkaTemplate.send(topic, sagaId, json)} 으로 보냈기 때문에
 * OTel Collector 가 {@code messaging.kafka.message.key} 를 {@code saga.id} 로 승격해 왔으나,
 * productId 기반 파티셔닝으로 전환한 후에는 이 경로가 깨지므로 baggage 로 대체한다.
 *
 * <p>W3C Baggage 는 {@code OTEL_PROPAGATORS=tracecontext,baggage} 설정에 의해
 * Kafka record header({@code baggage} 헤더)에 자동 인코딩되어 consumer 로 전파된다.
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
