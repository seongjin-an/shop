package com.ansj.shopstock.kafka;

import com.ansj.shopstock.common.JsonUtil;
import com.ansj.shopstock.stock.dto.inbound.ProductCreatedEvent;
import com.ansj.shopstock.usecase.StockUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Transactional
@Service
public class StockKafkaConsumer {
    private final StockUseCase stockUseCase;
    private final JsonUtil jsonUtil;

    @KafkaListener(
            topics = "${shop.kafka.topics.product-created.topic}",
            groupId = "${shop.kafka.topics.product-created.group-id}",
            concurrency = "${shop.kafka.topics.product-created.concurrency}"
    )
    public void orderCreated(ConsumerRecord<String, String> consumerRecord, Acknowledgment acknowledgment) {
        try {
            jsonUtil.fromJson(consumerRecord.value(), ProductCreatedEvent.class)
                    .ifPresent(stockUseCase::processIncreaseStockEvent);
        } catch (Exception e) {
            log.error("handling order-created event error. cause: {}", e.getMessage(), e);
        } finally {
            acknowledgment.acknowledge();
        }

    }
}
