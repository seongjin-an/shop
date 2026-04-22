package com.ansj.shopstock.stock.event.inbound;

import com.ansj.shopstock.common.*;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 단일 상품 재고 예약 요청 (shop-order → shop-stock, key=productId).
 */
@Getter
public class StockReservationRequestedEvent extends BaseEvent {

    private final UUID productId;
    private final int  quantity;
    private final UUID orderItemId;

    @JsonCreator
    public StockReservationRequestedEvent(
            @JsonProperty("eventId") EventId eventId,
            @JsonProperty("sagaId") SagaId sagaId,
            @JsonProperty("aggregateId") AggregateId aggregateId,
            @JsonProperty("aggregateType") String aggregateType,
            @JsonProperty("occurredAt") LocalDateTime occurredAt,
            @JsonProperty("productId") UUID productId,
            @JsonProperty("quantity") int quantity,
            @JsonProperty("orderItemId") UUID orderItemId) {
        super(MessageType.STOCK_RESERVATION_REQUESTED, eventId, sagaId, aggregateId, aggregateType, occurredAt);
        this.productId = productId;
        this.quantity = quantity;
        this.orderItemId = orderItemId;
    }
}
