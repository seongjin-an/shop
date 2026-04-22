package com.ansj.shopstock.stock.event.inbound;

import com.ansj.shopstock.common.*;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 단일 상품 재고 예약 취소/보상 요청 (shop-order → shop-stock, key=productId).
 */
@Getter
public class StockReleaseRequestedEvent extends BaseEvent {

    private final UUID productId;
    private final int  quantity;
    private final UUID orderItemId;
    private final String reason;

    @JsonCreator
    public StockReleaseRequestedEvent(
            @JsonProperty("eventId") EventId eventId,
            @JsonProperty("sagaId") SagaId sagaId,
            @JsonProperty("aggregateId") AggregateId aggregateId,
            @JsonProperty("aggregateType") String aggregateType,
            @JsonProperty("occurredAt") LocalDateTime occurredAt,
            @JsonProperty("productId") UUID productId,
            @JsonProperty("quantity") int quantity,
            @JsonProperty("orderItemId") UUID orderItemId,
            @JsonProperty("reason") String reason) {
        super(MessageType.STOCK_RELEASE_REQUESTED, eventId, sagaId, aggregateId, aggregateType, occurredAt);
        this.productId = productId;
        this.quantity = quantity;
        this.orderItemId = orderItemId;
        this.reason = reason;
    }
}
