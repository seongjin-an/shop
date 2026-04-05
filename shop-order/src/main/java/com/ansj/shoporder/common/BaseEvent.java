package com.ansj.shoporder.common;

import com.ansj.shoporder.order.event.inbound.PaymentFailedEvent;
import com.ansj.shoporder.order.event.inbound.PaymentSuccessEvent;
import com.ansj.shoporder.order.event.inbound.StockReserveFailedEvent;
import com.ansj.shoporder.order.event.inbound.StockReservedEvent;
import com.ansj.shoporder.order.event.outbound.OrderCreatedEvent;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "eventType", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = OrderCreatedEvent.class, name = MessageType.ORDER_CREATED),
        @JsonSubTypes.Type(value = StockReservedEvent.class, name = MessageType.STOCK_RESERVED),
        @JsonSubTypes.Type(value = StockReserveFailedEvent.class, name = MessageType.STOCK_RESERVE_FAILED),
        @JsonSubTypes.Type(value = PaymentSuccessEvent.class, name = MessageType.PAYMENT_SUCCESS),
        @JsonSubTypes.Type(value = PaymentFailedEvent.class, name = MessageType.PAYMENT_FAILED),
})
public abstract class BaseEvent {
    private final String eventType;
    private final EventId eventId;
    private final SagaId sagaId;
    private final AggregateId aggregateId;
    private final String aggregateType;
    private final LocalDateTime occurredAt;

    protected BaseEvent(String eventType, EventId eventId, SagaId sagaId,
                        AggregateId aggregateId, String aggregateType, LocalDateTime occurredAt) {
        this.eventType = eventType;
        this.eventId = eventId;
        this.sagaId = sagaId;
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.occurredAt = occurredAt;
    }
}
