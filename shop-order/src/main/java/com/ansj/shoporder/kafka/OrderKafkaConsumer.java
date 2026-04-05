package com.ansj.shoporder.kafka;

import com.ansj.shoporder.common.JsonUtil;
import com.ansj.shoporder.order.event.inbound.PaymentFailedEvent;
import com.ansj.shoporder.order.event.inbound.PaymentSuccessEvent;
import com.ansj.shoporder.order.event.inbound.StockReserveFailedEvent;
import com.ansj.shoporder.order.event.inbound.StockReservedEvent;
import com.ansj.shoporder.usecase.PaymentResultUseCase;
import com.ansj.shoporder.usecase.StockReserveResultUseCase;
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
public class OrderKafkaConsumer {

    private final StockReserveResultUseCase stockReserveResultUseCase;
    private final PaymentResultUseCase paymentResultUseCase;
    private final JsonUtil jsonUtil;

    @KafkaListener(
            topics = "${shop.kafka.topics.stock-reserved.topic}",
            groupId = "${shop.kafka.topics.stock-reserved.group-id}",
            concurrency = "${shop.kafka.topics.stock-reserved.concurrency}"
    )
    public void onStockReserved(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        try {
            jsonUtil.fromJson(record.value(), StockReservedEvent.class)
                    .ifPresent(event -> {
                        MDC.put("sagaId", event.getSagaId().toString());
                        stockReserveResultUseCase.onStockReserved(event);
                    });
        } catch (Exception e) {
            log.error("stock-reserved 처리 중 오류. cause: {}", e.getMessage(), e);
        } finally {
            MDC.clear();
            acknowledgment.acknowledge();
        }
    }

    @KafkaListener(
            topics = "${shop.kafka.topics.stock-reserve-failed.topic}",
            groupId = "${shop.kafka.topics.stock-reserve-failed.group-id}",
            concurrency = "${shop.kafka.topics.stock-reserve-failed.concurrency}"
    )
    public void onStockReserveFailed(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        try {
            jsonUtil.fromJson(record.value(), StockReserveFailedEvent.class)
                    .ifPresent(event -> {
                        MDC.put("sagaId", event.getSagaId().toString());
                        stockReserveResultUseCase.onStockReserveFailed(event);
                    });
        } catch (Exception e) {
            log.error("stock-reserve-failed 처리 중 오류. cause: {}", e.getMessage(), e);
        } finally {
            MDC.clear();
            acknowledgment.acknowledge();
        }
    }

    @KafkaListener(
            topics = "${shop.kafka.topics.payment-success.topic}",
            groupId = "${shop.kafka.topics.payment-success.group-id}",
            concurrency = "${shop.kafka.topics.payment-success.concurrency}"
    )
    public void onPaymentSuccess(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        try {
            jsonUtil.fromJson(record.value(), PaymentSuccessEvent.class)
                    .ifPresent(event -> {
                        MDC.put("sagaId", event.getSagaId().toString());
                        paymentResultUseCase.onPaymentSuccess(event);
                    });
        } catch (Exception e) {
            log.error("payment-success 처리 중 오류. cause: {}", e.getMessage(), e);
        } finally {
            MDC.clear();
            acknowledgment.acknowledge();
        }
    }

    @KafkaListener(
            topics = "${shop.kafka.topics.payment-failed.topic}",
            groupId = "${shop.kafka.topics.payment-failed.group-id}",
            concurrency = "${shop.kafka.topics.payment-failed.concurrency}"
    )
    public void onPaymentFailed(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        try {
            jsonUtil.fromJson(record.value(), PaymentFailedEvent.class)
                    .ifPresent(event -> {
                        MDC.put("sagaId", event.getSagaId().toString());
                        paymentResultUseCase.onPaymentFailed(event);
                    });
        } catch (Exception e) {
            log.error("payment-failed 처리 중 오류. cause: {}", e.getMessage(), e);
        } finally {
            MDC.clear();
            acknowledgment.acknowledge();
        }
    }
}
