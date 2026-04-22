package com.ansj.shoporder.order.event.outbound;

import com.ansj.shoporder.common.*;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 단일 상품에 대한 재고 예약 확정 요청 (payment-success 이후).
 * shop-order → shop-stock. Kafka partition key = productId (UUID).
 */
@Getter
public class StockConfirmRequestedEvent extends BaseEvent {

    private final UUID productId;
    private final int  quantity;
    private final UUID orderItemId;

    @Builder
    public StockConfirmRequestedEvent(EventId eventId, SagaId sagaId, AggregateId aggregateId,
                                      String aggregateType, LocalDateTime occurredAt,
                                      UUID productId, int quantity, UUID orderItemId) {
        super(MessageType.STOCK_CONFIRM_REQUESTED, eventId, sagaId, aggregateId, aggregateType, occurredAt);
        this.productId = productId;
        this.quantity = quantity;
        this.orderItemId = orderItemId;
    }
}
