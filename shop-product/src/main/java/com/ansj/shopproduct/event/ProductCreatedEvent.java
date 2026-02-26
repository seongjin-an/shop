package com.ansj.shopproduct.event;

import com.ansj.shopproduct.common.*;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class ProductCreatedEvent extends BaseEvent {

    private StockItem item;

    public ProductCreatedEvent(EventId eventId, SagaId sagaId, AggregateId aggregateId, String aggregateType, LocalDateTime occurredAt, StockItem item) {
        super(MessageType.PRODUCT_CREATED, eventId, sagaId, aggregateId, aggregateType, occurredAt);
        this.item = item;
    }
}
