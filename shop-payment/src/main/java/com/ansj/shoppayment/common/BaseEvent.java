package com.ansj.shoppayment.common;

import com.ansj.shoppayment.payment.event.inbound.PaymentRequestedEvent;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "eventType", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = PaymentRequestedEvent.class, name = MessageType.PAYMENT_REQUESTED),
})
public abstract class BaseEvent {
    private final String eventType;
    private final EventId eventId;
    private final SagaId sagaId;
    private final AggregateId aggregateId;
    private final String aggregateType;
    private final LocalDateTime occurredAt;

    protected BaseEvent(String eventType, EventId eventId, SagaId sagaId,
                        AggregateId aggregateId, String aggregateType, LocalDateTime occurredAt) {
        this.eventType = eventType;
        this.eventId = eventId;
        this.sagaId = sagaId;
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.occurredAt = occurredAt;
    }
}
