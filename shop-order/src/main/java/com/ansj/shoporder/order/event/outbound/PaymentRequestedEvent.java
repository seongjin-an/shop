package com.ansj.shoporder.order.event.outbound;

import com.ansj.shoporder.common.*;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public class PaymentRequestedEvent extends BaseEvent {

    /** 결제 대상 사용자 */
    private final UUID userId;

    /** 결제 금액 */
    private final BigDecimal totalAmount;

    @Builder
    public PaymentRequestedEvent(EventId eventId, SagaId sagaId, AggregateId aggregateId,
                                  String aggregateType, LocalDateTime occurredAt,
                                  UUID userId, BigDecimal totalAmount) {
        super(MessageType.PAYMENT_REQUESTED, eventId, sagaId, aggregateId, aggregateType, occurredAt);
        this.userId = userId;
        this.totalAmount = totalAmount;
    }
}
