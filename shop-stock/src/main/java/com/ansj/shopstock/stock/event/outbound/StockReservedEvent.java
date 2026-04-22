package com.ansj.shopstock.stock.event.outbound;

import com.ansj.shopstock.common.*;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 단일 아이템 예약 성공 결과 (shop-stock → shop-order).
 *
 * <p>shop-order 의 aggregator 가 같은 sagaId 에 속한 모든 아이템이 RESERVED 에 도달할 때까지
 * 집계하여 payment-requested 를 발행하므로, {@code orderItemId} 필드가 필수.
 */
@Getter
public class StockReservedEvent extends BaseEvent {

    private final UUID productId;
    private final UUID orderItemId;
    private final int  quantity;

    @Builder
    public StockReservedEvent(EventId eventId,
                              SagaId sagaId,
                              AggregateId aggregateId,
                              String aggregateType,
                              LocalDateTime occurredAt,
                              UUID productId,
                              UUID orderItemId,
                              int quantity) {
        super(MessageType.STOCK_RESERVED, eventId, sagaId, aggregateId, aggregateType, occurredAt);
        this.productId = productId;
        this.orderItemId = orderItemId;
        this.quantity = quantity;
    }
}
