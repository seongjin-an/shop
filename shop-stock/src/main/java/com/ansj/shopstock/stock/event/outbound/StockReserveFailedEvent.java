package com.ansj.shopstock.stock.event.outbound;

import com.ansj.shopstock.common.*;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 단일 아이템 예약 실패 결과 (shop-stock → shop-order).
 */
@Getter
public class StockReserveFailedEvent extends BaseEvent {

    private final UUID productId;
    private final UUID orderItemId;
    private final int  quantity;
    private final String reason;

    @Builder
    public StockReserveFailedEvent(EventId eventId,
                                   SagaId sagaId,
                                   AggregateId aggregateId,
                                   String aggregateType,
                                   LocalDateTime occurredAt,
                                   UUID productId,
                                   UUID orderItemId,
                                   int quantity,
                                   String reason) {
        super(MessageType.STOCK_RESERVE_FAILED, eventId, sagaId, aggregateId, aggregateType, occurredAt);
        this.productId = productId;
        this.orderItemId = orderItemId;
        this.quantity = quantity;
        this.reason = reason;
    }
}
