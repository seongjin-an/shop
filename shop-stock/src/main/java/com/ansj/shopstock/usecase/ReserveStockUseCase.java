package com.ansj.shopstock.usecase;

import com.ansj.shopstock.box.service.InboxEventService;
import com.ansj.shopstock.common.*;
import com.ansj.shopstock.stock.dto.StockItem;
import com.ansj.shopstock.stock.dto.inbound.OrderCreatedEvent;
import com.ansj.shopstock.stock.dto.outbound.StockReserveFailedEvent;
import com.ansj.shopstock.stock.dto.outbound.StockReservedEvent;
import com.ansj.shopstock.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class ReserveStockUseCase {

    @Value("${shop.kafka.topics.stock-reserved.topic}")
    private String stockReservedTopic;

    @Value("${shop.kafka.topics.stock-reserve-failed.topic}")
    private String stockReserveFailedTopic;

    private final StockService stockService;
    private final InboxEventService inboxEventService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonUtil jsonUtil;

    public void processOrderCreatedEvent(OrderCreatedEvent event) {
        if (inboxEventService.existsByEventId(event.getEventId())) {
            log.info("중복 이벤트 무시. eventId={}", event.getEventId());
            return;
        }

        try {
            List<StockItem> stockItems = event.getItems().stream()
                    .map(item -> new StockItem(AggregateId.from(item.getProductId()), item.getQuantity()))
                    .toList();

            stockService.reserve(stockItems);
            inboxEventService.createInboxEvent(event);

            publishStockReserved(event);

        } catch (Exception e) {
            log.warn("재고 예약 실패. sagaId={}, cause={}", event.getSagaId(), e.getMessage());
            inboxEventService.createInboxEvent(event);
            publishStockReserveFailed(event, e.getMessage());
        }
    }

    private void publishStockReserved(OrderCreatedEvent event) {
        StockReservedEvent reservedEvent = StockReservedEvent.builder()
                .eventId(EventId.newId())
                .sagaId(event.getSagaId())
                .aggregateId(event.getAggregateId())  // orderId
                .aggregateType("ORDER")
                .occurredAt(LocalDateTime.now())
                .build();

        jsonUtil.toJson(reservedEvent)
                .ifPresentOrElse(
                        json -> kafkaTemplate.send(stockReservedTopic, event.getSagaId().toString(), json),
                        () -> log.error("stock-reserved 이벤트 직렬화 실패. sagaId={}", event.getSagaId())
                );
    }

    private void publishStockReserveFailed(OrderCreatedEvent event, String reason) {
        StockReserveFailedEvent failedEvent = StockReserveFailedEvent.builder()
                .eventId(EventId.newId())
                .sagaId(event.getSagaId())
                .aggregateId(event.getAggregateId())  // orderId
                .aggregateType("ORDER")
                .occurredAt(LocalDateTime.now())
                .reason(reason)
                .build();

        jsonUtil.toJson(failedEvent)
                .ifPresentOrElse(
                        json -> kafkaTemplate.send(stockReserveFailedTopic, event.getSagaId().toString(), json),
                        () -> log.error("stock-reserve-failed 이벤트 직렬화 실패. sagaId={}", event.getSagaId())
                );
    }
}
