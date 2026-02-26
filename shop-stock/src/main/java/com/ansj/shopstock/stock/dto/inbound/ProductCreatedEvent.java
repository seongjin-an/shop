package com.ansj.shopstock.stock.dto.inbound;

import com.ansj.shopstock.common.*;
import com.ansj.shopstock.stock.dto.StockItem;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ProductCreatedEvent extends BaseEvent {

    private final StockItem item;

    @JsonCreator
    public ProductCreatedEvent(
            @JsonProperty("eventId") EventId eventId,
            @JsonProperty("sagaId") SagaId sagaId,
            @JsonProperty("aggregateId") AggregateId aggregateId,
            @JsonProperty("aggregateType") String aggregateType,
            @JsonProperty("occurredAt") LocalDateTime occurredAt,
            @JsonProperty("item") StockItem item) {
        super(MessageType.PRODUCT_CREATED, eventId, sagaId, aggregateId, aggregateType, occurredAt);
        this.item = item;
    }
}
