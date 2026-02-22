package com.ansj.shopproduct.inventory.dto.inbound;

import com.ansj.shopproduct.common.*;
import com.ansj.shopproduct.inventory.dto.InventoryItem;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class OrderCreatedEvent extends BaseEvent {

    private final List<InventoryItem> items;

    @JsonCreator
    public OrderCreatedEvent(
            @JsonProperty("eventId") EventId eventId,
            @JsonProperty("sagaId") SagaId sagaId,
            @JsonProperty("aggregateId") AggregateId aggregateId,
            @JsonProperty("aggregateType") String aggregateType,
            @JsonProperty("occurredAt") LocalDateTime occurredAt,
            @JsonProperty("items") List<InventoryItem> items) {
        super(MessageType.ORDER_CREATED, eventId, sagaId, aggregateId, aggregateType, occurredAt);
        this.items = items;
    }
}
