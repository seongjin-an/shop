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

/**
 * per-item 재고 예약 유스케이스.
 *
 * <p>Kafka topic = {@code stock-reservation-requested}, key = productId.
 * 동일 productId 이벤트는 동일 파티션 → 동일 consumer thread 로 직렬 처리되므로
 * StockEntity 에 대한 동시 write 가 사라져 낙관적 락 충돌이 원천 차단된다.
 */
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

        jsonUtil.toJson(reservedEvent).ifPresentOrElse(
                json -> kafkaPublisher.send(
                        stockReservedTopic,
                        event.getProductId().toString(),  // partition key = productId
                        event.getSagaId().toString(),
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

        jsonUtil.toJson(failedEvent).ifPresentOrElse(
                json -> kafkaPublisher.send(
                        stockReserveFailedTopic,
                        event.getProductId().toString(),
                        event.getSagaId().toString(),
                        json
                ),
                () -> log.error("stock-reserve-failed 직렬화 실패. sagaId={}", event.getSagaId())
        );
    }
}
