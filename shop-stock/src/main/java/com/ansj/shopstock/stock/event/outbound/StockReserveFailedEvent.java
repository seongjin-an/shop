package com.ansj.shopstock.stock.event.outbound;

import com.ansj.shopstock.common.*;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class StockReserveFailedEvent extends BaseEvent {

    private final String reason;

    @Builder
    public StockReserveFailedEvent(EventId eventId,
                                   SagaId sagaId,
                                   AggregateId aggregateId,
                                   String aggregateType,
                                   LocalDateTime occurredAt,
                                   String reason) {
        super(MessageType.STOCK_RESERVE_FAILED, eventId, sagaId, aggregateId, aggregateType, occurredAt);
        this.reason = reason;
    }
}
