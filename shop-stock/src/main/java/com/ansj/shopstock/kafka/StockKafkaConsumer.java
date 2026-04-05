package com.ansj.shopstock.kafka;

import com.ansj.shopstock.common.JsonUtil;
import com.ansj.shopstock.stock.event.inbound.OrderCancelledEvent;
import com.ansj.shopstock.stock.event.inbound.OrderCreatedEvent;
import com.ansj.shopstock.stock.event.inbound.PaymentSuccessEvent;
import com.ansj.shopstock.stock.event.inbound.ProductCreatedEvent;
import com.ansj.shopstock.usecase.CompensateStockUseCase;
import com.ansj.shopstock.usecase.ReserveStockUseCase;
import com.ansj.shopstock.usecase.StockUseCase;
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
public class StockKafkaConsumer {

    private final StockUseCase stockUseCase;
    private final ReserveStockUseCase reserveStockUseCase;
    private final CompensateStockUseCase compensateStockUseCase;
    private final JsonUtil jsonUtil;

    @KafkaListener(
            topics = "${shop.kafka.topics.product-created.topic}",
            groupId = "${shop.kafka.topics.product-created.group-id}",
            concurrency = "${shop.kafka.topics.product-created.concurrency}"
    )
    public void onProductCreated(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        try {
            jsonUtil.fromJson(record.value(), ProductCreatedEvent.class)
                    .ifPresent(event -> {
                        MDC.put("sagaId", event.getSagaId().toString());
                        stockUseCase.processIncreaseStockEvent(event);
                    });
        } catch (Exception e) {
            log.error("product-created 처리 중 오류. cause: {}", e.getMessage(), e);
        } finally {
            MDC.clear();
            acknowledgment.acknowledge();
        }
    }

    @KafkaListener(
            topics = "${shop.kafka.topics.order-created.topic}",
            groupId = "${shop.kafka.topics.order-created.group-id}",
            concurrency = "${shop.kafka.topics.order-created.concurrency}"
    )
    public void onOrderCreated(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        try {
            jsonUtil.fromJson(record.value(), OrderCreatedEvent.class)
                    .ifPresent(event -> {
                        MDC.put("sagaId", event.getSagaId().toString());
                        reserveStockUseCase.processOrderCreatedEvent(event);
                    });
        } catch (Exception e) {
            log.error("order-created 처리 중 오류. cause: {}", e.getMessage(), e);
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
                        compensateStockUseCase.onPaymentSuccess(event);
                    });
        } catch (Exception e) {
            log.error("payment-success 처리 중 오류. cause: {}", e.getMessage(), e);
        } finally {
            MDC.clear();
            acknowledgment.acknowledge();
        }
    }

    @KafkaListener(
            topics = "${shop.kafka.topics.order-cancelled.topic}",
            groupId = "${shop.kafka.topics.order-cancelled.group-id}",
            concurrency = "${shop.kafka.topics.order-cancelled.concurrency}"
    )
    public void onOrderCancelled(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        try {
            jsonUtil.fromJson(record.value(), OrderCancelledEvent.class)
                    .ifPresent(event -> {
                        MDC.put("sagaId", event.getSagaId().toString());
                        compensateStockUseCase.onOrderCancelled(event);
                    });
        } catch (Exception e) {
            log.error("order-cancelled 처리 중 오류. cause: {}", e.getMessage(), e);
        } finally {
            MDC.clear();
            acknowledgment.acknowledge();
        }
    }
}
