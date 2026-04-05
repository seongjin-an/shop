package com.ansj.shoporder.order.event.outbound;

import com.ansj.shoporder.common.*;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class OrderCancelledEvent extends BaseEvent {

    @Builder
    public OrderCancelledEvent(EventId eventId, SagaId sagaId, AggregateId aggregateId,
                               String aggregateType, LocalDateTime occurredAt) {
        super(MessageType.ORDER_CANCELLED, eventId, sagaId, aggregateId, aggregateType, occurredAt);
    }
}
