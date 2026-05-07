package com.ansj.shopstock.usecase;

import com.ansj.shopstock.box.service.InboxEventService;
import com.ansj.shopstock.common.AggregateId;
import com.ansj.shopstock.common.EventId;
import com.ansj.shopstock.common.JsonUtil;
import com.ansj.shopstock.common.SagaAwareKafkaPublisher;
import com.ansj.shopstock.stock.event.inbound.StockReservationRequestedEvent;
import com.ansj.shopstock.stock.event.outbound.StockReserveFailedEvent;
import com.ansj.shopstock.stock.event.outbound.StockReservedEvent;
import com.ansj.shopstock.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

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
    private final SagaAwareKafkaPublisher kafkaPublisher;
    private final JsonUtil jsonUtil;

    public void processReservationRequested(StockReservationRequestedEvent event) {
        if (inboxEventService.existsByEventId(event.getEventId())) {
            log.info("중복 이벤트 무시. eventId={}", event.getEventId());
            return;
        }

        try {
            stockService.reserveOne(event.getProductId(), event.getQuantity());
            inboxEventService.createInboxEvent(event);
            publishStockReserved(event);
        } catch (Exception e) {
            log.warn("재고 예약 실패. sagaId={}, productId={}, cause={}",
                    event.getSagaId(), event.getProductId(), e.getMessage());
            inboxEventService.createInboxEvent(event);
            publishStockReserveFailed(event, e.getMessage());
        }
    }

    private void publishStockReserved(StockReservationRequestedEvent event) {
        StockReservedEvent reservedEvent = StockReservedEvent.builder()
                .eventId(EventId.newId())
                .sagaId(event.getSagaId())
                .aggregateId(AggregateId.from(event.getProductId()))
                .aggregateType("STOCK")
                .occurredAt(LocalDateTime.now())
                .productId(event.getProductId())
                .orderItemId(event.getOrderItemId())
                .quantity(event.getQuantity())
                .build();
        reservedEvent.setTraceId(event.getTraceId());

        jsonUtil.toJson(reservedEvent).ifPresentOrElse(
                json -> kafkaPublisher.send(
                        stockReservedTopic,
                        event.getProductId().toString(),
                        event.getSagaId().toString(),
                        event.getTraceId(),
                        json
                ),
                () -> log.error("stock-reserved 직렬화 실패. sagaId={}", event.getSagaId())
        );
    }

    private void publishStockReserveFailed(StockReservationRequestedEvent event, String reason) {
        StockReserveFailedEvent failedEvent = StockReserveFailedEvent.builder()
                .eventId(EventId.newId())
                .sagaId(event.getSagaId())
                .aggregateId(AggregateId.from(event.getProductId()))
                .aggregateType("STOCK")
                .occurredAt(LocalDateTime.now())
                .productId(event.getProductId())
                .orderItemId(event.getOrderItemId())
                .quantity(event.getQuantity())
                .reason(reason)
                .build();
        failedEvent.setTraceId(event.getTraceId());

        jsonUtil.toJson(failedEvent).ifPresentOrElse(
                json -> kafkaPublisher.send(
                        stockReserveFailedTopic,
                        event.getProductId().toString(),
                        event.getSagaId().toString(),
                        event.getTraceId(),
                        json
                ),
                () -> log.error("stock-reserve-failed 직렬화 실패. sagaId={}", event.getSagaId())
        );
    }
}
