package com.ansj.shoporder.order.event.outbound;

import com.ansj.shoporder.common.*;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 단일 상품에 대한 재고 예약 요청.
 * shop-order → shop-stock. Kafka partition key = productId (UUID).
 *
 * <p>주문 1건이 N 개 아이템을 가지면 N 개 이벤트가 발행된다.
 * 동일 productId 에 대한 이벤트는 모두 같은 파티션으로 라우팅되어
 * 동일 consumer thread 가 직렬 처리하므로 낙관적 락 충돌이 사라진다.
 */
@Getter
public class StockReservationRequestedEvent extends BaseEvent {

    private final UUID productId;
    private final int  quantity;
    private final UUID orderItemId;  // shop-order 의 OrderItemEntity 식별용 (aggregator 에서 사용)

    @Builder
    public StockReservationRequestedEvent(EventId eventId, SagaId sagaId, AggregateId aggregateId,
                                          String aggregateType, LocalDateTime occurredAt,
                                          UUID productId, int quantity, UUID orderItemId) {
        super(MessageType.STOCK_RESERVATION_REQUESTED, eventId, sagaId, aggregateId, aggregateType, occurredAt);
        this.productId = productId;
        this.quantity = quantity;
        this.orderItemId = orderItemId;
    }
}
