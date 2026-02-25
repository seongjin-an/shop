package com.ansj.shopstock.event.entity;

import com.ansj.shopstock.common.UuidUtils;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Table(name = "stock_inbox_event")
@Entity
public class InboxEventEntity {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)")
    private UUID id;

    // ORDER_CREATED, ORDER_CANCELED, PAYMENT_SUCCESS, PAYMENT_FAIL
    @Column(name = "event_type", nullable = false)
    private String eventType; // 이벤트 타입이 뭐가 있을지 ... 모르니 일단 String

    // 수신한 이벤트의 고유 ID (멱등성 체크용)
    @Column(name = "event_id", nullable = false, unique = true, columnDefinition = "BINARY(16)")
    private UUID eventId;

    // SAGA 흐름 추적
    @Column(name = "saga_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID sagaId;

    @Column(name = "aggregate_id", nullable = false, unique = true, columnDefinition = "BINARY(16)")
    private UUID aggregateId;

    // ORDER, PAYMENT
    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Lob
    @Column(name = "payload", nullable = false)
    private String payload;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    @Builder
    public InboxEventEntity(String eventType, UUID eventId, UUID sagaId, UUID aggregateId, String aggregateType, String payload) {
        this.eventType = eventType;
        this.eventId = eventId;
        this.sagaId = sagaId;
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.payload = payload;
        this.processedAt = LocalDateTime.now();
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidUtils.createV7();
        }
    }
}
