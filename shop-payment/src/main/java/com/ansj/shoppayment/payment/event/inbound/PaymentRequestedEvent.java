package com.ansj.shoppayment.payment.event.inbound;

import com.ansj.shoppayment.common.*;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public class PaymentRequestedEvent extends BaseEvent {

    private final Long userId;
    private final BigDecimal totalAmount;

    @JsonCreator
    public PaymentRequestedEvent(
            @JsonProperty("eventType") String eventType,
            @JsonProperty("eventId") EventId eventId,
            @JsonProperty("sagaId") SagaId sagaId,
            @JsonProperty("aggregateId") AggregateId aggregateId,
            @JsonProperty("aggregateType") String aggregateType,
            @JsonProperty("occurredAt") LocalDateTime occurredAt,
            @JsonProperty("userId") Long userId,
            @JsonProperty("totalAmount") BigDecimal totalAmount) {
        super(eventType, eventId, sagaId, aggregateId, aggregateType, occurredAt);
        this.userId = userId;
        this.totalAmount = totalAmount;
    }
}
