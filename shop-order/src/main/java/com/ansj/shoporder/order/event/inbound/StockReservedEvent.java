package com.ansj.shoporder.order.event.inbound;

import com.ansj.shoporder.common.*;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 단일 아이템 예약 성공 결과 (shop-stock → shop-order).
 * 예전 1-주문-1-이벤트 → 1-아이템-1-이벤트 구조로 변경됨.
 *
 * <p>shop-order 의 {@code StockReserveResultUseCase} aggregator 가
 * 같은 sagaId 의 모든 아이템이 성공할 때까지 집계 후 payment-requested 를 발행한다.
 */
@Getter
public class StockReservedEvent extends BaseEvent {

    private final UUID productId;
    private final UUID orderItemId;
    private final int  quantity;

    @JsonCreator
    public StockReservedEvent(
            @JsonProperty("eventId") EventId eventId,
            @JsonProperty("sagaId") SagaId sagaId,
            @JsonProperty("aggregateId") AggregateId aggregateId,
            @JsonProperty("aggregateType") String aggregateType,
            @JsonProperty("occurredAt") LocalDateTime occurredAt,
            @JsonProperty("productId") UUID productId,
            @JsonProperty("orderItemId") UUID orderItemId,
            @JsonProperty("quantity") int quantity) {
        super(MessageType.STOCK_RESERVED, eventId, sagaId, aggregateId, aggregateType, occurredAt);
        this.productId = productId;
        this.orderItemId = orderItemId;
        this.quantity = quantity;
    }
}
