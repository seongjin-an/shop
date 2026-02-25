package com.ansj.shopstock.common;

import com.ansj.shopstock.stock.dto.inbound.ProductCreatedEvent;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "eventType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ProductCreatedEvent.class, name = MessageType.PRODUCT_CREATED),
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
