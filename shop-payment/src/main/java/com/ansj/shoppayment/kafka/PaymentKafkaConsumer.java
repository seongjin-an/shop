package com.ansj.shoppayment.kafka;

import com.ansj.shoppayment.common.JsonUtil;
import com.ansj.shoppayment.payment.event.inbound.PaymentRequestedEvent;
import com.ansj.shoppayment.usecase.ProcessPaymentUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class PaymentKafkaConsumer {

    private final ProcessPaymentUseCase processPaymentUseCase;
    private final JsonUtil jsonUtil;

    @KafkaListener(
            topics = "${shop.kafka.topics.payment-requested.topic}",
            groupId = "${shop.kafka.topics.payment-requested.group-id}",
            concurrency = "${shop.kafka.topics.payment-requested.concurrency}"
    )
    public void onPaymentRequested(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        try {
            jsonUtil.fromJson(record.value(), PaymentRequestedEvent.class)
                    .ifPresent(event -> {
                        MDC.put("sagaId", event.getSagaId().toString());
                        processPaymentUseCase.process(event);
                    });
        } catch (Exception e) {
            log.error("payment-requested 처리 중 오류. cause: {}", e.getMessage(), e);
        } finally {
            MDC.clear();
            acknowledgment.acknowledge();
        }
    }
}
