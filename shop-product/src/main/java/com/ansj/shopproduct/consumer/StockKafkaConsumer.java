package com.ansj.shopproduct.consumer;

import com.ansj.shopproduct.common.JsonUtil;
import com.ansj.shopproduct.stock.dto.inbound.OrderCreatedEvent;
import com.ansj.shopproduct.usecase.ProductStockUseCase;
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

    private final JsonUtil jsonUtil;
    private final ProductStockUseCase productStockUseCase;

    @KafkaListener(
            topics = "${shop.kafka.topics.order-created.topic}",
            groupId = "${shop.kafka.topics.order-created.group-id}",
            concurrency = "${shop.kafka.topics.order-created.concurrency}"
    )
    public void orderCreated(ConsumerRecord<String, String> consumerRecord, Acknowledgment acknowledgment) {
        try {
            jsonUtil.fromJson(consumerRecord.value(), OrderCreatedEvent.class)
                    .ifPresent(productStockUseCase::processOrder);
        } catch (Exception e) {
            log.error("handling order-created event error. cause: {}", e.getMessage(), e);
        } finally {
            acknowledgment.acknowledge();
        }

    }
}
