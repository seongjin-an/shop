package com.ansj.shoppayment.payment.event.outbound;

import com.ansj.shoppayment.common.*;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class PaymentFailedEvent extends BaseEvent {

    private final String reason;

    @Builder
    public PaymentFailedEvent(EventId eventId, SagaId sagaId, AggregateId aggregateId,
                              String aggregateType, LocalDateTime occurredAt, String reason) {
        super(MessageType.PAYMENT_FAILED, eventId, sagaId, aggregateId, aggregateType, occurredAt);
        this.reason = reason;
    }
}
