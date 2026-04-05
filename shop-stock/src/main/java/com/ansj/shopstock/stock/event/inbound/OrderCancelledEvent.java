package com.ansj.shopstock.stock.event.inbound;

import com.ansj.shopstock.common.*;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class OrderCancelledEvent extends BaseEvent {

    @JsonCreator
    public OrderCancelledEvent(
            @JsonProperty("eventId") EventId eventId,
            @JsonProperty("sagaId") SagaId sagaId,
            @JsonProperty("aggregateId") AggregateId aggregateId,
            @JsonProperty("aggregateType") String aggregateType,
            @JsonProperty("occurredAt") LocalDateTime occurredAt) {
        super(MessageType.ORDER_CANCELLED, eventId, sagaId, aggregateId, aggregateType, occurredAt);
    }
}
