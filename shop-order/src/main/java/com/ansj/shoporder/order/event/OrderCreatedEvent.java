package com.ansj.shoporder.order.event;

import com.ansj.shoporder.common.*;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class OrderCreatedEvent extends BaseEvent {

    private final List<OrderEventItem> items;

    public OrderCreatedEvent(EventId eventId, SagaId sagaId, AggregateId aggregateId,
                             String aggregateType, LocalDateTime occurredAt,
                             List<OrderEventItem> items) {
        super(MessageType.ORDER_CREATED, eventId, sagaId, aggregateId, aggregateType, occurredAt);
        this.items = items;
    }
}
