package com.ansj.shopstock.stock.event.inbound;

import com.ansj.shopstock.common.*;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 단일 상품 재고 예약 확정 요청 (shop-order → shop-stock, key=productId).
 * payment-success 수신 후 shop-order 가 per-item 으로 fan-out.
 */
@Getter
public class StockConfirmRequestedEvent extends BaseEvent {

    private final UUID productId;
    private final int  quantity;
    private final UUID orderItemId;

    @JsonCreator
    public StockConfirmRequestedEvent(
            @JsonProperty("eventId") EventId eventId,
            @JsonProperty("sagaId") SagaId sagaId,
            @JsonProperty("aggregateId") AggregateId aggregateId,
            @JsonProperty("aggregateType") String aggregateType,
            @JsonProperty("occurredAt") LocalDateTime occurredAt,
            @JsonProperty("productId") UUID productId,
            @JsonProperty("quantity") int quantity,
            @JsonProperty("orderItemId") UUID orderItemId) {
        super(MessageType.STOCK_CONFIRM_REQUESTED, eventId, sagaId, aggregateId, aggregateType, occurredAt);
        this.productId = productId;
        this.quantity = quantity;
        this.orderItemId = orderItemId;
    }
}
