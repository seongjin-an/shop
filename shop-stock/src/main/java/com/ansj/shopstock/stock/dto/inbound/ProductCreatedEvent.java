package com.ansj.shopstock.stock.dto.inbound;

import com.ansj.shopstock.common.*;
import com.ansj.shopstock.stock.dto.StockItem;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class ProductCreatedEvent extends BaseEvent {

    private final List<StockItem> items;

    @JsonCreator
    public ProductCreatedEvent(
            @JsonProperty("eventId") EventId eventId,
            @JsonProperty("sagaId") SagaId sagaId,
            @JsonProperty("aggregateId") AggregateId aggregateId,
            @JsonProperty("aggregateType") String aggregateType,
            @JsonProperty("occurredAt") LocalDateTime occurredAt,
            @JsonProperty("items") List<StockItem> items) {
        super(MessageType.PRODUCT_CREATED, eventId, sagaId, aggregateId, aggregateType, occurredAt);
        this.items = items;
    }
}
