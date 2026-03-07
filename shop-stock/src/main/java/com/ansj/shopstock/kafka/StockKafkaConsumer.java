package com.ansj.shopstock.kafka;

import com.ansj.shopstock.common.JsonUtil;
import com.ansj.shopstock.stock.dto.inbound.OrderCreatedEvent;
import com.ansj.shopstock.stock.dto.inbound.ProductCreatedEvent;
import com.ansj.shopstock.usecase.ReserveStockUseCase;
import com.ansj.shopstock.usecase.StockUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class StockKafkaConsumer {

    private final StockUseCase stockUseCase;
    private final ReserveStockUseCase reserveStockUseCase;
    private final JsonUtil jsonUtil;

    @KafkaListener(
            topics = "${shop.kafka.topics.product-created.topic}",
            groupId = "${shop.kafka.topics.product-created.group-id}",
            concurrency = "${shop.kafka.topics.product-created.concurrency}"
    )
    public void onProductCreated(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        try {
            jsonUtil.fromJson(record.value(), ProductCreatedEvent.class)
                    .ifPresent(stockUseCase::processIncreaseStockEvent);
        } catch (Exception e) {
            log.error("product-created 처리 중 오류. cause: {}", e.getMessage(), e);
        } finally {
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
                    .ifPresent(reserveStockUseCase::processOrderCreatedEvent);
        } catch (Exception e) {
            log.error("order-created 처리 중 오류. cause: {}", e.getMessage(), e);
        } finally {
            acknowledgment.acknowledge();
        }
    }
}
