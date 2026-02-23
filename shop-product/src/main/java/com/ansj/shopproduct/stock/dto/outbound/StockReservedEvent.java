package com.ansj.shopproduct.stock.dto.outbound;

import com.ansj.shopproduct.common.*;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class StockReservedEvent extends BaseEvent {

    @Builder
    public StockReservedEvent(EventId eventId,
                              SagaId sagaId,
                              AggregateId aggregateId,
                              String aggregateType,
                              LocalDateTime occurredAt) {
        super(MessageType.STOCK_RESERVED, eventId, sagaId, aggregateId, aggregateType, occurredAt);
    }
}
