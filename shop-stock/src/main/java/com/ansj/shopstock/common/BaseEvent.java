package com.ansj.shopstock.common;

import com.ansj.shopstock.stock.event.inbound.OrderCancelledEvent;
import com.ansj.shopstock.stock.event.inbound.OrderCreatedEvent;
import com.ansj.shopstock.stock.event.inbound.PaymentSuccessEvent;
import com.ansj.shopstock.stock.event.inbound.ProductCreatedEvent;
import com.ansj.shopstock.stock.event.inbound.StockConfirmRequestedEvent;
import com.ansj.shopstock.stock.event.inbound.StockReleaseRequestedEvent;
import com.ansj.shopstock.stock.event.inbound.StockReservationRequestedEvent;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "eventType", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ProductCreatedEvent.class, name = MessageType.PRODUCT_CREATED),
        // legacy order-level events (kept for backward compatibility during migration)
        @JsonSubTypes.Type(value = OrderCreatedEvent.class, name = MessageType.ORDER_CREATED),
        @JsonSubTypes.Type(value = PaymentSuccessEvent.class, name = MessageType.PAYMENT_SUCCESS),
        @JsonSubTypes.Type(value = OrderCancelledEvent.class, name = MessageType.ORDER_CANCELLED),
        // per-item stock lifecycle (inbound to shop-stock, key=productId)
        @JsonSubTypes.Type(value = StockReservationRequestedEvent.class, name = MessageType.STOCK_RESERVATION_REQUESTED),
        @JsonSubTypes.Type(value = StockConfirmRequestedEvent.class,     name = MessageType.STOCK_CONFIRM_REQUESTED),
        @JsonSubTypes.Type(value = StockReleaseRequestedEvent.class,     name = MessageType.STOCK_RELEASE_REQUESTED),
})
public abstract class BaseEvent {
    private final String eventType;
    private final EventId eventId;
    private final SagaId sagaId;
    private final AggregateId aggregateId;
    private final String aggregateType;
    private final LocalDateTime occurredAt;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String traceId;

    protected BaseEvent(String eventType, EventId eventId, SagaId sagaId, AggregateId aggregateId, String aggregateType, LocalDateTime occurredAt) {
        this.eventId = eventId;
        this.sagaId = sagaId;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.occurredAt = occurredAt;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}
