package com.ansj.shoporder.order.event.inbound;

import com.ansj.shoporder.common.*;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class PaymentFailedEvent extends BaseEvent {

    private final String reason;

    @JsonCreator
    public PaymentFailedEvent(
            @JsonProperty("eventId") EventId eventId,
            @JsonProperty("sagaId") SagaId sagaId,
            @JsonProperty("aggregateId") AggregateId aggregateId,
            @JsonProperty("aggregateType") String aggregateType,
            @JsonProperty("occurredAt") LocalDateTime occurredAt,
            @JsonProperty("reason") String reason) {
        super(MessageType.PAYMENT_FAILED, eventId, sagaId, aggregateId, aggregateType, occurredAt);
        this.reason = reason;
    }
}
