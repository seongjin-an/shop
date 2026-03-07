package com.ansj.shopstock.stock.dto.inbound;

import com.ansj.shopstock.common.*;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class OrderCreatedEvent extends BaseEvent {

    private final List<OrderItem> items;

    @JsonCreator
    public OrderCreatedEvent(
            @JsonProperty("eventId") EventId eventId,
            @JsonProperty("sagaId") SagaId sagaId,
            @JsonProperty("aggregateId") AggregateId aggregateId,
            @JsonProperty("aggregateType") String aggregateType,
            @JsonProperty("occurredAt") LocalDateTime occurredAt,
            @JsonProperty("items") List<OrderItem> items) {
        super(MessageType.ORDER_CREATED, eventId, sagaId, aggregateId, aggregateType, occurredAt);
        this.items = items;
    }
}
