package com.ansj.shoporder.order.event.outbound;

import com.ansj.shoporder.common.*;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 단일 상품에 대한 재고 예약 취소/보상 요청.
 * <ul>
 *   <li>payment-failed → 이미 예약된 모든 아이템에 대해 발행</li>
 *   <li>일부 아이템 reserve 실패 → 먼저 reserved 된 아이템에 대해 발행 (부분 롤백)</li>
 * </ul>
 * shop-order → shop-stock. Kafka partition key = productId (UUID).
 */
@Getter
public class StockReleaseRequestedEvent extends BaseEvent {

    private final UUID productId;
    private final int  quantity;
    private final UUID orderItemId;
    private final String reason;

    @Builder
    public StockReleaseRequestedEvent(EventId eventId, SagaId sagaId, AggregateId aggregateId,
                                      String aggregateType, LocalDateTime occurredAt,
                                      UUID productId, int quantity, UUID orderItemId, String reason) {
        super(MessageType.STOCK_RELEASE_REQUESTED, eventId, sagaId, aggregateId, aggregateType, occurredAt);
        this.productId = productId;
        this.quantity = quantity;
        this.orderItemId = orderItemId;
        this.reason = reason;
    }
}
