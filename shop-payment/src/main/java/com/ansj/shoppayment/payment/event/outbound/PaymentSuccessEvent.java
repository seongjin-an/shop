package com.ansj.shoppayment.payment.event.outbound;

import com.ansj.shoppayment.common.*;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class PaymentSuccessEvent extends BaseEvent {

    @Builder
    public PaymentSuccessEvent(EventId eventId, SagaId sagaId, AggregateId aggregateId,
                               String aggregateType, LocalDateTime occurredAt) {
        super(MessageType.PAYMENT_SUCCESS, eventId, sagaId, aggregateId, aggregateType, occurredAt);
    }
}
