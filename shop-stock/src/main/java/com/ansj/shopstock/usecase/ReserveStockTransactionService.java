package com.ansj.shopstock.usecase;

import com.ansj.shopstock.box.service.InboxEventService;
import com.ansj.shopstock.box.service.OutboxEventService;
import com.ansj.shopstock.common.AggregateId;
import com.ansj.shopstock.common.EventId;
import com.ansj.shopstock.stock.event.inbound.StockReservationRequestedEvent;
import com.ansj.shopstock.stock.event.outbound.StockReserveFailedEvent;
import com.ansj.shopstock.stock.event.outbound.StockReservedEvent;
import com.ansj.shopstock.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * ReserveStockUseCase 의 성공/실패 경로를 각각 독립 트랜잭션으로 처리.
 *
 * <p>성공 경로: 재고 예약 + inbox + outbox 를 하나의 트랜잭션으로 묶어
 * Debezium CDC 가 outbox INSERT 를 감지해 Kafka 에 발행하도록 보장.
 *
 * <p>실패 경로: inbox + outbox(failed) 만 별도 트랜잭션으로 커밋.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ReserveStockTransactionService {

    @Value("${shop.kafka.topics.stock-reserved.topic}")
    private String stockReservedTopic;

    @Value("${shop.kafka.topics.stock-reserve-failed.topic}")
    private String stockReserveFailedTopic;

    private final StockService stockService;
    private final InboxEventService inboxEventService;
    private final OutboxEventService outboxEventService;

    @Transactional
    public void reserveAndSaveOutbox(StockReservationRequestedEvent inEvent) {
        stockService.reserveOne(inEvent.getProductId(), inEvent.getQuantity());
        inboxEventService.createInboxEvent(inEvent);
        outboxEventService.save(buildReservedEvent(inEvent), stockReservedTopic,
                inEvent.getProductId().toString());
    }

    @Transactional
    public void failAndSaveOutbox(StockReservationRequestedEvent inEvent, String reason) {
        inboxEventService.createInboxEvent(inEvent);
        outboxEventService.save(buildFailedEvent(inEvent, reason), stockReserveFailedTopic,
                inEvent.getProductId().toString());
    }

    private StockReservedEvent buildReservedEvent(StockReservationRequestedEvent src) {
        StockReservedEvent event = StockReservedEvent.builder()
                .eventId(EventId.newId())
                .sagaId(src.getSagaId())
                .aggregateId(AggregateId.from(src.getProductId()))
                .aggregateType("STOCK")
                .occurredAt(LocalDateTime.now())
                .productId(src.getProductId())
                .orderItemId(src.getOrderItemId())
                .quantity(src.getQuantity())
                .build();
        event.setTraceId(src.getTraceId());
        return event;
    }

    private StockReserveFailedEvent buildFailedEvent(StockReservationRequestedEvent src, String reason) {
        StockReserveFailedEvent event = StockReserveFailedEvent.builder()
                .eventId(EventId.newId())
                .sagaId(src.getSagaId())
                .aggregateId(AggregateId.from(src.getProductId()))
                .aggregateType("STOCK")
                .occurredAt(LocalDateTime.now())
                .productId(src.getProductId())
                .orderItemId(src.getOrderItemId())
                .quantity(src.getQuantity())
                .reason(reason)
                .build();
        event.setTraceId(src.getTraceId());
        return event;
    }
}
