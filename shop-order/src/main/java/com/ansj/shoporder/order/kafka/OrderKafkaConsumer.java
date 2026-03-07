package com.ansj.shoporder.order.kafka;

import com.ansj.shoporder.common.JsonUtil;
import com.ansj.shoporder.order.event.inbound.StockReserveFailedEvent;
import com.ansj.shoporder.order.event.inbound.StockReservedEvent;
import com.ansj.shoporder.order.usecase.StockReserveResultUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class OrderKafkaConsumer {

    private final StockReserveResultUseCase stockReserveResultUseCase;
    private final JsonUtil jsonUtil;

    @KafkaListener(
            topics = "${shop.kafka.topics.stock-reserved.topic}",
            groupId = "${shop.kafka.topics.stock-reserved.group-id}",
            concurrency = "${shop.kafka.topics.stock-reserved.concurrency}"
    )
    public void onStockReserved(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        try {
            jsonUtil.fromJson(record.value(), StockReservedEvent.class)
                    .ifPresent(stockReserveResultUseCase::onStockReserved);
        } catch (Exception e) {
            log.error("stock-reserved 처리 중 오류. cause: {}", e.getMessage(), e);
        } finally {
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
                    .ifPresent(stockReserveResultUseCase::onStockReserveFailed);
        } catch (Exception e) {
            log.error("stock-reserve-failed 처리 중 오류. cause: {}", e.getMessage(), e);
        } finally {
            acknowledgment.acknowledge();
        }
    }
}
