package com.ansj.shopstock.common;

import com.ansj.shopstock.stock.event.inbound.OrderCancelledEvent;
import com.ansj.shopstock.stock.event.inbound.OrderCreatedEvent;
import com.ansj.shopstock.stock.event.inbound.PaymentSuccessEvent;
import com.ansj.shopstock.stock.event.inbound.ProductCreatedEvent;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "eventType", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ProductCreatedEvent.class, name = MessageType.PRODUCT_CREATED),
        @JsonSubTypes.Type(value = OrderCreatedEvent.class, name = MessageType.ORDER_CREATED),
        @JsonSubTypes.Type(value = PaymentSuccessEvent.class, name = MessageType.PAYMENT_SUCCESS),
        @JsonSubTypes.Type(value = OrderCancelledEvent.class, name = MessageType.ORDER_CANCELLED),
})
public abstract class BaseEvent {
    private final String eventType;
    private final EventId eventId;
    private final SagaId sagaId;
    private final AggregateId aggregateId;
    private final String aggregateType;
    private final LocalDateTime occurredAt;

    protected BaseEvent(String eventType, EventId eventId, SagaId sagaId, AggregateId aggregateId, String aggregateType, LocalDateTime occurredAt) {
        this.eventId = eventId;
        this.sagaId = sagaId;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.occurredAt = occurredAt;
    }
}
